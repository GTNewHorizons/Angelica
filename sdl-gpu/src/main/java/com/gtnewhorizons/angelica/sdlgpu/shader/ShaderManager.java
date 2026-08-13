package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ShaderWorkSubmitter;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess.Edit;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import org.taumc.glsl.grammar.GLSLParser;
import com.gtnewhorizons.angelica.sdlgpu.shader.cross.CrossCompileUtil;
import com.gtnewhorizons.angelica.sdlgpu.shader.dxbc.DxbcCrossCompile;
import com.gtnewhorizons.angelica.sdlgpu.shader.msl.MslCrossCompile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDL_GPUComputePipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUShaderCreateInfo;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

/**
 * Manages shader compilation: GLSL -> SPIR-V (via shaderc) -> per-backend (via SPIRV-Cross).
 * Tracks shader/program objects with synthetic GL IDs.
 */
public final class ShaderManager {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private final Device device;
    private Consumer<ProgramObject> uniformReleaseListener;

    public void setUniformReleaseListener(Consumer<ProgramObject> listener) { this.uniformReleaseListener = listener; }

    private int nextShaderId = 1;
    private int nextProgramId = 1;
    private final Int2ObjectOpenHashMap<ShaderObject> shaderObjects = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<ProgramObject> programObjects = new Int2ObjectOpenHashMap<>();

    private record PrewarmKey(String source, int glShaderType) {}
    private record PrewarmEntry(byte[] remappedSpirv, StageReflection reflection, GraphicsBindingMap graphicsBindingMap, Set<String> boolUniforms, String transformedSource) {}
    private record PrewarmHit(ByteBuffer spirv, StageReflection reflection, GraphicsBindingMap graphicsBindingMap, Set<String> boolUniforms, String transformedSource) {}
    private static final int PREWARM_CACHE_MAX = 256;
    private static final Object2ObjectLinkedOpenHashMap<PrewarmKey, PrewarmEntry> PREWARM_CACHE = new Object2ObjectLinkedOpenHashMap<>();

    public static void clearPrewarmCache() {
        synchronized (PREWARM_CACHE) { PREWARM_CACHE.clear(); }
    }

    private static PrewarmHit lookupPrewarm(String src, int glShaderType) {
        final PrewarmEntry e;
        synchronized (PREWARM_CACHE) {
            e = PREWARM_CACHE.getAndMoveToFirst(new PrewarmKey(src, glShaderType));
        }
        if (e == null) return null;
        // per-hit copy: linkProgram patches the returned SPIR-V in place (applyVaryingMatch / applyAttribLocationsAndInputMask)
        final ByteBuffer copy = memAlloc(e.remappedSpirv().length);
        copy.put(e.remappedSpirv()).flip();
        return new PrewarmHit(copy, e.reflection(), e.graphicsBindingMap(), e.boolUniforms(), e.transformedSource());
    }

    public ShaderManager(Device device) {
        this.device = device;
    }

    public int createShader(int type) {
        final int id = nextShaderId++;
        shaderObjects.put(id, new ShaderObject(type));
        return id;
    }

    public void shaderSource(int shader, CharSequence source) {
        final ShaderObject obj = shaderObjects.get(shader);
        if (obj == null) return;

        String raw = source.toString();
        if (!raw.isEmpty() && raw.charAt(raw.length() - 1) == '\0') {
            raw = raw.substring(0, raw.length() - 1);
        }

        final PrewarmHit hit = lookupPrewarm(raw, obj.type);
        if (hit != null) {
            obj.source = hit.transformedSource();
            obj.reflection = hit.reflection();
            obj.graphicsBindingMap = hit.graphicsBindingMap();
            obj.boolUniforms = hit.boolUniforms();
            obj.spirvFuture = CompletableFuture.completedFuture(new SpirvCompiler.Result(hit.spirv(), null, null));
            return;
        }

        final GlslVulkanPreprocess.Result pre = GlslVulkanPreprocess.run(raw, obj.type, "shader" + shader, true);
        obj.boolUniforms = pre != null ? pre.boolUniforms() : Set.of();
        String src = pre != null ? pre.rewrittenSource() : raw;
        if (obj.isVertex()) {
            src = ClipZRemap.injectGLToVulkanClipZ(src);
        }
        src = SamplerStripper.stripUnused(src);
        if (obj.type == GL20.GL_VERTEX_SHADER || obj.type == GL20.GL_FRAGMENT_SHADER) {
            src = PerFrameBlockInjector.inject(src, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock);
        }

        obj.source = src;

        final ShaderWorkSubmitter submitter = GLSMHooks.shaderWorkSubmitter;
        if (submitter != null) {
            final int shaderKind = shaderKindFor(obj);
            final int glType = obj.type;
            final String finalSrc = src;
            obj.spirvFuture = submitter.submit(() -> {
                final SpirvCompiler.Result r = SpirvCompiler.compile(finalSrc, shaderKind, "shader" + shader, SpirvCompiler.Options.vulkanForced460Core());
                if (r.spirv() != null && (glType == GL20.GL_VERTEX_SHADER || glType == GL20.GL_FRAGMENT_SHADER)) {
                    obj.graphicsBindingMap = remapSpirvForSDLGPU(r.spirv(), glType);
                    obj.reflection = reflectStage(r.spirv(), glType == GL20.GL_VERTEX_SHADER);
                }
                return r;
            });
        } else {
            obj.spirvFuture = null;
        }
    }

    private static int shaderKindFor(ShaderObject obj) {
        return shaderKindFor(obj.type);
    }

    private static int shaderKindFor(int glShaderType) {
        if (glShaderType == GL20.GL_VERTEX_SHADER) return Shaderc.shaderc_vertex_shader;
        if (glShaderType == GL32.GL_GEOMETRY_SHADER) return Shaderc.shaderc_geometry_shader;
        if (glShaderType == GL43.GL_COMPUTE_SHADER) return Shaderc.shaderc_compute_shader;
        return Shaderc.shaderc_fragment_shader;
    }

    public record PrewarmTransformResult(String source, Set<String> boolUniforms) {}

    public static String applyPrewarmTransforms(String transformedSource, int glShaderType) {
        return applyPrewarmTransformsFull(transformedSource, glShaderType).source();
    }

    public static PrewarmTransformResult applyPrewarmTransformsFull(String transformedSource, int glShaderType) {
        final GLSLParser.Translation_unitContext root;
        try {
            root = GlslTransformUtils.parseFullQuiet(transformedSource);
        } catch (Exception e) {
            return new PrewarmTransformResult(transformedSource, Set.of());
        }
        final List<Edit> edits = new ArrayList<>();
        if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
            PerFrameBlockInjector.collectEdits(root, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock, edits);
        }
        final GlslVulkanPreprocess.Metadata meta = GlslVulkanPreprocess.collectEdits(transformedSource, root, glShaderType, "prewarm", true, edits);
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            ClipZRemap.collectEdits(root, edits);
        }
        SamplerStripper.collectEdits(root, transformedSource, edits);
        final String s = edits.isEmpty() ? transformedSource : GlslVulkanPreprocess.applyEdits(transformedSource, edits);
        return new PrewarmTransformResult(s, meta.boolUniforms());
    }

    public static PrewarmTransformResult applyPrewarmTransformsFull(String finalSource, GLSLParser.Translation_unitContext bodyTree, int headerLen, int glShaderType) {
        final String header = finalSource.substring(0, headerLen);
        final String body = finalSource.substring(headerLen);
        final List<Edit> edits = new ArrayList<>();
        if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
            PerFrameBlockInjector.collectEdits(bodyTree, GLSMHooks.perFrameUniformBlock, GLSMHooks.perPassUniformBlock, edits);
        }
        final GlslVulkanPreprocess.Metadata meta = GlslVulkanPreprocess.collectEdits(null, bodyTree, glShaderType, "prewarm", true, edits);
        if (glShaderType == GL20.GL_VERTEX_SHADER) {
            ClipZRemap.collectEdits(bodyTree, edits);
        }
        SamplerStripper.collectEdits(bodyTree, body, edits);
        String outHeader = header;
        if (meta.needsSamplerless()) {
            final int nl = header.indexOf('\n');
            outHeader = header.substring(0, nl) + "\n" + GlslVulkanPreprocess.SAMPLERLESS_EXTENSION + header.substring(nl);
        }
        if (edits.isEmpty() && outHeader == header) {
            return new PrewarmTransformResult(finalSource, meta.boolUniforms());
        }
        return new PrewarmTransformResult(outHeader + GlslVulkanPreprocess.applyEdits(body, edits), meta.boolUniforms());
    }

    public static void prewarmSpirv(String transformedSource, int glShaderType) {
        prewarmSpirv(transformedSource, null, 0, glShaderType);
    }

    public static void prewarmSpirv(String transformedSource, GLSLParser.Translation_unitContext bodyTree, int headerLen, int glShaderType) {
        if (glShaderType != GL20.GL_VERTEX_SHADER && glShaderType != GL20.GL_FRAGMENT_SHADER
            && glShaderType != GL43.GL_COMPUTE_SHADER && glShaderType != GL32.GL_GEOMETRY_SHADER) {
            return;
        }

        final PrewarmKey key = new PrewarmKey(transformedSource, glShaderType);
        synchronized (PREWARM_CACHE) {
            if (PREWARM_CACHE.containsKey(key)) return;
        }

        final PrewarmTransformResult pre = bodyTree != null
            ? applyPrewarmTransformsFull(transformedSource, bodyTree, headerLen, glShaderType)
            : applyPrewarmTransformsFull(transformedSource, glShaderType);

        final SpirvCompiler.Result r = SpirvCompiler.compile(pre.source(), shaderKindFor(glShaderType), "prewarm", SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) return;

        try {
            GraphicsBindingMap graphicsBindingMap = GraphicsBindingMap.EMPTY;
            if (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER) {
                graphicsBindingMap = remapSpirvForSDLGPU(r.spirv(), glShaderType);
            }
            final StageReflection reflection = (glShaderType == GL20.GL_VERTEX_SHADER || glShaderType == GL20.GL_FRAGMENT_SHADER)
                ? reflectStage(r.spirv(), glShaderType == GL20.GL_VERTEX_SHADER) : StageReflection.EMPTY;

            final byte[] heap = new byte[r.spirv().remaining()];
            r.spirv().duplicate().get(heap);
            synchronized (PREWARM_CACHE) {
                PREWARM_CACHE.putAndMoveToFirst(key, new PrewarmEntry(heap, reflection, graphicsBindingMap, pre.boolUniforms(), pre.source()));
                while (PREWARM_CACHE.size() > PREWARM_CACHE_MAX) PREWARM_CACHE.removeLast();
            }
        } finally {
            memFree(r.spirv());
        }
    }

    public void compileShader(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        if (obj == null) return;

        final SpirvCompiler.Result r;
        final boolean alreadyRemapped;
        if (obj.spirvFuture != null) {
            r = obj.spirvFuture.join();
            obj.spirvFuture = null;
            alreadyRemapped = true;
        } else {
            r = SpirvCompiler.compile(obj.source, shaderKindFor(obj), "shader" + shader, SpirvCompiler.Options.vulkanForced460Core());
            alreadyRemapped = false;
        }
        if (r.spirv() == null) {
            obj.infoLog = r.error() == null ? "(no error)" : r.error();
            obj.compiled = false;
            LOG.error("Shader {} compilation failed: {} (dump={})", shader, obj.infoLog, r.dumpPath());
            return;
        }
        obj.spirv = r.spirv();
        if (!alreadyRemapped && (obj.type == GL20.GL_VERTEX_SHADER || obj.type == GL20.GL_FRAGMENT_SHADER)) {
            obj.graphicsBindingMap = remapSpirvForSDLGPU(obj.spirv, obj.type);
            obj.reflection = reflectStage(obj.spirv, obj.isVertex());
        }
        obj.compiled = true;
        obj.infoLog = "";
    }

    public void deleteShader(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        if (obj == null) return;
        if (obj.attachCount > 0) {
            obj.deletePending = true;
            return;
        }
        shaderObjects.remove(shader);
        if (obj.spirv != null) memFree(obj.spirv);
    }

    private void releaseShaderAttachment(int shader) {
        if (shader == 0) return;
        final ShaderObject obj = shaderObjects.get(shader);
        if (obj == null) return;
        if (obj.attachCount > 0) obj.attachCount--;
        if (obj.deletePending && obj.attachCount == 0) {
            shaderObjects.remove(shader);
            if (obj.spirv != null) memFree(obj.spirv);
        }
    }

    public boolean isCompiled(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        return obj != null && obj.compiled;
    }

    public String getShaderInfoLog(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        return obj != null ? obj.infoLog : "";
    }

    public int getShaderType(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        return obj != null ? obj.type : 0;
    }

    public String getShaderSource(int shader) {
        final ShaderObject obj = shaderObjects.get(shader);
        return obj != null ? obj.source : "";
    }

    public int createProgram() {
        final int id = nextProgramId++;
        programObjects.put(id, new ProgramObject());
        return id;
    }

    public void attachShader(int program, int shader) {
        final ProgramObject prog = programObjects.get(program);
        final ShaderObject shObj = shaderObjects.get(shader);
        if (prog == null || shObj == null) return;

        final int oldSlot;
        if (shObj.isVertex())        { oldSlot = prog.vertexShader;   prog.vertexShader   = shader; }
        else if (shObj.isGeometry()) { oldSlot = prog.geometryShader; prog.geometryShader = shader; }
        else if (shObj.isCompute())  { oldSlot = prog.computeShader;  prog.computeShader  = shader; }
        else                         { oldSlot = prog.fragmentShader; prog.fragmentShader = shader; }

        if (oldSlot == shader) return;
        if (oldSlot != 0) {
            final ShaderObject oldObj = shaderObjects.get(oldSlot);
            if (oldObj != null && oldObj.attachCount > 0) oldObj.attachCount--;
        }
        shObj.attachCount++;
    }

    public void linkProgram(int program) {
        final ProgramObject prog = programObjects.get(program);
        if (prog == null) return;

        if (prog.computeShader != 0 && prog.vertexShader == 0 && prog.fragmentShader == 0) {
            linkComputeProgram(program, prog);
            return;
        }

        final ShaderObject vs = shaderObjects.get(prog.vertexShader);
        final ShaderObject fs = shaderObjects.get(prog.fragmentShader);
        final ShaderObject gs = prog.geometryShader != 0 ? shaderObjects.get(prog.geometryShader) : null;

        if (vs == null || !vs.compiled || fs == null || !fs.compiled) {
            prog.linked = false;
            prog.infoLog = "Shader not compiled";
            return;
        }

        if (gs != null && !gs.compiled) {
            prog.linked = false;
            prog.infoLog = "Geometry shader not compiled";
            return;
        }

        releaseProgramGpuResources(prog);

        prog.vertexSpirv = copyBuffer(vs.spirv);
        prog.fragmentSpirv = copyBuffer(fs.spirv);
        prog.vertexSource = vs.source;
        prog.vertexReflection = vs.reflection;
        prog.maxFragOutputLocation = fs.reflection.maxOutputLocation();
        dumpLinkedSource(program, vs.source, fs.source);

        applyVaryingMatch(prog, vs.reflection, fs.reflection);
        applyAttribLocationsAndInputMask(prog, vs.reflection);

        prog.vertexResources = vs.reflection.counts();
        prog.fragmentResources = fs.reflection.counts();
        prog.vertexGraphicsBindingMap = vs.graphicsBindingMap;
        prog.fragmentGraphicsBindingMap = fs.graphicsBindingMap;
        prog.vertexSamplerNames = vs.reflection.samplerNames();
        prog.fragmentSamplerNames = fs.reflection.samplerNames();
        prog.samplerUnitsDirty = true;
        prog.allSamplerNames.clear();
        prog.allSamplerNames.addAll(prog.vertexSamplerNames);
        prog.allSamplerNames.addAll(prog.fragmentSamplerNames);
        prog.vertexImageNames = vs.reflection.storageImageNames();
        prog.fragmentImageNames = fs.reflection.storageImageNames();
        prog.allImageNames.clear();
        prog.allImageNames.addAll(prog.vertexImageNames);
        prog.allImageNames.addAll(prog.fragmentImageNames);
        prog.imageTextureUnits.clear();
        prog.boolUniforms.clear();
        prog.boolUniforms.addAll(vs.boolUniforms);
        prog.boolUniforms.addAll(fs.boolUniforms);
        if (gs != null) prog.boolUniforms.addAll(gs.boolUniforms);
        LOG.debug("Program {} resources: VS={} FS={} vsSamplers={} fsSamplers={}", program,
            prog.vertexResources, prog.fragmentResources, prog.vertexSamplerNames, prog.fragmentSamplerNames);
        if (gs != null) {
            LOG.warn("Program {} has geometry shader attached -- GS emulation not yet implemented, GS will be ignored", program);
        }
        if (prog.vertexGraphicsBindingMap.rwStorageTextureGlSlots().length > 0
                || prog.fragmentGraphicsBindingMap.rwStorageTextureGlSlots().length > 0) {
            LOG.error("Program {} has a graphics-stage RW storage image (SDL_GPU unsupported); ensure RwImageStoreExtractor stripped it. Rejecting link.", program);
            prog.linked = false;
            prog.infoLog = "graphics-stage RW storage image present";
            return;
        }

        prog.linked = true;
        prog.infoLog = "";

        applyUboMembers(prog, vs.reflection, true);
        applyUboMembers(prog, fs.reflection, false);
        applyBlockMembers(prog, vs.reflection);
        applyBlockMembers(prog, fs.reflection);
        registerExtraNames(prog, vs.reflection);
        registerExtraNames(prog, fs.reflection);

        for (final String name : prog.allSamplerNames) ensureLocation(prog, name);
        for (final String name : prog.allImageNames) ensureLocation(prog, name);

        prog.buildUniformSlotArrays();

    }

    private void linkComputeProgram(int program, ProgramObject prog) {
        final ShaderObject cs = shaderObjects.get(prog.computeShader);
        if (cs == null || !cs.compiled || cs.spirv == null) {
            prog.linked = false;
            prog.infoLog = "Compute shader not compiled";
            return;
        }

        releaseProgramGpuResources(prog);

        prog.computeSpirv = copyBuffer(cs.spirv);
        prog.computeBindingMap = remapSpirvForComputeSDLGPU(prog.computeSpirv);
        if (!validateComputeBindingMap(prog.computeBindingMap, program)) {
            prog.linked = false;
            prog.infoLog = "Compute shader has out-of-range layout(binding=N); see prior log for details";
            return;
        }

        final StageReflection refl = cs.reflection != StageReflection.EMPTY ? cs.reflection : reflectStage(prog.computeSpirv, false);
        final ResourceCounts counts = refl.counts();
        prog.computeResources = counts;

        prog.vertexSamplerNames = List.of();
        prog.fragmentSamplerNames = refl.samplerNames();
        prog.samplerUnitsDirty = true;
        prog.allSamplerNames.clear();
        prog.allSamplerNames.addAll(refl.samplerNames());
        prog.vertexImageNames = List.of();
        prog.fragmentImageNames = refl.storageImageNames();
        prog.allImageNames.clear();
        prog.allImageNames.addAll(refl.storageImageNames());
        prog.imageTextureUnits.clear();
        prog.boolUniforms.clear();
        prog.boolUniforms.addAll(cs.boolUniforms);

        applyUboMembers(prog, refl, false);
        registerExtraNames(prog, refl);
        for (final String name : prog.allSamplerNames) ensureLocation(prog, name);
        for (final String name : prog.allImageNames) ensureLocation(prog, name);
        prog.buildUniformSlotArrays();

        final int[] localSize = CrossCompileUtil.parseLocalSize(prog.computeSpirv);
        final CrossCompiled cc = crossCompile(prog.computeSpirv, GL43.GL_COMPUTE_SHADER);

        try (var stack = stackPush()) {
            final SDL_GPUComputePipelineCreateInfo ci = SDL_GPUComputePipelineCreateInfo.calloc(stack)
                .code(cc.code())
                .entrypoint(stack.ASCII(cc.entrypoint()))
                .format(cc.targetFormat())
                .num_samplers(counts.numSamplers())
                .num_readonly_storage_textures(refl.numReadonlyStorageTextures())
                .num_readonly_storage_buffers(refl.numReadonlyStorageBuffers())
                .num_readwrite_storage_textures(refl.numReadwriteStorageTextures())
                .num_readwrite_storage_buffers(refl.numReadwriteStorageBuffers())
                .num_uniform_buffers(counts.numUBOs())
                .threadcount_x(localSize[0]).threadcount_y(localSize[1]).threadcount_z(localSize[2]);

            LOG.debug("Creating SDL compute pipeline: program={} fmt=0x{} entry={} samplers={} ubos={} ssbo(ro/rw)={}/{} storTex(ro/rw)={}/{} localSize={}x{}x{}",
                program, Integer.toHexString(cc.targetFormat()), cc.entrypoint(),
                counts.numSamplers(), counts.numUBOs(),
                refl.numReadonlyStorageBuffers(), refl.numReadwriteStorageBuffers(),
                refl.numReadonlyStorageTextures(), refl.numReadwriteStorageTextures(),
                localSize[0], localSize[1], localSize[2]);

            final long handle = SDL_CreateGPUComputePipeline(device.getDevice(), ci);
            if (handle == 0) {
                LOG.error("Failed to create SDL GPU compute pipeline for program {}: {}", program, SDLError.SDL_GetError());
                prog.linked = false;
                prog.infoLog = "Compute pipeline creation failed: " + SDLError.SDL_GetError();
                return;
            }
            prog.sdlComputePipeline = handle;
        } finally {
            if (cc.ownsCode()) memFree(cc.code());
        }

        prog.linked = true;
        prog.infoLog = "";
    }

    static void applyUboMembers(ProgramObject prog, StageReflection refl, boolean isVertex) {
        if (refl.uboSize() > 0) {
            if (isVertex) prog.vertexUboSize = refl.uboSize();
            else prog.fragmentUboSize = refl.uboSize();
        }
        for (UboMember m : refl.uboMembers()) {
            final int loc = ensureLocation(prog, m.name());
            final UniformMemberInfo info = new UniformMemberInfo(
                m.offset(), m.size(), m.arrayStride(), isVertex,
                m.vectorSize(), m.columns(), m.baseType(), m.arrayLen()
            );
            prog.locationToMemberInfo.put(loc, info);
            if (isVertex) prog.vertexMemberInfo.put(loc, info);
            else prog.fragmentMemberInfo.put(loc, info);
        }
    }

    static void applyBlockMembers(ProgramObject prog, StageReflection refl) {
        for (int b = 0; b < BLOCK_COUNT; b++) {
            final BlockReflection block = refl.blocks()[b];
            if (block.members().isEmpty()) continue;
            prog.blockSize[b] = Math.max(prog.blockSize[b], block.size());
            prog.blockBinding[b] = block.binding();
            for (UboMember m : block.members()) {
                final int loc = ensureLocation(prog, m.name());
                if (prog.blockMemberInfo[b].containsKey(loc)) continue;
                prog.blockMemberInfo[b].put(loc, new UniformMemberInfo(
                    m.offset(), m.size(), m.arrayStride(), false,
                    m.vectorSize(), m.columns(), m.baseType(), m.arrayLen()));
            }
        }
    }

    private static void registerExtraNames(ProgramObject prog, StageReflection refl) {
        for (String name : refl.extraUniformNames()) ensureLocation(prog, name);
    }

    private static int ensureLocation(ProgramObject prog, String name) {
        final int existing = prog.nameToLocation.getInt(name);
        if (existing != -1) return existing;
        final int loc = prog.nextUniformLocation++;
        prog.nameToLocation.put(name, loc);
        prog.locationToName.put(loc, name);
        return loc;
    }

    private static void applyVaryingMatch(ProgramObject prog, StageReflection vs, StageReflection fs) {
        if (vs.vsOutputs().isEmpty() || fs.fsInputs().isEmpty() || prog.fragmentSpirv == null) return;
        final HashMap<String, Integer> vsOutLocs = new HashMap<>(vs.vsOutputs().size() * 2);
        for (VsOutput o : vs.vsOutputs()) vsOutLocs.put(o.name(), o.originalLocation());
        final IntBuffer fsBuf = prog.fragmentSpirv.asIntBuffer();
        for (FsInput fi : fs.fsInputs()) {
            final Integer vsLoc = vsOutLocs.get(fi.name());
            if (vsLoc == null || vsLoc == fi.originalLocation()) continue;
            if (fsBuf.get(fi.binaryOffset()) != fi.originalLocation()) {
                LOG.error("applyVaryingMatch: SPIR-V binary offset sanity check failed for '{}' (expected {}, got {})", fi.name(), fi.originalLocation(), fsBuf.get(fi.binaryOffset()));
                continue;
            }
            fsBuf.put(fi.binaryOffset(), vsLoc);
        }
    }

    static void applyAttribLocationsAndInputMask(ProgramObject prog, StageReflection vs) {
        prog.resolvedAttribLocations.clear();
        prog.activeAttribNames = null;
        if (vs.vsInputs().isEmpty() || prog.vertexSpirv == null) return;
        prog.vertexInputMask = patchAttribLocations(prog.vertexSpirv, vs, prog.attribLocationBindings,
            prog.vertexInputVecSize, prog.vertexInputBaseType, prog.vertexInputName, prog.resolvedAttribLocations);
    }

    private static int patchAttribLocations(ByteBuffer spirv, StageReflection vs, Object2IntOpenHashMap<String> bindings, int[] outVecSize, int[] outBaseType, String[] outName, Object2IntOpenHashMap<String> resolvedOut) {
        final IntBuffer vsBuf = spirv.asIntBuffer();
        int mask = 0;
        if (outName != null) Arrays.fill(outName, null);
        for (VsInput vi : vs.vsInputs()) {
            int finalLoc = vi.originalLocation();
            final int desired = bindings.getInt(vi.name());
            if (desired != -1 && desired != vi.originalLocation()) {
                if (vsBuf.get(vi.binaryOffset()) != vi.originalLocation()) {
                    LOG.error("applyAttribLocations: sanity check failed for '{}' (expected {}, got {})", vi.name(), vi.originalLocation(), vsBuf.get(vi.binaryOffset()));
                } else {
                    vsBuf.put(vi.binaryOffset(), desired);
                    finalLoc = desired;
                }
            }
            if (resolvedOut != null) resolvedOut.put(vi.name(), finalLoc);
            if (finalLoc < 0 || finalLoc >= 16) continue;
            mask |= (1 << finalLoc);
            outVecSize[finalLoc] = vi.vecSize();
            outBaseType[finalLoc] = vi.baseType();
            if (outName != null) outName[finalLoc] = vi.name();
        }
        return mask;
    }

    public VertexVariant getOrBuildVertexVariant(int program, long key, List<UscaledRetype.Attrib> attribs) {
        final ProgramObject prog = programObjects.get(program);
        if (prog == null || !prog.linked || prog.vertexSource.isEmpty()) return null;
        final VertexVariant hit = prog.vertexVariants.get(key);
        if (hit != null) return hit;
        if (prog.vertexVariantFailed.contains(key)) return null;

        final VertexVariant built = buildVertexVariant(program, prog, attribs);
        if (built == null) {
            prog.vertexVariantFailed.add(key);
            return null;
        }
        prog.vertexVariants.put(key, built);
        return built;
    }

    private VertexVariant buildVertexVariant(int program, ProgramObject prog, List<UscaledRetype.Attrib> attribs) {
        final String preprocessed = SpirvCompiler.preprocess(prog.vertexSource, Shaderc.shaderc_vertex_shader, "variant" + program, SpirvCompiler.Options.vulkanForced460Core());
        if (preprocessed == null) {
            LOG.warn("Vertex attribute conversion: program {} preprocessing failed; falling back to normalized coercion, values will be scaled", program);
            return null;
        }
        final String src = UscaledRetype.retype(preprocessed, attribs);
        if (src == null) {
            LOG.warn("Vertex attribute conversion: program {} source rewrite failed for {}; falling back to normalized coercion, values will be scaled", program, attribs);
            return null;
        }
        final SpirvCompiler.Result r = SpirvCompiler.compile(src, Shaderc.shaderc_vertex_shader, "variant" + program, SpirvCompiler.Options.vulkanForced460Core());
        if (r.spirv() == null) {
            LOG.warn("Vertex attribute conversion: program {} variant failed to compile ({}); falling back to normalized coercion, values will be scaled", program, r.error());
            return null;
        }
        try {
            remapSpirvForSDLGPU(r.spirv(), GL20.GL_VERTEX_SHADER);
            final StageReflection refl = reflectStage(r.spirv(), true);
            if (!sameVaryingLayout(prog.vertexReflection.vsOutputs(), refl.vsOutputs())) {
                LOG.error("Vertex attribute conversion: program {} variant changed VS output locations; the fragment stage is patched against the base layout. Falling back to normalized coercion.", program);
                return null;
            }
            final VertexVariant v = new VertexVariant();
            v.spirv = copyBuffer(r.spirv());

            final Object2IntOpenHashMap<String> bindings = new Object2IntOpenHashMap<>(prog.attribLocationBindings);
            bindings.defaultReturnValue(-1);
            for (UscaledRetype.Attrib a : attribs) bindings.put(a.name() + UscaledRetype.SUFFIX, a.location());
            v.inputMask = patchAttribLocations(v.spirv, refl, bindings, v.inputVecSize, v.inputBaseType, null, null);
            if (v.inputMask != prog.vertexInputMask) {
                memFree(v.spirv);
                LOG.error("Vertex attribute conversion: program {} variant resolved inputs to 0x{} but the program binds 0x{}; a renamed input landed on the wrong location. Falling back to normalized coercion.",
                    program, Integer.toHexString(v.inputMask), Integer.toHexString(prog.vertexInputMask));
                return null;
            }
            v.sdlShader = createSDLShader(v.spirv, SDL_GPU_SHADERSTAGE_VERTEX, prog.vertexResources.numSamplers(), prog.vertexResources.numUBOs(), prog.vertexResources.numStorageBuffers(), prog.vertexResources.numStorageTextures());
            if (v.sdlShader == 0) {
                memFree(v.spirv);
                LOG.error("Vertex attribute conversion: program {} variant shader creation failed: {}", program, SDLError.SDL_GetError());
                return null;
            }
            LOG.info("Vertex attribute conversion: program {} converted {}", program, attribs);
            if (SystemProperties.DUMP_SHADERS) dumpVariantSource(program, src);
            return v;
        } finally {
            memFree(r.spirv());
        }
    }

    private static boolean linkDumpDirCleared;

    private static void dumpLinkedSource(int program, String vertexSrc, String fragmentSrc) {
        final Path dir = SystemProperties.shaderDumpDir("link");
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            if (!linkDumpDirCleared) {
                linkDumpDirCleared = true;
                try (var entries = Files.list(dir)) {
                    for (Path p : entries.toList()) Files.deleteIfExists(p);
                }
            }
            Files.writeString(dir.resolve("prog" + program + "_vs.glsl"), vertexSrc);
            Files.writeString(dir.resolve("prog" + program + "_fs.glsl"), fragmentSrc);
        } catch (IOException e) {
            LOG.warn("Failed to dump linked source for program {}: {}", program, e.toString());
        }
    }

    private static void dumpVariantSource(int program, String src) {
        try {
            final Path dir = Path.of("patched_shaders");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("variant_" + program + ".vsh"), src);
        } catch (IOException e) {
            LOG.warn("Failed to dump vertex attribute conversion variant for program {}: {}", program, e.toString());
        }
    }

    private static boolean sameVaryingLayout(List<VsOutput> base, List<VsOutput> variant) {
        if (base.size() != variant.size()) return false;
        for (VsOutput b : base) {
            boolean matched = false;
            for (VsOutput v : variant) {
                if (b.name().equals(v.name())) {
                    if (b.originalLocation() != v.originalLocation()) return false;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private void releaseProgramGpuResources(ProgramObject prog) {
        final long dev = device.getDevice();
        for (VertexVariant v : prog.vertexVariants.values()) {
            if (v.sdlShader != 0) SDL_ReleaseGPUShader(dev, v.sdlShader);
            if (v.spirv != null) memFree(v.spirv);
        }
        prog.vertexVariants.clear();
        prog.vertexVariantFailed.clear();
        if (prog.sdlVertexShader   != 0) { SDL_ReleaseGPUShader(dev, prog.sdlVertexShader);   prog.sdlVertexShader   = 0; }
        if (prog.sdlFragmentShader != 0) { SDL_ReleaseGPUShader(dev, prog.sdlFragmentShader); prog.sdlFragmentShader = 0; }
        if (prog.sdlComputePipeline != 0) { SDL_ReleaseGPUComputePipeline(dev, prog.sdlComputePipeline); prog.sdlComputePipeline = 0; }
        if (prog.vertexSpirv   != null) { memFree(prog.vertexSpirv);   prog.vertexSpirv   = null; }
        if (prog.fragmentSpirv != null) { memFree(prog.fragmentSpirv); prog.fragmentSpirv = null; }
        if (prog.geometrySpirv != null) { memFree(prog.geometrySpirv); prog.geometrySpirv = null; }
        if (prog.computeSpirv  != null) { memFree(prog.computeSpirv);  prog.computeSpirv  = null; }
        if (uniformReleaseListener != null) uniformReleaseListener.accept(prog);
    }

    public void releaseProgramFinal(int program) {
        final ProgramObject prog = programObjects.remove(program);
        if (prog == null) return;
        releaseProgramGpuResources(prog);
        forEachAttachedShader(prog, this::releaseShaderAttachment);
    }

    private static void forEachAttachedShader(ProgramObject prog, IntConsumer body) {
        if (prog.vertexShader   != 0) body.accept(prog.vertexShader);
        if (prog.fragmentShader != 0) body.accept(prog.fragmentShader);
        if (prog.geometryShader != 0) body.accept(prog.geometryShader);
        if (prog.computeShader  != 0) body.accept(prog.computeShader);
    }

    public void markProgramForDeletion(int program) {
        final ProgramObject prog = programObjects.get(program);
        if (prog != null) prog.deletePending = true;
    }

    public boolean isLinked(int program) {
        final ProgramObject prog = programObjects.get(program);
        return prog != null && prog.linked;
    }

    public String getProgramInfoLog(int program) {
        final ProgramObject prog = programObjects.get(program);
        return prog != null ? prog.infoLog : "";
    }

    public boolean isShader(int id)  { return shaderObjects.containsKey(id); }
    public boolean isProgram(int id) { return programObjects.containsKey(id); }

    public int getAttachedShaderCount(int program) {
        final ProgramObject prog = programObjects.get(program);
        if (prog == null) return 0;
        final int[] n = { 0 };
        forEachAttachedShader(prog, s -> n[0]++);
        return n[0];
    }

    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        final ProgramObject prog = programObjects.get(program);
        final int[] n = { 0 };
        if (prog != null && shaders != null) {
            forEachAttachedShader(prog, s -> {
                if (shaders.remaining() > n[0]) shaders.put(shaders.position() + n[0]++, s);
            });
        }
        if (count != null && count.remaining() > 0) count.put(count.position(), n[0]);
    }

    public ProgramObject getProgram(int program) {
        return programObjects.get(program);
    }

    public long createSDLShader(ByteBuffer spirv, int stage, int numSamplers, int numUniformBuffers, int numStorageBuffers, int numStorageTextures) {
        final CrossCompiled cc = crossCompile(spirv, glShaderTypeForSdlStage(stage));
        try (var stack = stackPush()) {
            final SDL_GPUShaderCreateInfo ci = SDL_GPUShaderCreateInfo.calloc(stack)
                .code(cc.code())
                .entrypoint(stack.ASCII(cc.entrypoint()))
                .format(cc.targetFormat())
                .stage(stage)
                .num_samplers(numSamplers)
                .num_uniform_buffers(numUniformBuffers)
                .num_storage_buffers(numStorageBuffers)
                .num_storage_textures(numStorageTextures);

            LOG.debug("Creating SDL shader: stage={} samplers={} ubos={} ssbos={} storTex={} fmt=0x{} entry={} code=[pos={} lim={} rem={}]",
                stage, numSamplers, numUniformBuffers, numStorageBuffers, numStorageTextures,
                Integer.toHexString(cc.targetFormat()), cc.entrypoint(),
                cc.code().position(), cc.code().limit(), cc.code().remaining());

            final long handle = SDL_CreateGPUShader(device.getDevice(), ci);
            if (handle == 0) {
                LOG.error("Failed to create SDL GPU shader: {}", SDLError.SDL_GetError());
            }
            return handle;
        } finally {
            if (cc.ownsCode()) memFree(cc.code());
        }
    }

    private record CrossCompiled(int targetFormat, ByteBuffer code, String entrypoint, boolean ownsCode) {}

    private CrossCompiled crossCompile(ByteBuffer spirv, int glShaderType) {
        if (device.supportsSpirv()) {
            return new CrossCompiled(SDL_GPU_SHADERFORMAT_SPIRV, spirv, "main", false);
        }
        if (device.supportsMsl()) {
            final MslCrossCompile.Output out = MslCrossCompile.compile(spirv, glShaderType);
            return new CrossCompiled(SDL_GPU_SHADERFORMAT_MSL, out.code(), out.entrypoint(), true);
        }
        if (device.supportsDxbc()) {
            final DxbcCrossCompile.Output out = DxbcCrossCompile.compile(spirv, glShaderType);
            return new CrossCompiled(SDL_GPU_SHADERFORMAT_DXBC, out.code(), out.entrypoint(), true);
        }
        if (device.supportsDxil()) {
            throw new UnsupportedOperationException("SPIR-V -> DXIL cross-compilation not yet implemented");
        }
        throw new RuntimeException("No supported shader format available");
    }

    private static int glShaderTypeForSdlStage(int sdlStage) {
        if (sdlStage == SDL_GPU_SHADERSTAGE_VERTEX) return GL20.GL_VERTEX_SHADER;
        if (sdlStage == SDL_GPU_SHADERSTAGE_FRAGMENT) return GL20.GL_FRAGMENT_SHADER;
        throw new IllegalArgumentException("Unsupported SDL_GPU shader stage: " + sdlStage);
    }

    public record ResourceCounts(int numSamplers, int numUBOs, int numStorageBuffers, int numStorageTextures) {
        public static final ResourceCounts EMPTY = new ResourceCounts(0, 0, 0, 0);
    }


    public int getUniformLocation(int program, String name) {
        final ProgramObject prog = programObjects.get(program);
        if (prog == null || !prog.linked) return -1;
        return prog.nameToLocation.getInt(name);
    }

    /**
     * Remap SPIR-V descriptor set/binding numbers to match SDL GPU's expected layout.
     */
    public static GraphicsBindingMap remapSpirvForSDLGPU(ByteBuffer spirv, int shaderType) {
        final boolean isVertex = (shaderType == GL20.GL_VERTEX_SHADER);
        final boolean isFragment = (shaderType == GL20.GL_FRAGMENT_SHADER);
        if (!isVertex && !isFragment) return GraphicsBindingMap.EMPTY;

        final int resourceSet = isVertex ? 0 : 2;
        final int uboSet = isVertex ? 1 : 3;

        final Int2IntOpenHashMap idToNewSet = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap idToNewBinding = new Int2IntOpenHashMap();
        final IntArrayList roStorageTextureGlSlots = new IntArrayList();
        final IntArrayList rwStorageTextureGlSlots = new IntArrayList();
        final IntArrayList roSsboGlSlots = new IntArrayList();

        withSpvc(spirv, null, (compiler, resources, stack) -> {
            int n = 0;
            n = collectResourceIds(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE,    idToNewSet, idToNewBinding, resourceSet, n, stack);
            n = collectGraphicsStorageImages(resources, compiler, idToNewSet, idToNewBinding, resourceSet, n, roStorageTextureGlSlots, rwStorageTextureGlSlots, stack);
            n = collectComputeBindings(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, idToNewSet, idToNewBinding, roSsboGlSlots, resourceSet, n, stack);
            collectResourceIds(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, idToNewSet, idToNewBinding, uboSet, 0, stack);
            warnOnSeparateSamplers(resources, stack, isVertex ? "vertex" : "fragment");
            return null;
        });

        if (idToNewSet.isEmpty()) return GraphicsBindingMap.EMPTY;
        patchSpirvDecorations(spirv.asIntBuffer(), idToNewSet, idToNewBinding);
        LOG.debug("Remapped {} SPIR-V resources for {} shader (resourceSet={}, uboSet={})", idToNewSet.size(), isVertex ? "vertex" : "fragment", resourceSet, uboSet);
        return new GraphicsBindingMap(roStorageTextureGlSlots.toIntArray(), rwStorageTextureGlSlots.toIntArray(), roSsboGlSlots.toIntArray());
    }

    private static void warnOnSeparateSamplers(long resources, MemoryStack stack, String stage) {
        if (CrossCompileUtil.countResources(resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS, stack) > 0 && separateSamplerWarned.compareAndSet(false, true)) {
            LOG.error("{} shader declares a separate sampler; SDL_GPU cannot bind one and its descriptor will be left unremapped", stage);
        }
    }

    private static final AtomicBoolean separateSamplerWarned = new AtomicBoolean();

    private static int collectGraphicsStorageImages(long resources, long compiler, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, int targetSet, int nextBinding, IntArrayList roOut, IntArrayList rwOut, MemoryStack stack) {
        final int[] next = { nextBinding };
        forEachResource(resources, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, stack, (j, res) -> {
            final int spvId = res.id();
            roOut.add(Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding));
            idToNewSet.put(spvId, targetSet);
            idToNewBinding.put(spvId, next[0]++);
        });
        forEachResource(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, stack, (j, res) -> {
            final int spvId = res.id();
            final int oldBinding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            idToNewSet.put(spvId, targetSet);
            idToNewBinding.put(spvId, next[0]++);
            if (Spvc.spvc_compiler_has_decoration(compiler, spvId, Spv.SpvDecorationNonWritable)) {
                roOut.add(oldBinding);
            } else {
                rwOut.add(oldBinding);
            }
        });
        return next[0];
    }

    public static ComputeBindingMap remapSpirvForComputeSDLGPU(ByteBuffer spirv) {
        final Int2IntOpenHashMap idToNewSet = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap idToNewBinding = new Int2IntOpenHashMap();
        final IntArrayList samplerSlots = new IntArrayList();
        final IntArrayList roTexSlots = new IntArrayList();
        final IntArrayList rwTexSlots = new IntArrayList();
        final IntArrayList roBufSlots = new IntArrayList();
        final IntArrayList rwBufSlots = new IntArrayList();
        final IntArrayList uboSlots = new IntArrayList();

        final List<String> samplerNames = new ArrayList<>();
        final List<String> roTexNames = new ArrayList<>();
        final List<String> rwTexNames = new ArrayList<>();
        final BooleanArrayList uboDefault = new BooleanArrayList();
        final IntArrayList uboSizes = new IntArrayList();
        final IntArrayList roTexFormats = new IntArrayList();
        final IntArrayList rwTexFormats = new IntArrayList();
        final IntArrayList roTexTargets = new IntArrayList();
        final IntArrayList rwTexTargets = new IntArrayList();

        final StorageSplitOut roTex = new StorageSplitOut(roTexSlots, roTexNames, roTexFormats, roTexTargets);
        final StorageSplitOut rwTex = new StorageSplitOut(rwTexSlots, rwTexNames, rwTexFormats, rwTexTargets);

        final ComputeBindingMap err = withSpvc(spirv, ComputeBindingMap.EMPTY, (compiler, resources, stack) -> {
            int s0 = 0, s1 = 0;
            s0 = collectComputeBindings(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE,  idToNewSet, idToNewBinding, samplerSlots, 0, s0, stack, samplerNames);
            final int[] sepSplit = collectComputeStorageSplit(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, idToNewSet, idToNewBinding, roTex, rwTex, s0, s1, stack, (c, id, st) -> true);
            s0 = sepSplit[0]; s1 = sepSplit[1];
            final int[] imgSplit = collectComputeStorageSplit(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, idToNewSet, idToNewBinding, roTex, rwTex, s0, s1, stack, (c, id, st) -> Spvc.spvc_compiler_has_decoration(c, id, Spv.SpvDecorationNonWritable));
            s0 = imgSplit[0]; s1 = imgSplit[1];
            final int[] bufSplit = collectComputeStorageSplit(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, idToNewSet, idToNewBinding, StorageSplitOut.slotsOnly(roBufSlots), StorageSplitOut.slotsOnly(rwBufSlots), s0, s1, stack, CrossCompileUtil::isStorageBufferReadOnly);
            s0 = bufSplit[0]; s1 = bufSplit[1];
            collectComputeUbos(resources, compiler, idToNewSet, idToNewBinding, uboSlots, uboDefault, uboSizes, stack);
            warnOnSeparateSamplers(resources, stack, "compute");
            return null;
        });
        if (err != null) return err;

        if (!idToNewSet.isEmpty()) {
            patchSpirvDecorations(spirv.asIntBuffer(), idToNewSet, idToNewBinding);
        }

        return new ComputeBindingMap(
            samplerSlots.toIntArray(),
            roTexSlots.toIntArray(),
            rwTexSlots.toIntArray(),
            roBufSlots.toIntArray(),
            rwBufSlots.toIntArray(),
            uboSlots.toIntArray(),
            samplerNames.toArray(new String[0]),
            roTexNames.toArray(new String[0]),
            rwTexNames.toArray(new String[0]),
            uboDefault.toBooleanArray(),
            uboSizes.toIntArray(),
            roTexFormats.toIntArray(),
            rwTexFormats.toIntArray(),
            roTexTargets.toIntArray(),
            rwTexTargets.toIntArray());
    }

    private static void collectComputeUbos(long resources, long compiler, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, IntArrayList glSlotsOut, BooleanArrayList isDefaultOut, IntArrayList sizesOut, MemoryStack stack) {
        final int[] next = { 0 };
        final PointerBuffer pSize = stack.pointers(0);
        forEachResource(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, stack, (j, res) -> {
            final int spvId = res.id();
            final int oldBinding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            idToNewSet.put(spvId, 2);
            idToNewBinding.put(spvId, next[0]++);
            glSlotsOut.add(oldBinding);
            final String resName = res.nameString();
            final String typeName = Spvc.spvc_compiler_get_name(compiler, res.base_type_id());
            isDefaultOut.add((resName != null && resName.endsWith("DefaultUniformBlock")) || (typeName != null && typeName.endsWith("DefaultUniformBlock")));
            int size = 0;
            final long typeHandle = Spvc.spvc_compiler_get_type_handle(compiler, res.base_type_id());
            if (Spvc.spvc_compiler_get_declared_struct_size(compiler, typeHandle, pSize) == Spvc.SPVC_SUCCESS) {
                size = (int) pSize.get(0);
            }
            sizesOut.add(size);
        });
    }

    @FunctionalInterface
    private interface SpvcBody<T> { T run(long compiler, long resources, MemoryStack stack); }

    private static <T> T withSpvc(ByteBuffer spirv, T onError, SpvcBody<T> body) {
        try (var stack = stackPush()) {
            final PointerBuffer pContext = stack.pointers(0);
            if (Spvc.spvc_context_create(pContext) != Spvc.SPVC_SUCCESS) {
                LOG.error("SPIRV-Cross: spvc_context_create failed");
                return onError;
            }
            final long ctx = pContext.get(0);
            try {
                final IntBuffer spirvWords = spirv.asIntBuffer();
                final PointerBuffer pParsedIR = stack.pointers(0);
                if (Spvc.spvc_context_parse_spirv(ctx, spirvWords, spirvWords.remaining(), pParsedIR) != Spvc.SPVC_SUCCESS) {
                    LOG.error("SPIRV-Cross: parse_spirv failed");
                    return onError;
                }
                final PointerBuffer pCompiler = stack.pointers(0);
                if (Spvc.spvc_context_create_compiler(ctx, Spvc.SPVC_BACKEND_GLSL, pParsedIR.get(0), Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler) != Spvc.SPVC_SUCCESS) {
                    LOG.error("SPIRV-Cross: create_compiler failed");
                    return onError;
                }
                final long compiler = pCompiler.get(0);
                final PointerBuffer pResources = stack.pointers(0);
                if (Spvc.spvc_compiler_create_shader_resources(compiler, pResources) != Spvc.SPVC_SUCCESS) {
                    LOG.error("SPIRV-Cross: create_shader_resources failed");
                    return onError;
                }
                return body.run(compiler, pResources.get(0), stack);
            } finally {
                Spvc.spvc_context_destroy(ctx);
            }
        }
    }

    private static void patchSpirvDecorations(IntBuffer words, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding) {
        final int wordCount = words.limit();
        int i = 5; // skip header
        while (i < wordCount) {
            final int word0 = words.get(i);
            final int opcode = word0 & 0xFFFF;
            final int instrLen = (word0 >>> 16) & 0xFFFF;
            if (instrLen == 0) break; // malformed
            if (opcode == 71 && instrLen >= 4) { // OpDecorate
                final int targetId = words.get(i + 1);
                if (idToNewSet.containsKey(targetId)) {
                    final int decoration = words.get(i + 2);
                    if (decoration == Spv.SpvDecorationBinding) {
                        words.put(i + 3, idToNewBinding.get(targetId));
                    } else if (decoration == Spv.SpvDecorationDescriptorSet) {
                        words.put(i + 3, idToNewSet.get(targetId));
                    }
                }
            }
            i += instrLen;
        }
    }

    @FunctionalInterface
    private interface ResourceConsumer { void accept(int index, SpvcReflectedResource res); }

    @FunctionalInterface
    private interface ReadOnlyClassifier { boolean isReadOnly(long compiler, int spvId, MemoryStack stack); }

    private static void forEachResource(long resources, int type, MemoryStack stack, ResourceConsumer body) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, type, pList, pCount) != Spvc.SPVC_SUCCESS) return;
        final int count = (int) pCount.get(0);
        if (count == 0) return;
        final SpvcReflectedResource.Buffer list = SpvcReflectedResource.create(pList.get(0), count);
        for (int j = 0; j < count; j++) body.accept(j, list.get(j));
    }

    private static int collectComputeBindings(long resources, long compiler, int resourceType, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, IntArrayList glSlotsOut, int targetSet, int nextBinding, MemoryStack stack) {
        return collectComputeBindings(resources, compiler, resourceType, idToNewSet, idToNewBinding, glSlotsOut, targetSet, nextBinding, stack, null);
    }

    private static int collectComputeBindings(long resources, long compiler, int resourceType, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, IntArrayList glSlotsOut, int targetSet, int nextBinding, MemoryStack stack, List<String> namesOut) {
        final int[] next = { nextBinding };
        forEachResource(resources, resourceType, stack, (j, res) -> {
            final int spvId = res.id();
            final int oldBinding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            idToNewSet.put(spvId, targetSet);
            idToNewBinding.put(spvId, next[0]++);
            glSlotsOut.add(oldBinding);
            if (namesOut != null) namesOut.add(res.nameString());
        });
        return next[0];
    }

    private static boolean validateComputeBindingMap(ComputeBindingMap map, int program) {
        final int maxSampler = 32;
        final int maxImage = ContextState.MAX_IMAGE_UNITS;
        final int maxIndexed = ContextState.MAX_INDEXED_BUFFERS;
        return checkSlots(map.samplerGlSlots(), maxSampler, "sampler", program)
            && checkSlots(map.roStorageTextureGlSlots(), maxImage, "readonly storage image", program)
            && checkSlots(map.rwStorageTextureGlSlots(), maxImage, "readwrite storage image", program)
            && checkSlots(map.roSsboGlSlots(), maxIndexed, "readonly SSBO", program)
            && checkSlots(map.rwSsboGlSlots(), maxIndexed, "readwrite SSBO", program)
            && checkSlots(map.uboGlSlots(), maxIndexed, "UBO", program);
    }

    private static boolean checkSlots(int[] slots, int max, String kind, int program) {
        for (int s : slots) {
            if (s < 0 || s >= max) {
                LOG.error("Compute program {}: {} layout(binding={}) is out of range [0,{}); rejecting link", program, kind, s, max);
                return false;
            }
        }
        return true;
    }

    private record StorageSplitOut(IntArrayList glSlots, List<String> names, IntArrayList glFormats, IntArrayList glTargets) {
        static StorageSplitOut slotsOnly(IntArrayList glSlots) {
            return new StorageSplitOut(glSlots, null, null, null);
        }

        void add(long compiler, SpvcReflectedResource res, int oldBinding) {
            glSlots.add(oldBinding);
            if (names == null) return;
            names.add(res.nameString());
            final long imageType = Spvc.spvc_compiler_get_type_handle(compiler, res.base_type_id());
            int glFormat = SpvImageFormatMap.glInternalFormat(Spvc.spvc_type_get_image_storage_format(imageType));
            if (glFormat == 0) {
                final long sampled = Spvc.spvc_compiler_get_type_handle(compiler, Spvc.spvc_type_get_image_sampled_type(imageType));
                glFormat = SpvImageFormatMap.glFormatForSampledType(Spvc.spvc_type_get_basetype(sampled));
            }
            glFormats.add(glFormat);
            glTargets.add(SpvImageFormatMap.glTextureTarget(
                Spvc.spvc_type_get_image_dimension(imageType), Spvc.spvc_type_get_image_arrayed(imageType)));
        }
    }

    private static int[] collectComputeStorageSplit(long resources, long compiler, int resourceType, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, StorageSplitOut ro, StorageSplitOut rw, int set0Binding, int set1Binding, MemoryStack stack, ReadOnlyClassifier classifier) {
        final int[] split = { set0Binding, set1Binding };
        forEachResource(resources, resourceType, stack, (j, res) -> {
            final int spvId = res.id();
            final int oldBinding = Spvc.spvc_compiler_get_decoration(compiler, spvId, Spv.SpvDecorationBinding);
            if (classifier.isReadOnly(compiler, spvId, stack)) {
                idToNewSet.put(spvId, 0);
                idToNewBinding.put(spvId, split[0]++);
                ro.add(compiler, res, oldBinding);
            } else {
                idToNewSet.put(spvId, 1);
                idToNewBinding.put(spvId, split[1]++);
                rw.add(compiler, res, oldBinding);
            }
        });
        return split;
    }

    private static int collectResourceIds(long resources, long compiler, int resourceType, Int2IntOpenHashMap idToNewSet, Int2IntOpenHashMap idToNewBinding, int targetSet, int nextBinding, MemoryStack stack) {
        final int[] next = { nextBinding };
        forEachResource(resources, resourceType, stack, (j, res) -> {
            final int spvId = res.id();
            idToNewSet.put(spvId, targetSet);
            idToNewBinding.put(spvId, next[0]++);
        });
        return next[0];
    }

    private static ByteBuffer copyBuffer(ByteBuffer src) {
        final ByteBuffer copy = memAlloc(src.remaining());
        copy.put(src.duplicate());
        copy.flip();
        return copy;
    }

    public void shutdown() {
        for (ProgramObject prog : programObjects.values()) {
            releaseProgramGpuResources(prog);
        }
        programObjects.clear();
        for (ShaderObject obj : shaderObjects.values()) {
            if (obj.spirv != null) memFree(obj.spirv);
        }
        shaderObjects.clear();
    }

    public static final class ShaderObject {
        public final int type; // GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
        public String source = "";
        public ByteBuffer spirv;
        public boolean compiled;
        public String infoLog = "";
        public boolean deletePending;
        public int attachCount;
        public CompletableFuture<SpirvCompiler.Result> spirvFuture;
        public StageReflection reflection = StageReflection.EMPTY;
        public GraphicsBindingMap graphicsBindingMap = GraphicsBindingMap.EMPTY;
        public Set<String> boolUniforms = Set.of();

        public ShaderObject(int type) {
            this.type = type;
        }

        public boolean isVertex() {
            return type == GL20.GL_VERTEX_SHADER;
        }

        public boolean isFragment() {
            return type == GL20.GL_FRAGMENT_SHADER;
        }

        public boolean isGeometry() {
            return type == GL32.GL_GEOMETRY_SHADER;
        }

        public boolean isCompute() {
            return type == GL43.GL_COMPUTE_SHADER;
        }
    }

    public record UniformMemberInfo(
        int offset, int size, int arrayStride, boolean isVertex,
        int vectorSize, int columns, int baseType, int arrayLen
    ) {}

    public record UboMember(
        String name, int offset, int size, int arrayStride,
        int vectorSize, int columns, int baseType, int arrayLen
    ) {}

    public record VsInput(String name, int binaryOffset, int originalLocation, int vecSize, int baseType) {}

    public record VsOutput(String name, int originalLocation) {}

    public record FsInput(String name, int binaryOffset, int originalLocation) {}

    public record StageReflection(
        ResourceCounts counts,
        List<String> samplerNames,      // SAMPLED_IMAGE, sorted by binding (post-remap)
        List<String> extraUniformNames, // SEPARATE_SAMPLERS - registered in nameToLocation only
        List<String> storageImageNames, // SEPARATE_IMAGE + STORAGE_IMAGE (RO + RW), sorted by binding (post-remap)
        int uboSize,                    // binding-0 UBO total size in bytes; 0 if absent
        List<UboMember> uboMembers,     // members of the binding-0 UBO
        List<VsInput> vsInputs,         // VS only (FS leaves empty); binary offset for in-place patch
        List<VsOutput> vsOutputs,       // VS only (FS leaves empty); for varying matching
        List<FsInput> fsInputs,         // FS only (VS leaves empty); for varying matching
        int maxOutputLocation,          // FS only; highest fragment output location, -1 if none
        int numReadonlyStorageBuffers,
        int numReadwriteStorageBuffers,
        int numReadonlyStorageTextures,
        int numReadwriteStorageTextures,
        BlockReflection[] blocks
    ) {
        public static final StageReflection EMPTY = new StageReflection(
            ResourceCounts.EMPTY, List.of(), List.of(), List.of(), 0, List.of(), List.of(), List.of(), List.of(),
            -1, 0, 0, 0, 0, BlockReflection.emptyBlocks());
    }

   public record BlockReflection(int size, int binding, List<UboMember> members, boolean readOnly) {
        public static final BlockReflection EMPTY = new BlockReflection(0, -1, List.of(), false);

        public static BlockReflection[] emptyBlocks() {
            return new BlockReflection[] { EMPTY, EMPTY };
        }
    }

    public record ComputeBindingMap(
        int[] samplerGlSlots,
        int[] roStorageTextureGlSlots,
        int[] rwStorageTextureGlSlots,
        int[] roSsboGlSlots,
        int[] rwSsboGlSlots,
        int[] uboGlSlots,
        String[] samplerNames,
        String[] roStorageTextureNames,
        String[] rwStorageTextureNames,
        boolean[] uboIsDefaultBlock,
        int[] uboSizes,
        int[] roStorageTextureFormats,
        int[] rwStorageTextureFormats,
        int[] roStorageTextureTargets,
        int[] rwStorageTextureTargets
    ) {
        public static final ComputeBindingMap EMPTY = new ComputeBindingMap(
            new int[0], new int[0], new int[0], new int[0], new int[0], new int[0],
            new String[0], new String[0], new String[0],
            new boolean[0], new int[0],
            new int[0], new int[0], new int[0], new int[0]);
    }

    public record GraphicsBindingMap(int[] roStorageTextureGlSlots, int[] rwStorageTextureGlSlots, int[] roSsboGlSlots) {
        public static final GraphicsBindingMap EMPTY = new GraphicsBindingMap(new int[0], new int[0], new int[0]);
    }

    public static StageReflection reflectStage(ByteBuffer spirv, boolean isVertex) {
        if (spirv == null) return StageReflection.EMPTY;
        return withSpvc(spirv, StageReflection.EMPTY,
            (compiler, resources, stack) -> reflectStageImpl(compiler, resources, stack, isVertex));
    }

    private static StageReflection reflectStageImpl(long compiler, long resources, MemoryStack stack, boolean isVertex) {
        final int numUBOs = countResourceTypeStatic(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, stack);
        final int[] roRwBuf = CrossCompileUtil.countStorageBuffersSplit(resources, compiler, stack);
        final int[] roRwImg = CrossCompileUtil.countStorageImagesSplit(resources, compiler, stack);
        final int numStorageBuffers = roRwBuf[0] + roRwBuf[1];
        final int numStorageTextures = roRwImg[0] + roRwImg[1];

        final IntArrayList samplerBindings = new IntArrayList();
        final List<String> samplerNamesRaw = new ArrayList<>();
        collectSamplerEntries(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE,  samplerNamesRaw, samplerBindings, stack);
        final int sCount = samplerBindings.size();
        final int[] perm = new int[sCount];
        for (int i = 0; i < sCount; i++) perm[i] = i;
        IntArrays.quickSort(perm, (a, b) -> Integer.compare(samplerBindings.getInt(a), samplerBindings.getInt(b)));
        final List<String> samplerNames = new ArrayList<>(sCount);
        for (int p : perm) samplerNames.add(samplerNamesRaw.get(p));

        final List<String> extraNames = new ArrayList<>();
        collectResourceNames(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_SAMPLERS, extraNames, stack);

        final IntArrayList storageImageBindings = new IntArrayList();
        final List<String> storageImageNamesRaw = new ArrayList<>();
        collectSamplerEntries(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_SEPARATE_IMAGE, storageImageNamesRaw, storageImageBindings, stack);
        collectSamplerEntries(resources, compiler, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE, storageImageNamesRaw, storageImageBindings, stack);
        final int siCount = storageImageBindings.size();
        final int[] siPerm = new int[siCount];
        for (int i = 0; i < siCount; i++) siPerm[i] = i;
        IntArrays.quickSort(siPerm, (a, b) -> Integer.compare(storageImageBindings.getInt(a), storageImageBindings.getInt(b)));
        final List<String> storageImageNames = new ArrayList<>(siCount);
        for (int p : siPerm) storageImageNames.add(storageImageNamesRaw.get(p));

        int uboSize = 0;
        final List<UboMember> uboMembers = new ArrayList<>();
        final IntBuffer pOffset = stack.ints(0);
        final IntBuffer pStride = stack.ints(0);
        final PointerBuffer pSize = stack.pointers(0);
        final PointerBuffer pUboList = stack.pointers(0);
        final PointerBuffer pUboCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, pUboList, pUboCount) == Spvc.SPVC_SUCCESS) {
            final int uboCount = (int) pUboCount.get(0);
            if (uboCount > 0) {
                final SpvcReflectedResource.Buffer ubos = SpvcReflectedResource.create(pUboList.get(0), uboCount);
                for (int i = 0; i < uboCount; i++) {
                    final SpvcReflectedResource ubo = ubos.get(i);
                    final int uboBinding = Spvc.spvc_compiler_get_decoration(compiler, ubo.id(), Spv.SpvDecorationBinding);
                    if (uboBinding != 0) continue;
                    final long baseTypeHandle = Spvc.spvc_compiler_get_type_handle(compiler, ubo.base_type_id());
                    if (Spvc.spvc_compiler_get_declared_struct_size(compiler, baseTypeHandle, pSize) == Spvc.SPVC_SUCCESS) {
                        uboSize = (int) pSize.get(0);
                    }
                    final int memberCount = Spvc.spvc_type_get_num_member_types(Spvc.spvc_compiler_get_type_handle(compiler, ubo.type_id()));
                    for (int m = 0; m < memberCount; m++) {
                        final String memberName = Spvc.spvc_compiler_get_member_name(compiler, ubo.base_type_id(), m);
                        if (memberName == null || memberName.isEmpty()) continue;
                        int memberOffset = 0;
                        if (Spvc.spvc_compiler_type_struct_member_offset(compiler, baseTypeHandle, m, pOffset) == Spvc.SPVC_SUCCESS) {
                            memberOffset = pOffset.get(0);
                        }
                        int memberSize = 0;
                        if (Spvc.spvc_compiler_get_declared_struct_member_size(compiler, baseTypeHandle, m, pSize) == Spvc.SPVC_SUCCESS) {
                            memberSize = (int) pSize.get(0);
                        }
                        int memberArrayStride = 0;
                        if (Spvc.spvc_compiler_type_struct_member_array_stride(compiler, baseTypeHandle, m, pStride) == Spvc.SPVC_SUCCESS) {
                            memberArrayStride = pStride.get(0);
                        }
                        final int memberTypeId = Spvc.spvc_type_get_member_type(baseTypeHandle, m);
                        final long memberTypeHandle = Spvc.spvc_compiler_get_type_handle(compiler, memberTypeId);
                        final int memberVectorSize = Spvc.spvc_type_get_vector_size(memberTypeHandle);
                        final int memberColumns = Spvc.spvc_type_get_columns(memberTypeHandle);
                        final int memberBaseType = Spvc.spvc_type_get_basetype(memberTypeHandle);
                        final int memberArrayLen = (memberArrayStride > 0) ? Math.max(1, memberSize / memberArrayStride) : 1;
                        uboMembers.add(new UboMember(memberName, memberOffset, memberSize, memberArrayStride, memberVectorSize, memberColumns, memberBaseType, memberArrayLen));
                    }
                }
            }
        }

        final BlockReflection[] blocks = BlockReflection.emptyBlocks();
        final PointerBuffer pSsboList = stack.pointers(0);
        final PointerBuffer pSsboCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, pSsboList, pSsboCount) == Spvc.SPVC_SUCCESS) {
            final int ssboCount = (int) pSsboCount.get(0);
            if (ssboCount > 0) {
                final SpvcReflectedResource.Buffer ssbos = SpvcReflectedResource.create(pSsboList.get(0), ssboCount);
                for (int i = 0; i < ssboCount; i++) {
                    final SpvcReflectedResource ssbo = ssbos.get(i);
                    final String blockName = Spvc.spvc_compiler_get_name(compiler, ssbo.base_type_id());
                    final int b = blockIndexForName(blockName);
                    if (b < 0) continue;
                    int blockSize = 0;
                    final long baseTypeHandle = Spvc.spvc_compiler_get_type_handle(compiler, ssbo.base_type_id());
                    if (Spvc.spvc_compiler_get_declared_struct_size(compiler, baseTypeHandle, pSize) == Spvc.SPVC_SUCCESS) {
                        blockSize = (int) pSize.get(0);
                    }
                    final int blockBinding = Spvc.spvc_compiler_get_decoration(compiler, ssbo.id(), Spv.SpvDecorationBinding);
                    final boolean readOnly = CrossCompileUtil.isStorageBufferReadOnly(compiler, ssbo.id(), stack);
                    final List<UboMember> blockMembers = new ArrayList<>();
                    collectBlockMembers(compiler, ssbo, baseTypeHandle, pOffset, pStride, pSize, blockMembers);
                    blocks[b] = new BlockReflection(blockSize, blockBinding, blockMembers, readOnly);
                }
            }
        }

        final List<VsInput> vsInputs = new ArrayList<>();
        final List<VsOutput> vsOutputs = new ArrayList<>();
        final List<FsInput> fsInputs = new ArrayList<>();
        int maxOutputLocation = -1;
        final IntBuffer pBinaryOffset = stack.ints(0);

        final PointerBuffer pInList = stack.pointers(0);
        final PointerBuffer pInCount = stack.pointers(0);
        if (isVertex) {
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT, pInList, pInCount) == Spvc.SPVC_SUCCESS) {
                final int n = (int) pInCount.get(0);
                if (n > 0) {
                    final SpvcReflectedResource.Buffer items = SpvcReflectedResource.create(pInList.get(0), n);
                    for (int i = 0; i < n; i++) {
                        final SpvcReflectedResource it = items.get(i);
                        final String name = it.nameString();
                        if (name == null || name.isEmpty()) continue;
                        final int loc = Spvc.spvc_compiler_get_decoration(compiler, it.id(), Spv.SpvDecorationLocation);
                        final long typeHandle = Spvc.spvc_compiler_get_type_handle(compiler, it.type_id());
                        final int vecSize = Spvc.spvc_type_get_vector_size(typeHandle);
                        final int baseType = Spvc.spvc_type_get_basetype(typeHandle);
                        if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, it.id(), Spv.SpvDecorationLocation, pBinaryOffset)) continue;
                        vsInputs.add(new VsInput(name, pBinaryOffset.get(0), loc, vecSize, baseType));
                    }
                }
            }
            final PointerBuffer pOutList = stack.pointers(0);
            final PointerBuffer pOutCount = stack.pointers(0);
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STAGE_OUTPUT, pOutList, pOutCount) == Spvc.SPVC_SUCCESS) {
                final int n = (int) pOutCount.get(0);
                if (n > 0) {
                    final SpvcReflectedResource.Buffer items = SpvcReflectedResource.create(pOutList.get(0), n);
                    for (int i = 0; i < n; i++) {
                        final SpvcReflectedResource it = items.get(i);
                        final String name = it.nameString();
                        if (name == null || name.isEmpty()) continue;
                        final int loc = Spvc.spvc_compiler_get_decoration(compiler, it.id(), Spv.SpvDecorationLocation);
                        vsOutputs.add(new VsOutput(name, loc));
                    }
                }
            }
        } else {
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STAGE_INPUT, pInList, pInCount) == Spvc.SPVC_SUCCESS) {
                final int n = (int) pInCount.get(0);
                if (n > 0) {
                    final SpvcReflectedResource.Buffer items = SpvcReflectedResource.create(pInList.get(0), n);
                    for (int i = 0; i < n; i++) {
                        final SpvcReflectedResource it = items.get(i);
                        final String name = it.nameString();
                        if (name == null || name.isEmpty()) continue;
                        final int loc = Spvc.spvc_compiler_get_decoration(compiler, it.id(), Spv.SpvDecorationLocation);
                        if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, it.id(), Spv.SpvDecorationLocation, pBinaryOffset)) continue;
                        fsInputs.add(new FsInput(name, pBinaryOffset.get(0), loc));
                    }
                }
            }
            final PointerBuffer pFsOutList = stack.pointers(0);
            final PointerBuffer pFsOutCount = stack.pointers(0);
            if (Spvc.spvc_resources_get_resource_list_for_type(resources, Spvc.SPVC_RESOURCE_TYPE_STAGE_OUTPUT, pFsOutList, pFsOutCount) == Spvc.SPVC_SUCCESS) {
                final int n = (int) pFsOutCount.get(0);
                if (n > 0) {
                    final SpvcReflectedResource.Buffer items = SpvcReflectedResource.create(pFsOutList.get(0), n);
                    for (int i = 0; i < n; i++) {
                        maxOutputLocation = Math.max(maxOutputLocation,
                            Spvc.spvc_compiler_get_decoration(compiler, items.get(i).id(), Spv.SpvDecorationLocation));
                    }
                }
            }
        }

        return new StageReflection(
            new ResourceCounts(samplerNames.size(), numUBOs, numStorageBuffers, numStorageTextures),
            samplerNames,
            extraNames,
            storageImageNames,
            uboSize,
            uboMembers,
            vsInputs,
            vsOutputs,
            fsInputs,
            maxOutputLocation,
            roRwBuf[0], roRwBuf[1],
            roRwImg[0], roRwImg[1],
            blocks);
    }

    public static final String PER_FRAME_BLOCK_NAME = "AngelicaPerFrame";

    public static final int PER_FRAME_BLOCK_BINDING = 15;

    public static final String PER_PASS_BLOCK_NAME = "AngelicaPerPass";

    public static final int PER_PASS_BLOCK_BINDING = 14;

    public static final int BLOCK_PER_FRAME = 0;
    public static final int BLOCK_PER_PASS = 1;
    public static final int BLOCK_COUNT = 2;
    public static final String[] BLOCK_NAMES = { PER_FRAME_BLOCK_NAME, PER_PASS_BLOCK_NAME };
    public static final int[] BLOCK_BINDINGS = { PER_FRAME_BLOCK_BINDING, PER_PASS_BLOCK_BINDING };

    public static int blockIndexForName(String name) {
        if (PER_FRAME_BLOCK_NAME.equals(name)) return BLOCK_PER_FRAME;
        if (PER_PASS_BLOCK_NAME.equals(name)) return BLOCK_PER_PASS;
        return -1;
    }

    private static void collectBlockMembers(long compiler, SpvcReflectedResource block, long baseTypeHandle, IntBuffer pOffset, IntBuffer pStride, PointerBuffer pSize, List<UboMember> out) {
        final int memberCount = Spvc.spvc_type_get_num_member_types(Spvc.spvc_compiler_get_type_handle(compiler, block.type_id()));
        for (int m = 0; m < memberCount; m++) {
            final String memberName = Spvc.spvc_compiler_get_member_name(compiler, block.base_type_id(), m);
            if (memberName == null || memberName.isEmpty()) continue;
            int memberOffset = 0;
            if (Spvc.spvc_compiler_type_struct_member_offset(compiler, baseTypeHandle, m, pOffset) == Spvc.SPVC_SUCCESS) {
                memberOffset = pOffset.get(0);
            }
            int memberSize = 0;
            if (Spvc.spvc_compiler_get_declared_struct_member_size(compiler, baseTypeHandle, m, pSize) == Spvc.SPVC_SUCCESS) {
                memberSize = (int) pSize.get(0);
            }
            int memberArrayStride = 0;
            if (Spvc.spvc_compiler_type_struct_member_array_stride(compiler, baseTypeHandle, m, pStride) == Spvc.SPVC_SUCCESS) {
                memberArrayStride = pStride.get(0);
            }
            final int memberTypeId = Spvc.spvc_type_get_member_type(baseTypeHandle, m);
            final long memberTypeHandle = Spvc.spvc_compiler_get_type_handle(compiler, memberTypeId);
            out.add(new UboMember(memberName, memberOffset, memberSize, memberArrayStride,
                Spvc.spvc_type_get_vector_size(memberTypeHandle),
                Spvc.spvc_type_get_columns(memberTypeHandle),
                Spvc.spvc_type_get_basetype(memberTypeHandle),
                (memberArrayStride > 0) ? Math.max(1, memberSize / memberArrayStride) : 1));
        }
    }

    private static int countResourceTypeStatic(long resources, int resourceType, MemoryStack stack) {
        final PointerBuffer pList = stack.pointers(0);
        final PointerBuffer pCount = stack.pointers(0);
        if (Spvc.spvc_resources_get_resource_list_for_type(resources, resourceType, pList, pCount) != Spvc.SPVC_SUCCESS) {
            return 0;
        }
        return (int) pCount.get(0);
    }

    private static void collectSamplerEntries(long resources, long compiler, int resourceType, List<String> namesOut, IntArrayList bindingsOut, MemoryStack stack) {
        forEachResource(resources, resourceType, stack, (i, r) -> {
            final String name = Spvc.spvc_compiler_get_name(compiler, r.id());
            final int binding = Spvc.spvc_compiler_get_decoration(compiler, r.id(), Spv.SpvDecorationBinding);
            namesOut.add(name != null ? name : ("sampler_" + i));
            bindingsOut.add(binding);
        });
    }

    private static void collectResourceNames(long resources, long compiler, int resourceType, List<String> out, MemoryStack stack) {
        forEachResource(resources, resourceType, stack, (i, r) -> {
            final String name = r.nameString();
            if (name != null && !name.isEmpty()) out.add(name);
        });
    }

    public static UniformMemberInfo getMemberInfo(ProgramObject prog, int location, boolean vertex) {
        if (vertex) {
            final UniformMemberInfo vi = prog.vertexMemberInfo.get(location);
            if (vi != null) return vi;
        } else {
            final UniformMemberInfo fi = prog.fragmentMemberInfo.get(location);
            if (fi != null) return fi;
        }
        final UniformMemberInfo info = prog.locationToMemberInfo.get(location);
        return (info != null && info.isVertex() == vertex) ? info : null;
    }

    public static final class VertexVariant {
        public ByteBuffer spirv;
        public long sdlShader;
        public int inputMask;
        public final int[] inputVecSize = new int[16];
        public final int[] inputBaseType = new int[16];
    }

    public static final class ProgramObject {
        public ByteBuffer computeSpirv;
        public ByteBuffer fragmentSpirv;
        public ByteBuffer geometrySpirv;
        public ByteBuffer vertexSpirv;
        public ComputeBindingMap computeBindingMap = ComputeBindingMap.EMPTY;
        public GraphicsBindingMap vertexGraphicsBindingMap = GraphicsBindingMap.EMPTY;
        public GraphicsBindingMap fragmentGraphicsBindingMap = GraphicsBindingMap.EMPTY;
        public List<String> fragmentSamplerNames = List.of();
        public List<String> vertexSamplerNames = List.of();
        public List<String> fragmentImageNames = List.of();
        public List<String> vertexImageNames = List.of();
        public final HashSet<String> allImageNames = new HashSet<>();
        public ResourceCounts computeResources = ResourceCounts.EMPTY;
        public ResourceCounts fragmentResources = ResourceCounts.EMPTY;
        public ResourceCounts vertexResources = ResourceCounts.EMPTY;
        public String infoLog = "";
        public int externalUboBinding = -1;
        public boolean deletePending;
        public boolean linked;
        public final Int2ObjectOpenHashMap<String> locationToName = new Int2ObjectOpenHashMap<>();
        public final Int2ObjectOpenHashMap<UniformMemberInfo> fragmentMemberInfo = new Int2ObjectOpenHashMap<>();
        public final Int2ObjectOpenHashMap<UniformMemberInfo> locationToMemberInfo = new Int2ObjectOpenHashMap<>();
        public final Int2ObjectOpenHashMap<UniformMemberInfo> vertexMemberInfo = new Int2ObjectOpenHashMap<>();

        @SuppressWarnings("unchecked")
        public final Int2ObjectOpenHashMap<UniformMemberInfo>[] blockMemberInfo = new Int2ObjectOpenHashMap[] { new Int2ObjectOpenHashMap<>(), new Int2ObjectOpenHashMap<>() };
        public final UniformMemberInfo[][] blockInfoBySlot = { EMPTY_INFO, EMPTY_INFO };
        public final int[] blockSize = new int[BLOCK_COUNT];
        public final int[] blockBinding = { -1, -1 };

        public UniformMemberInfo anyBlockMemberInfo(int loc) {
            final UniformMemberInfo m = blockMemberInfo[BLOCK_PER_FRAME].get(loc);
            return m != null ? m : blockMemberInfo[BLOCK_PER_PASS].get(loc);
        }

        private static final UniformMemberInfo[] EMPTY_INFO = new UniformMemberInfo[0];

        public UniformMemberInfo[] vsInfoBySlot;
        public UniformMemberInfo[] fsInfoBySlot;
        public int uniformSlotCount;
        public final int[] vertexInputBaseType = new int[16];
        public final int[] vertexInputVecSize = new int[16];
        public final String[] vertexInputName = new String[16];
        public String vertexSource = "";
        public int maxFragOutputLocation = -1;
        public StageReflection vertexReflection = StageReflection.EMPTY;
        public final Long2ObjectOpenHashMap<VertexVariant> vertexVariants = new Long2ObjectOpenHashMap<>();
        public final LongOpenHashSet vertexVariantFailed = new LongOpenHashSet();
        public final Object2IntOpenHashMap<String> attribLocationBindings = newLocMap();
        public final Object2IntOpenHashMap<String> resolvedAttribLocations = newLocMap();
        public final Object2IntOpenHashMap<String> nameToLocation = newLocMap();
        public final Object2IntOpenHashMap<String> samplerTextureUnits = newLocMap();
        public final Object2IntOpenHashMap<String> imageTextureUnits = newLocMap();

        public int[] vertexSamplerUnits = EMPTY_UNITS;
        public int[] fragmentSamplerUnits = EMPTY_UNITS;
        public boolean samplerUnitsDirty = true;

        private static final int[] EMPTY_UNITS = new int[0];

        private static Object2IntOpenHashMap<String> newLocMap() {
            final Object2IntOpenHashMap<String> m = new Object2IntOpenHashMap<>();
            m.defaultReturnValue(-1);
            return m;
        }
        public final HashSet<String> allSamplerNames = new HashSet<>();
        public final HashSet<String> boolUniforms = new HashSet<>();
        public List<String> activeUniformNames;
        public List<String> activeAttribNames;

        public List<String> getActiveUniformNames() {
            if (activeUniformNames == null) {
                activeUniformNames = new ArrayList<>(nameToLocation.keySet());
                Collections.sort(activeUniformNames);
            }
            return activeUniformNames;
        }
        public List<String> getActiveAttribNames() {
            if (activeAttribNames == null) {
                activeAttribNames = new ArrayList<>(linked ? resolvedAttribLocations.keySet() : attribLocationBindings.keySet());
                Collections.sort(activeAttribNames);
            }
            return activeAttribNames;
        }

        public int getAttribGlType(String name) {
            final int loc = resolvedAttribLocations.getInt(name);
            if (loc < 0 || loc >= 16) return GL20.GL_FLOAT_VEC4;
            final int vec = Math.max(1, vertexInputVecSize[loc]);
            final int base = vertexInputBaseType[loc];
            if (base == Spvc.SPVC_BASETYPE_INT32) {
                return switch (vec) {
                    case 1 -> GL11.GL_INT;
                    case 2 -> GL20.GL_INT_VEC2;
                    case 3 -> GL20.GL_INT_VEC3;
                    default -> GL20.GL_INT_VEC4;
                };
            }
            if (base == Spvc.SPVC_BASETYPE_UINT32) {
                return switch (vec) {
                    case 1 -> GL11.GL_UNSIGNED_INT;
                    case 2 -> GL30.GL_UNSIGNED_INT_VEC2;
                    case 3 -> GL30.GL_UNSIGNED_INT_VEC3;
                    default -> GL30.GL_UNSIGNED_INT_VEC4;
                };
            }
            return switch (vec) {
                case 1 -> GL11.GL_FLOAT;
                case 2 -> GL20.GL_FLOAT_VEC2;
                case 3 -> GL20.GL_FLOAT_VEC3;
                default -> GL20.GL_FLOAT_VEC4;
            };
        }
        public int computeShader;
        public int fragmentShader;
        public int fragmentUboSize;
        public int geometryShader;
        public int nextUniformLocation = 0;
        public int vertexInputMask;
        public int vertexShader;
        public int vertexUboSize;
        public long sdlComputePipeline;
        public long sdlFragmentShader;
        public long sdlVertexShader;
        public long lastComputeCb;
        public long lastComputeFrame;
        public long[] lastComputeUboHash;

        public void buildUniformSlotArrays() {
            final int n = nextUniformLocation;
            uniformSlotCount = n;
            vsInfoBySlot = new UniformMemberInfo[n];
            fsInfoBySlot = new UniformMemberInfo[n];
            for (int b = 0; b < BLOCK_COUNT; b++) {
                blockInfoBySlot[b] = new UniformMemberInfo[n];
                for (var it = blockMemberInfo[b].int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                    final var e = it.next();
                    final int k = e.getIntKey();
                    if (k >= 0 && k < n) blockInfoBySlot[b][k] = e.getValue();
                }
            }
            for (var it = vertexMemberInfo.int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                final var e = it.next();
                final int k = e.getIntKey();
                if (k >= 0 && k < n) vsInfoBySlot[k] = e.getValue();
            }
            for (var it = fragmentMemberInfo.int2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
                final var e = it.next();
                final int k = e.getIntKey();
                if (k >= 0 && k < n) fsInfoBySlot[k] = e.getValue();
            }
        }
    }
}
