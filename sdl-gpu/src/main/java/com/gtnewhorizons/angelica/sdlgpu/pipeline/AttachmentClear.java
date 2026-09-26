package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.GlslTransformUtils;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL20;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineTargetInfo;
import org.lwjgl.sdl.SDL_GPUMultisampleState;
import org.lwjgl.sdl.SDL_GPURasterizerState;
import org.lwjgl.sdl.SDL_GPUVertexInputState;
import org.lwjgl.sdl.SDL_GPUViewport;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;
import org.taumc.glsl.Transformer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class AttachmentClear {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final String VERTEX_SOURCE = "angelica:sdlgpu/clear_attachment.vsh";
    private static final String FRAGMENT_SOURCE = "angelica:sdlgpu/clear_attachment.fsh";
    private static final long KEY_SEED = 0x5B1D2A7C4E9F3061L;
    private static final int CACHE_SLOTS = 4;
    private static final int MAX_COLOR_TARGETS = ContextState.MAX_COLOR_ATTACHMENTS;

    public static final int EMPTY = 0;
    public static final int PARTIAL = 1;
    public static final int FULL = 2;

    public static final class Target {
        public int[] colorFormats;
        public int[] drawBuffers;
        public int depthFormat;
        public long depthTexture;
        public int width;
        public int height;
        public boolean flipY;
    }

    private final FrameManager frameManager;
    private final PipelineStore pipelineStore;
    private final ShaderManager shaderManager;

    private final Target fboTarget = new Target();
    private final Target fbo0Target = new Target();

    private int vertexShaderId;
    private long sdlVertexShader;
    private final long[] sdlFragmentShaders = new long[MAX_COLOR_TARGETS + 1];
    private final boolean[] fragmentFailed = new boolean[MAX_COLOR_TARGETS + 1];

    private final long[] cacheKeys = new long[CACHE_SLOTS];
    private final long[] cachePipelines = new long[CACHE_SLOTS];
    private int cacheNext;

    private final int[] scissorRect = new int[4];

    private final Builder builder = new Builder();
    private Target buildTarget;
    private int buildColorTargets;
    private boolean buildDepthFlag;
    private boolean buildStencilFlag;
    private int buildStencilMask;
    private int buildColorMask;

    public AttachmentClear(FrameManager frameManager, PipelineStore pipelineStore, ShaderManager shaderManager) {
        this.frameManager = frameManager;
        this.pipelineStore = pipelineStore;
        this.shaderManager = shaderManager;
    }

    private final class Builder implements LongSupplier {
        @Override public long getAsLong() { return createPipeline(); }
    }

    public Target forFbo(FboState fbo) {
        final Target t = fboTarget;
        t.colorFormats = fbo.cachedColorFormats;
        t.drawBuffers = fbo.drawBuffers;
        t.depthFormat = fbo.depthTexture != 0 ? fbo.depthFormat : 0;
        t.depthTexture = fbo.depthTexture;
        t.width = fbo.width;
        t.height = fbo.height;
        t.flipY = false;
        return t;
    }

    public Target forFbo0(int[] swapchainFormats, int w, int h) {
        final Target t = fbo0Target;
        t.colorFormats = swapchainFormats;
        t.drawBuffers = null;
        t.depthFormat = 0;
        t.depthTexture = 0;
        t.width = w;
        t.height = h;
        t.flipY = true;
        return t;
    }

    static boolean enabled(Target t, int i) {
        return t.drawBuffers == null || t.drawBuffers[i] >= 0;
    }

    public boolean hasIntegerColorTarget(Target t) {
        final int[] formats = t.colorFormats;
        if (formats == null) return false;
        for (int i = 0; i < formats.length; i++) {
            if (enabled(t, i) && PixelOps.isSdlFormatInteger(formats[i])) return true;
        }
        return false;
    }

    public int coverage(ContextState st, Target t) {
        final int sx, sy, sw, sh;
        if (st.scissorEnabled) {
            sx = st.scissorX;
            sy = t.flipY ? t.height - st.scissorY - st.scissorH : st.scissorY;
            sw = st.scissorW;
            sh = st.scissorH;
        } else {
            sx = 0;
            sy = 0;
            sw = t.width;
            sh = t.height;
        }
        if (!ScissorClamp.clamp(sx, sy, sw, sh, t.width, t.height, scissorRect)) return EMPTY;
        if (scissorRect[ScissorClamp.X] == 0 && scissorRect[ScissorClamp.Y] == 0
            && scissorRect[ScissorClamp.W] == t.width && scissorRect[ScissorClamp.H] == t.height) return FULL;
        return PARTIAL;
    }

    public boolean clearInPass(ContextState st, FrameState f, Target t, boolean color, boolean depth, boolean stencil) {
        final int colorTargets = color ? t.colorFormats.length : 0;
        final int stencilMask = stencil ? (st.pipeline.stencilWriteMask & 0xFF) : 0;
        final int colorMask = color ? st.pipeline.colorWriteMask : 0;
        final long pipeline = getOrCreatePipeline(t, colorTargets, depth, stencil, stencilMask, colorMask);
        if (pipeline == 0) return false;
        if (coverage(st, t) == EMPTY) return false;

        final long rp = f.renderPass;
        SDL_BindGPUGraphicsPipeline(rp, pipeline);

        final float clearDepth = Math.min(1.0f, Math.max(0.0f, st.depthClearValue));
        final long vpAddr = st.cachedViewport.address();
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.X, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.Y, 0f);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.W, t.width);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.H, t.height);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MIN_DEPTH, clearDepth);
        MemoryAccess.putFloat(vpAddr + SDL_GPUViewport.MAX_DEPTH, clearDepth);
        SDL_SetGPUViewport(rp, st.cachedViewport);

        final long scAddr = st.cachedScissor.address();
        MemoryAccess.putInt(scAddr + SDL_Rect.X, scissorRect[ScissorClamp.X]);
        MemoryAccess.putInt(scAddr + SDL_Rect.Y, scissorRect[ScissorClamp.Y]);
        MemoryAccess.putInt(scAddr + SDL_Rect.W, scissorRect[ScissorClamp.W]);
        MemoryAccess.putInt(scAddr + SDL_Rect.H, scissorRect[ScissorClamp.H]);
        SDL_SetGPUScissor(rp, st.cachedScissor);

        if (stencil) {
            SDL_SetGPUStencilReference(rp, (byte) st.stencilClearValue);
        }

        if (colorTargets > 0) {
            try (MemoryStack stack = stackPush()) {
                final ByteBuffer clearColor = stack.malloc(16);
                clearColor.putFloat(0, st.clearR);
                clearColor.putFloat(4, st.clearG);
                clearColor.putFloat(8, st.clearB);
                clearColor.putFloat(12, st.clearA);
                SDL_PushGPUFragmentUniformData(frameManager.getCommandBuffer(f), 0, clearColor);
            }
            st.lastPushedProgramFs = -1;
        }

        SDL_DrawGPUPrimitives(rp, 3, 1, 0, 0);

        st.lastBoundPipeline = 0;
        st.viewportDirty = true;
        st.scissorDirty = true;
        st.lastAppliedStencilRef = Integer.MIN_VALUE;
        st.lastAppliedVboBindCb = 0;

        if (t.depthTexture != 0) {
            if (depth) st.clearedTexturesThisFrame.add(t.depthTexture);
            if (stencil) st.clearedStencilTexturesThisFrame.add(t.depthTexture);
        }

        frameManager.noteInPassClear();
        return true;
    }

    long getOrCreatePipeline(Target t, int colorTargets, boolean depth, boolean stencil, int stencilMask, int colorMask) {
        if (!ensureShaders(colorTargets)) return 0;

        final int[] formats = t.colorFormats;
        final int count = formats != null ? formats.length : 0;
        long key = KEY_SEED;
        key = Hashing.fmix64(key, count);
        for (int i = 0; i < count; i++) {
            key = Hashing.fmix64(key, formats[i]);
            key = Hashing.fmix64(key, enabled(t, i) ? 1 : 0);
        }
        key = Hashing.fmix64(key, t.depthFormat);
        key = Hashing.fmix64(key, colorTargets);
        key = Hashing.fmix64(key, (depth ? 1 : 0) | ((stencil ? 1 : 0) << 1) | (stencilMask << 8) | (colorMask << 16));

        for (int i = 0; i < CACHE_SLOTS; i++) {
            if (cacheKeys[i] == key && cachePipelines[i] != 0) return cachePipelines[i];
        }

        buildTarget = t;
        buildColorTargets = colorTargets;
        buildDepthFlag = depth;
        buildStencilFlag = stencil;
        buildStencilMask = stencilMask;
        buildColorMask = colorMask;
        final long pipeline = pipelineStore.getOrBuild(key, builder);
        if (pipeline != 0) {
            cacheKeys[cacheNext] = key;
            cachePipelines[cacheNext] = pipeline;
            cacheNext = (cacheNext + 1) & (CACHE_SLOTS - 1);
        }
        return pipeline;
    }

    private long createPipeline() {
        try (var stack = stackPush()) {
            final Target t = buildTarget;
            final int numColorTargets = t.colorFormats != null ? t.colorFormats.length : 0;
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(numColorTargets, stack);
            for (int i = 0; i < numColorTargets; i++) {
                final var desc = colorDesc.get(i);
                desc.format(t.colorFormats[i]);
                final int mask = (buildColorTargets > 0 && enabled(t, i)) ? buildColorMask : 0;
                desc.blend_state()
                    .enable_blend(false)
                    .color_write_mask((byte) mask)
                    .enable_color_write_mask(true);
            }

            final boolean hasDepthTarget = t.depthFormat != 0;
            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack)
                .enable_depth_test(buildDepthFlag)
                .enable_depth_write(buildDepthFlag)
                .compare_op(SDL_GPU_COMPAREOP_ALWAYS)
                .enable_stencil_test(buildStencilFlag)
                .compare_mask((byte) 0xFF)
                .write_mask((byte) buildStencilMask);
            if (buildStencilFlag) {
                depthStencil.front_stencil_state()
                    .fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .pass_op(SDL_GPU_STENCILOP_REPLACE)
                    .depth_fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .compare_op(SDL_GPU_COMPAREOP_ALWAYS);
                depthStencil.back_stencil_state()
                    .fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .pass_op(SDL_GPU_STENCILOP_REPLACE)
                    .depth_fail_op(SDL_GPU_STENCILOP_REPLACE)
                    .compare_op(SDL_GPU_COMPAREOP_ALWAYS);
            }

            final SDL_GPURasterizerState rasterizer = SDL_GPURasterizerState.calloc(stack)
                .cull_mode(SDL_GPU_CULLMODE_NONE)
                .front_face(SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE)
                .fill_mode(SDL_GPU_FILLMODE_FILL);

            final SDL_GPUMultisampleState multisample = SDL_GPUMultisampleState.calloc(stack)
                .sample_count(SDL_GPU_SAMPLECOUNT_1);

            final SDL_GPUGraphicsPipelineTargetInfo targetInfo = SDL_GPUGraphicsPipelineTargetInfo.calloc(stack)
                .num_color_targets(numColorTargets)
                .color_target_descriptions(colorDesc)
                .depth_stencil_format(t.depthFormat)
                .has_depth_stencil_target(hasDepthTarget);

            final SDL_GPUVertexInputState vertexInput = SDL_GPUVertexInputState.calloc(stack);

            final SDL_GPUGraphicsPipelineCreateInfo ci = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
                .vertex_shader(sdlVertexShader)
                .fragment_shader(sdlFragmentShaders[buildColorTargets])
                .primitive_type(SDL_GPU_PRIMITIVETYPE_TRIANGLELIST)
                .rasterizer_state(rasterizer)
                .multisample_state(multisample)
                .depth_stencil_state(depthStencil)
                .target_info(targetInfo)
                .vertex_input_state(vertexInput);

            final long pipeline = SDL_CreateGPUGraphicsPipeline(pipelineStore.device().getDevice(), ci);
            if (pipeline == 0) {
                LOG.error("AttachmentClear: pipeline creation failed: {}", SDLError.SDL_GetError());
                return PipelineStore.BAD_PIPELINE_SENTINEL;
            }
            return pipeline;
        }
    }

    private boolean ensureShaders(int colorTargets) {
        if (colorTargets < 0 || colorTargets > MAX_COLOR_TARGETS) return false;
        if (sdlFragmentShaders[colorTargets] != 0) return true;
        if (fragmentFailed[colorTargets]) return false;
        fragmentFailed[colorTargets] = true;

        if (vertexShaderId == 0) {
            vertexShaderId = shaderManager.createShader(GL20.GL_VERTEX_SHADER);
            shaderManager.shaderSource(vertexShaderId, ShaderLoader.getShaderSource(VERTEX_SOURCE));
            shaderManager.compileShader(vertexShaderId);
        }

        final String baseSource = ShaderLoader.getShaderSource(FRAGMENT_SOURCE);
        final String fragmentSource = colorTargets == 0 ? baseSource : withColorOutputs(baseSource, colorTargets);

        final int fs = shaderManager.createShader(GL20.GL_FRAGMENT_SHADER);
        shaderManager.shaderSource(fs, fragmentSource);
        shaderManager.compileShader(fs);

        final int prog = shaderManager.createProgram();
        shaderManager.attachShader(prog, vertexShaderId);
        shaderManager.attachShader(prog, fs);
        shaderManager.linkProgram(prog);

        final ShaderManager.ProgramObject po = shaderManager.getProgram(prog);
        if (po == null || !po.linked) {
            LOG.error("AttachmentClear: clear_attachment program (COLOR_TARGETS={}) failed to link: {}", colorTargets, po == null ? "no program object" : po.infoLog);
            return false;
        }
        if (colorTargets > 0 && po.fragmentUboSize != 16) {
            LOG.error("AttachmentClear: clear_attachment fragment UBO size {} != 16 for COLOR_TARGETS={}", po.fragmentUboSize, colorTargets);
            return false;
        }

        if (sdlVertexShader == 0) {
            sdlVertexShader = shaderManager.createSDLShader(po.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, po.vertexResources.numSamplers(), po.vertexResources.numUBOs(), po.vertexResources.numStorageBuffers(), po.vertexResources.numStorageTextures());
            if (sdlVertexShader == 0) {
                LOG.error("AttachmentClear: clear_attachment vertex SDL shader creation failed");
                return false;
            }
        }

        final long fragmentShader = shaderManager.createSDLShader(po.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, po.fragmentResources.numSamplers(), po.fragmentResources.numUBOs(), po.fragmentResources.numStorageBuffers(), po.fragmentResources.numStorageTextures());
        if (fragmentShader == 0) {
            LOG.error("AttachmentClear: clear_attachment fragment SDL shader creation failed (COLOR_TARGETS={})", colorTargets);
            return false;
        }

        sdlFragmentShaders[colorTargets] = fragmentShader;
        fragmentFailed[colorTargets] = false;
        return true;
    }

    private static String withColorOutputs(String source, int count) {
        final GlslTransformUtils.QuietParse parsed = GlslTransformUtils.parseBothQuiet(source);
        final Transformer transformer = new Transformer(parsed.full());
        transformer.injectVariable("uniform vec4 clearColor;");
        for (int i = 0; i < count; i++) {
            transformer.injectVariable("layout(location = " + i + ") out vec4 angelica_ClearColor" + i + ";");
            transformer.appendMain("angelica_ClearColor" + i + " = clearColor;");
        }
        final String header = GlslTransformUtils.getFormattedShader(parsed.pre(), "").trim();
        final StringBuilder out = new StringBuilder();
        transformer.mutateTree(tree -> out.append(GlslTransformUtils.getFormattedShader(tree, header)));
        return out.toString();
    }

    public void shutdown() {
        vertexShaderId = 0;
        sdlVertexShader = 0;
        Arrays.fill(sdlFragmentShaders, 0L);
        Arrays.fill(fragmentFailed, false);
        Arrays.fill(cacheKeys, 0L);
        Arrays.fill(cachePipelines, 0L);
        cacheNext = 0;
    }
}
