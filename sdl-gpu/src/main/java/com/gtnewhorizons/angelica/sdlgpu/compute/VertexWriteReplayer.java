package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.backend.VertexWriteReplaySetup;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL41;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;

/**
 * Performs the image writes of a vertex shader that SDL-GPU strips (graphics stages may only read storage images).
 * Every draw made with a program that has a {@link Replay} is captured: its vertex and index buffers, attribute layout,
 * draw range, the uniform values the replay reads and the textures it samples. When the render pass ends, the replay
 * compute shader re-runs the vertex shader for each captured vertex and performs the writes.
 */
public final class VertexWriteReplayer {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final int WORKGROUP_SIZE = 64;
    private static final int MAX_CAPTURES = 1 << 16;
    private static final int VERTEX_BUFFER_BINDING = 9;
    private static final int VERTEX_BUFFER_COUNT = 4;
    private static final int INDEX_BUFFER_BINDING = VERTEX_BUFFER_BINDING + VERTEX_BUFFER_COUNT;
    private static final int[] COMPONENT_SIZE = { 4, 2, 1, 1, 2, 2, 4, 4, 4, 4, 4, 4 };
    private static final int FIRST_PACKED_TYPE = 9;
    private static final int PACKED_10F_11F_11F = 11;

    private static final int ENABLED = 0x2000;
    private static final int BGRA = 0x4000;
    private static final int NO_ELEMENT = 0x8000;
    private static final int MAX_DIVISOR = 0xFFFF;
    private static final int UNCHANGED = -1;
    private static final int TEXTURE_UNITS = 32;
    private static final int SAMPLER_OBJECT_OF_DRAW = -1;

    public static final class Replay {
        final int replayProgram;
        final VertexWriteReplaySetup setup;

        private boolean resolved;
        private boolean broken;
        int[] inputLocations;
        int[] uniformSources;
        int[] uniformTargets;
        int[] samplerUnits;
        IntSupplier[] samplerSources;
        int[] samplerObjects;
        int[] packStorageSlots;
        int[] packRwStorageSlots;
        int[] uniformBlockIndices;
        int uniformBlockCount;
        int locAttr, locAttrDefault, locDraw, locIndex, locRestart, locInvocationBase;
        long[] lastHashes;
        boolean lastHashesApplied;

        public Replay(VertexWriteReplaySetup setup) {
            this.replayProgram = setup.replayProgram();
            this.setup = setup;
        }

        IntSupplier[] images() {
            return setup.images();
        }

        boolean resolve(ShaderManager.ProgramObject graphics, ShaderManager.ProgramObject replay) {
            if (resolved) return !broken;
            resolved = true;

            final String[] inputNames = setup.inputNames();
            inputLocations = new int[inputNames.length];
            for (int i = 0; i < inputNames.length; i++) {
                int loc = graphics.resolvedAttribLocations.getInt(inputNames[i]);
                if (loc < 0) loc = graphics.attribLocationBindings.getInt(inputNames[i]);
                if (loc < 0) loc = setup.inputLocations()[i];
                inputLocations[i] = loc;
            }

            final IntArrayList sources = new IntArrayList();
            final IntArrayList targets = new IntArrayList();
            final List<String> samplerNames = new ArrayList<>();
            for (int target = 0; target < replay.uniformSlotCount; target++) {
                final String name = replay.locationName[target];
                if (name == null || name.startsWith("_vg_")) continue;
                if (replay.locationKind[target] == ShaderManager.ProgramObject.LOCATION_KIND_SAMPLER) {
                    samplerNames.add(name);
                    continue;
                }
                if (replay.locationKind[target] != ShaderManager.ProgramObject.LOCATION_KIND_PLAIN) continue;
                final int source = graphics.nameToLocation.getInt(name);
                if (source < 0) continue;
                sources.add(source);
                targets.add(target);
            }
            uniformSources = sources.toIntArray();
            uniformTargets = targets.toIntArray();
            lastHashes = new long[uniformSources.length];
            resolveSamplers(graphics, replay, samplerNames);

            final IntArrayList storage = new IntArrayList();
            for (int slot : replay.computeBindingMap.roSsboGlSlots()) {
                if (slot < VERTEX_BUFFER_BINDING || slot > INDEX_BUFFER_BINDING) storage.add(slot);
            }
            packStorageSlots = storage.toIntArray();
            packRwStorageSlots = replay.computeBindingMap.rwSsboGlSlots().clone();
            final IntArrayList blocks = new IntArrayList();
            final boolean[] isDefault = replay.computeBindingMap.uboIsDefaultBlock();
            for (int i = 0; i < replay.computeBindingMap.uboGlSlots().length; i++) {
                if (i >= isDefault.length || !isDefault[i]) blocks.add(i);
            }
            uniformBlockIndices = blocks.toIntArray();
            uniformBlockCount = replay.computeBindingMap.uboGlSlots().length;

            locAttr = replay.nameToLocation.getInt("_vg_attr");
            locAttrDefault = replay.nameToLocation.getInt("_vg_attrDefault");
            locDraw = replay.nameToLocation.getInt("_vg_draw");
            locIndex = replay.nameToLocation.getInt("_vg_index");
            locRestart = replay.nameToLocation.getInt("_vg_restart");
            locInvocationBase = replay.nameToLocation.getInt("_vg_invocationBase");
            if (locDraw < 0 || locInvocationBase < 0) {
                broken = true;
                LOG.warn("Vertex write replay program {} is missing its dispatch uniforms; its writes are dropped", replayProgram);
            }
            return !broken;
        }

        private void resolveSamplers(ShaderManager.ProgramObject graphics, ShaderManager.ProgramObject replay, List<String> names) {
            final IntArrayList units = new IntArrayList();
            final List<IntSupplier> sources = new ArrayList<>();
            final IntArrayList objects = new IntArrayList();
            final IntArrayList ownTextureIndices = new IntArrayList();
            final List<String> ownTextureNames = new ArrayList<>();
            final List<String> pipelineNames = Arrays.asList(setup.samplerNames());
            for (String name : names) {
                int unit = graphics.samplerTextureUnits.getInt(name);
                if (unit < 0) {
                    final int i = pipelineNames.indexOf(name);
                    if (i < 0) continue;
                    if (setup.samplerTextures()[i] != null && setup.samplerUnits()[i] < 0) {
                        ownTextureNames.add(name);
                        ownTextureIndices.add(i);
                        continue;
                    }
                    unit = setup.samplerUnits()[i];
                    if (unit < 0) continue;
                }
                replay.samplerTextureUnits.put(name, unit);
                if (!units.contains(unit)) {
                    units.add(unit);
                    sources.add(null);
                    objects.add(SAMPLER_OBJECT_OF_DRAW);
                }
            }
            int free = TEXTURE_UNITS - 1;
            for (int k = 0; k < ownTextureNames.size(); k++) {
                while (free >= 0 && units.contains(free)) free--;
                if (free < 0) break;
                final int i = ownTextureIndices.getInt(k);
                replay.samplerTextureUnits.put(ownTextureNames.get(k), free);
                units.add(free);
                sources.add(setup.samplerTextures()[i]);
                objects.add(setup.samplerObjects()[i]);
            }
            samplerUnits = units.toIntArray();
            samplerSources = sources.toArray(new IntSupplier[0]);
            samplerObjects = objects.toIntArray();
        }
    }

    private static final class Capture {
        Replay replay;
        final long[] buffers = new long[VERTEX_BUFFER_COUNT];
        int bufferCount;
        long indexBuffer;
        float[] attr = new float[0];
        float[] attrDefault = new float[0];
        int[] samplerTextures = new int[0];
        long[] samplerHandles = new long[0];
        int[] imageTextures = new int[0];
        long[] packStorage = new long[0];
        long[] packRwStorage = new long[0];
        ByteBuffer[] uniformBlocks = new ByteBuffer[0];
        int first, count, instances, baseVertex, indexSize, indexOffset, indexCount;
        boolean restart;
        int restartIndex;
    }

    private final ShaderManager shaderManager;
    private final ResourceManager resourceManager;
    private final PipelineApplier pipelineApplier;
    private final ComputeBinder computeBinder;
    private final Supplier<ContextState> state;

    private final ArrayList<Capture> captures = new ArrayList<>();
    private int pending;
    private float[] uniformValues = new float[4096];
    private int[] uniformLengths = new int[256];
    private int uniformValueCount;
    private int uniformCount;
    private boolean flushing;
    private final long[] handlesByGlSlot = new long[ContextState.MAX_INDEXED_BUFFERS];
    private final long[] rwHandlesByGlSlot = new long[ContextState.MAX_INDEXED_BUFFERS];
    private final long[] samplerOverrides = new long[TEXTURE_UNITS];
    private final Set<String> warned = new HashSet<>();

    public VertexWriteReplayer(ShaderManager shaderManager, ResourceManager resourceManager, PipelineApplier pipelineApplier, ComputeBinder computeBinder, Supplier<ContextState> state) {
        this.shaderManager = shaderManager;
        this.resourceManager = resourceManager;
        this.pipelineApplier = pipelineApplier;
        this.computeBinder = computeBinder;
        this.state = state;
    }

    public void setReplay(int graphicsProgram, VertexWriteReplaySetup setup) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(graphicsProgram);
        if (prog == null) return;
        if (setup == null) {
            prog.vertexWriteReplay = null;
            return;
        }
        if (setup.images().length > ContextState.MAX_IMAGE_UNITS) {
            warnOnce("images:" + setup.replayProgram(), "Vertex write replay program " + setup.replayProgram() + " uses " + setup.images().length + " images, more than the " + ContextState.MAX_IMAGE_UNITS + " image units; its writes are dropped");
            prog.vertexWriteReplay = null;
            return;
        }
        prog.vertexWriteReplay = new Replay(setup);
    }

    public void ensureImageUsage(Replay replay) {
        final IntSupplier[] images = replay.images();
        final int written = replay.setup.writtenImageCount();
        for (int i = 0; i < images.length; i++) {
            final int texture = images[i].getAsInt();
            if (texture != 0) resourceManager.ensureTextureUsage(texture, i < written ? SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE : SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ);
        }
    }

    public void capture(ContextState st, int first, int count, int instances, int indexType, long indexOffset, int baseVertex) {
        final ShaderManager.ProgramObject graphics = st.boundProgramObj;
        final Replay replay = graphics.vertexWriteReplay;
        final ShaderManager.ProgramObject twin = shaderManager.getProgram(replay.replayProgram);
        if (twin == null || !twin.linked || twin.sdlComputePipeline == 0) return;
        if (!replay.resolve(graphics, twin)) return;
        if (count <= 0 || instances <= 0) return;
        if (pending == MAX_CAPTURES) {
            warnOnce("captures", "More than " + MAX_CAPTURES + " draws to replay vertex image writes for in one render pass; the rest are dropped");
            return;
        }

        final IntSupplier[] images = replay.images();
        final Capture c = acquire();
        c.replay = replay;
        c.bufferCount = 0;
        if (!captureInputs(st, replay, c)) return;

        if (indexType != 0) {
            c.indexBuffer = resourceManager.getBufferHandle(st.currentVao.elementBuffer);
            c.indexSize = indexType == GL11.GL_UNSIGNED_BYTE ? 1 : indexType == GL11.GL_UNSIGNED_SHORT ? 2 : 4;
            c.indexOffset = (int) indexOffset;
            if (c.indexBuffer == 0) return;
            final long indexBytes = resourceManager.getBufferSize(st.currentVao.elementBuffer);
            c.indexCount = indexBytes <= 0 ? -1 : (int) Math.min(Math.max(0L, indexBytes - indexOffset) / c.indexSize, Integer.MAX_VALUE);
            c.restart = st.primitiveRestartEnabled && indexType != GL11.GL_UNSIGNED_BYTE;
            c.restartIndex = indexType == GL11.GL_UNSIGNED_SHORT ? st.primitiveRestartSentinel & 0xFFFF : st.primitiveRestartSentinel;
        } else {
            c.indexBuffer = 0;
            c.indexSize = 0;
            c.indexOffset = 0;
            c.indexCount = 0;
            c.restart = false;
        }
        c.first = first;
        c.count = count;
        c.instances = instances;
        c.baseVertex = baseVertex;

        if (c.samplerTextures.length != replay.samplerUnits.length) {
            c.samplerTextures = new int[replay.samplerUnits.length];
            c.samplerHandles = new long[replay.samplerUnits.length];
        }
        for (int i = 0; i < replay.samplerUnits.length; i++) {
            final int unit = replay.samplerUnits[i];
            final IntSupplier source = replay.samplerSources[i];
            c.samplerTextures[i] = source != null ? source.getAsInt() : st.boundTextures[unit];
            final int samplerObject = replay.samplerObjects[i] != SAMPLER_OBJECT_OF_DRAW ? replay.samplerObjects[i] : st.boundSamplerObjects[unit];
            c.samplerHandles[i] = computeBinder.samplerHandle(samplerObject, c.samplerTextures[i]);
        }
        if (c.imageTextures.length != images.length) c.imageTextures = new int[images.length];
        for (int i = 0; i < images.length; i++) c.imageTextures[i] = images[i].getAsInt();
        if (c.packStorage.length != replay.packStorageSlots.length) c.packStorage = new long[replay.packStorageSlots.length];
        for (int i = 0; i < replay.packStorageSlots.length; i++) {
            c.packStorage[i] = resourceManager.getBufferHandle(st.boundSsboByIndex[replay.packStorageSlots[i]]);
        }
        if (c.packRwStorage.length != replay.packRwStorageSlots.length) c.packRwStorage = new long[replay.packRwStorageSlots.length];
        for (int i = 0; i < replay.packRwStorageSlots.length; i++) {
            c.packRwStorage[i] = resourceManager.getBufferHandle(st.boundSsboByIndex[replay.packRwStorageSlots[i]]);
        }
        captureUniformBlocks(st, twin, replay, c);

        captureUniforms(st, graphics, replay);
        pending++;
    }

    private boolean captureInputs(ContextState st, Replay replay, Capture c) {
        final int n = replay.inputLocations.length;
        if (c.attr.length < n * 4) {
            c.attr = new float[n * 4];
            c.attrDefault = new float[n * 4];
        }
        final ContextState.VAOState vao = st.currentVao;
        for (int k = 0; k < n; k++) {
            final int loc = replay.inputLocations[k];
            int x = 0, y = 0, z = 0, w = 0;
            if (loc >= 0 && loc < ContextState.MAX_VERTEX_ATTRIBS) {
                System.arraycopy(st.attribDefaults, loc * 4, c.attrDefault, k * 4, 4);
                if (vao.attribEnabled[loc]) {
                    final int type = componentType(vao.attribType[loc]);
                    final int binding = vao.attribBinding[loc];
                    final int buffer = vao.bindingBuffer[binding];
                    final long handle = type >= 0 ? resourceManager.getBufferHandle(buffer) : 0;
                    if (type < 0) warnOnce("type:" + vao.attribType[loc], "Vertex write replay cannot read vertex attribute type 0x" + Integer.toHexString(vao.attribType[loc]) + "; that input reads its default value");
                    if (handle != 0) {
                        final int slot = bufferSlot(c, handle);
                        if (slot < 0) {
                            warnOnce("buffers", "A draw reads more than " + VERTEX_BUFFER_COUNT + " vertex buffers; its vertex image writes are dropped");
                            return false;
                        }
                        final boolean bgra = vao.attribSize[loc] == GL12.GL_BGRA;
                        final int size = type == PACKED_10F_11F_11F ? 3 : bgra ? 4 : vao.attribSize[loc];
                        final int elementBytes = type >= FIRST_PACKED_TYPE ? 4 : size * COMPONENT_SIZE[type];
                        final int stride = vao.bindingStride[binding] != 0 ? vao.bindingStride[binding] : elementBytes;
                        final long offset = vao.bindingOffset[binding] + vao.attribRelativeOffset[loc];
                        final int divisor = Math.min(vao.bindingDivisor[binding], MAX_DIVISOR);
                        final long bufferSize = resourceManager.getBufferSize(buffer);
                        final boolean noElement = bufferSize > 0 && bufferSize < offset + elementBytes;
                        x = slot | type << 4 | size << 8 | (vao.attribNormalized[loc] ? 0x800 : 0) | (vao.attribIsInteger[loc] ? 0x1000 : 0)
                            | ENABLED | (bgra ? BGRA : 0) | (noElement ? NO_ELEMENT : 0) | divisor << 16;
                        y = (int) offset;
                        z = stride;
                        w = lastElement(bufferSize, offset, elementBytes, stride);
                    }
                }
            } else {
                c.attrDefault[k * 4] = 0f;
                c.attrDefault[k * 4 + 1] = 0f;
                c.attrDefault[k * 4 + 2] = 0f;
                c.attrDefault[k * 4 + 3] = 1f;
            }
            c.attr[k * 4] = Float.intBitsToFloat(x);
            c.attr[k * 4 + 1] = Float.intBitsToFloat(y);
            c.attr[k * 4 + 2] = Float.intBitsToFloat(z);
            c.attr[k * 4 + 3] = Float.intBitsToFloat(w);
        }
        return true;
    }

    static int lastElement(long bufferSize, long offset, int elementBytes, int stride) {
        if (bufferSize <= 0) return -1;
        final long room = bufferSize - offset - elementBytes;
        if (room < 0 || stride <= 0) return 0;
        return (int) Math.min(room / stride, 0xFFFFFFFFL);
    }

    private static int bufferSlot(Capture c, long handle) {
        for (int i = 0; i < c.bufferCount; i++) {
            if (c.buffers[i] == handle) return i;
        }
        if (c.bufferCount == VERTEX_BUFFER_COUNT) return -1;
        c.buffers[c.bufferCount] = handle;
        return c.bufferCount++;
    }

    static int componentType(int glType) {
        return switch (glType) {
            case GL11.GL_FLOAT -> 0;
            case GL30.GL_HALF_FLOAT -> 1;
            case GL11.GL_BYTE -> 2;
            case GL11.GL_UNSIGNED_BYTE -> 3;
            case GL11.GL_SHORT -> 4;
            case GL11.GL_UNSIGNED_SHORT -> 5;
            case GL11.GL_INT -> 6;
            case GL11.GL_UNSIGNED_INT -> 7;
            case GL41.GL_FIXED -> 8;
            case GL33.GL_INT_2_10_10_10_REV -> 9;
            case GL12.GL_UNSIGNED_INT_2_10_10_10_REV -> 10;
            case GL30.GL_UNSIGNED_INT_10F_11F_11F_REV -> PACKED_10F_11F_11F;
            default -> -1;
        };
    }

    private void captureUniformBlocks(ContextState st, ShaderManager.ProgramObject twin, Replay replay, Capture c) {
        if (c.uniformBlocks.length != replay.uniformBlockCount) c.uniformBlocks = new ByteBuffer[replay.uniformBlockCount];
        for (int index : replay.uniformBlockIndices) {
            final ByteBuffer view = computeBinder.uniformBlockView(st, twin, index);
            if (view == null) {
                if (c.uniformBlocks[index] != null) c.uniformBlocks[index].limit(0);
                continue;
            }
            ByteBuffer copy = c.uniformBlocks[index];
            if (copy == null || copy.capacity() < view.remaining()) copy = c.uniformBlocks[index] = ByteBuffer.allocateDirect(view.remaining()).order(ByteOrder.nativeOrder());
            copy.clear();
            copy.put(view);
            copy.flip();
        }
    }

    private void captureUniforms(ContextState st, ShaderManager.ProgramObject graphics, Replay replay) {
        final UniformStaging us = st.uniformStaging(graphics);
        final long[] hashes = us.uniformValueHashBySlot;
        for (int u = 0; u < replay.uniformSources.length; u++) {
            final int source = replay.uniformSources[u];
            final float[] value = us.uniformDataBySlot != null && source < us.uniformDataBySlot.length ? us.uniformDataBySlot[source] : null;
            final long hash = hashes != null && source < hashes.length ? hashes[source] : 0L;
            if (uniformCount == uniformLengths.length) uniformLengths = Arrays.copyOf(uniformLengths, uniformLengths.length * 2);
            if (value != null && replay.lastHashesApplied && hash == replay.lastHashes[u]) {
                uniformLengths[uniformCount++] = UNCHANGED;
                continue;
            }
            replay.lastHashes[u] = hash;
            final int len = value == null ? 0 : value.length;
            uniformLengths[uniformCount++] = len;
            if (len == 0) continue;
            if (uniformValueCount + len > uniformValues.length) uniformValues = Arrays.copyOf(uniformValues, Math.max(uniformValues.length * 2, uniformValueCount + len));
            System.arraycopy(value, 0, uniformValues, uniformValueCount, len);
            uniformValueCount += len;
        }
        replay.lastHashesApplied = true;
    }

    public boolean hasPending() {
        return pending > 0;
    }

    public void noteIndirectDraw() {
        warnOnce("indirect", "An indirect draw used a program whose vertex shader writes images; those writes are dropped");
    }

    private Capture acquire() {
        if (pending == captures.size()) captures.add(new Capture());
        return captures.get(pending);
    }

    public void flush() {
        if (pending == 0 || flushing) return;
        flushing = true;
        final ContextState st = state.get();
        final int savedProgram = st.boundProgram;
        final ShaderManager.ProgramObject savedProgramObj = st.boundProgramObj;
        try {
            int uniformCursor = 0;
            int valueCursor = 0;
            int start = 0;
            while (start < pending) {
                final Capture first = captures.get(start);
                final Replay replay = first.replay;
                int end = start;
                while (end < pending && captures.get(end).replay == replay && Arrays.equals(captures.get(end).imageTextures, first.imageTextures)
                    && Arrays.equals(captures.get(end).packRwStorage, first.packRwStorage)) end++;
                final int[] cursors = { uniformCursor, valueCursor };
                replayRun(st, replay, start, end, cursors);
                uniformCursor = cursors[0];
                valueCursor = cursors[1];
                start = end;
            }
        } finally {
            st.boundProgram = savedProgram;
            st.boundProgramObj = savedProgramObj;
            for (int i = 0; i < pending; i++) captures.get(i).replay = null;
            pending = 0;
            uniformCount = 0;
            uniformValueCount = 0;
            flushing = false;
        }
    }

    private void replayRun(ContextState st, Replay replay, int start, int end, int[] cursors) {
        final ShaderManager.ProgramObject twin = shaderManager.getProgram(replay.replayProgram);
        if (twin == null || !twin.linked || twin.sdlComputePipeline == 0) {
            skipUniforms(replay, start, end, cursors);
            return;
        }

        final Capture first = captures.get(start);
        final int[] imageTextures = first.imageTextures;
        final int imageCount = imageTextures.length;
        final int written = replay.setup.writtenImageCount();
        final int[] savedImages = Arrays.copyOf(st.boundStorageTextureByUnit, imageCount);
        final int[] savedLevels = Arrays.copyOf(st.boundStorageTextureLevel, imageCount);
        final int[] savedAccess = Arrays.copyOf(st.boundStorageTextureAccess, imageCount);
        final int[] savedTextures = new int[replay.samplerUnits.length];
        for (int i = 0; i < replay.samplerUnits.length; i++) savedTextures[i] = st.boundTextures[replay.samplerUnits[i]];
        computeBinder.setSamplerOverrides(samplerOverrides);
        Arrays.fill(rwHandlesByGlSlot, 0L);
        for (int i = 0; i < replay.packRwStorageSlots.length; i++) rwHandlesByGlSlot[replay.packRwStorageSlots[i]] = first.packRwStorage[i];
        computeBinder.setReadWriteStorageBufferOverrides(rwHandlesByGlSlot);

        try {
            for (int i = 0; i < imageCount; i++) {
                st.boundStorageTextureByUnit[i] = imageTextures[i];
                st.boundStorageTextureLevel[i] = 0;
                st.boundStorageTextureAccess[i] = i < written ? GL15.GL_READ_WRITE : GL15.GL_READ_ONLY;
            }
            applySamplerTextures(st, replay, captures.get(start));

            st.boundProgram = replay.replayProgram;
            st.boundProgramObj = twin;
            final long pass = computeBinder.beginComputePassWithoutStorageBuffers(st);
            if (pass == 0) {
                skipUniforms(replay, start, end, cursors);
                return;
            }
            try {
                for (int i = start; i < end; i++) {
                    final Capture c = captures.get(i);
                    if (i > start && applySamplerTextures(st, replay, c)) computeBinder.rebindSamplers(pass, st);
                    dispatch(st, pass, replay, c, cursors);
                }
            } finally {
                computeBinder.endBatchedComputeDispatch(pass);
            }
        } finally {
            computeBinder.setReadWriteStorageBufferOverrides(null);
            computeBinder.setUniformBlockOverrides(null);
            System.arraycopy(savedImages, 0, st.boundStorageTextureByUnit, 0, imageCount);
            System.arraycopy(savedLevels, 0, st.boundStorageTextureLevel, 0, imageCount);
            System.arraycopy(savedAccess, 0, st.boundStorageTextureAccess, 0, imageCount);
            for (int i = 0; i < replay.samplerUnits.length; i++) {
                st.boundTextures[replay.samplerUnits[i]] = savedTextures[i];
                samplerOverrides[replay.samplerUnits[i]] = 0L;
            }
            computeBinder.setSamplerOverrides(null);
        }
    }

    private boolean applySamplerTextures(ContextState st, Replay replay, Capture c) {
        boolean changed = false;
        for (int i = 0; i < replay.samplerUnits.length; i++) {
            final int unit = replay.samplerUnits[i];
            if (st.boundTextures[unit] != c.samplerTextures[i] || samplerOverrides[unit] != c.samplerHandles[i]) {
                st.boundTextures[unit] = c.samplerTextures[i];
                samplerOverrides[unit] = c.samplerHandles[i];
                changed = true;
            }
        }
        return changed;
    }

    private void skipUniforms(Replay replay, int start, int end, int[] cursors) {
        replay.lastHashesApplied = false;
        for (int i = start; i < end; i++) {
            for (int u = 0; u < replay.uniformSources.length; u++) cursors[1] += Math.max(0, uniformLengths[cursors[0]++]);
        }
    }

    private void dispatch(ContextState st, long pass, Replay replay, Capture c, int[] cursors) {
        Arrays.fill(handlesByGlSlot, 0L);
        for (int i = 0; i < replay.packStorageSlots.length; i++) handlesByGlSlot[replay.packStorageSlots[i]] = c.packStorage[i];
        System.arraycopy(c.buffers, 0, handlesByGlSlot, VERTEX_BUFFER_BINDING, c.bufferCount);
        handlesByGlSlot[INDEX_BUFFER_BINDING] = c.indexBuffer;
        final boolean bound = computeBinder.bindRoStorageBufferHandles(pass, st, handlesByGlSlot);

        for (int u = 0; u < replay.uniformSources.length; u++) {
            final int len = uniformLengths[cursors[0]++];
            if (len <= 0) continue;
            final float[] value = pipelineApplier.reuseOrAlloc(st, replay.uniformTargets[u], len);
            if (value != null) {
                System.arraycopy(uniformValues, cursors[1], value, 0, len);
                pipelineApplier.putUniform(st, replay.uniformTargets[u], value);
            }
            cursors[1] += len;
        }
        if (!bound) return;

        final int inputs = replay.inputLocations.length;
        putFloats(st, replay.locAttr, c.attr, inputs * 4);
        putFloats(st, replay.locAttrDefault, c.attrDefault, inputs * 4);
        putInts(st, replay.locDraw, c.first, c.count, c.instances, c.baseVertex);
        putInts(st, replay.locIndex, c.indexSize, c.indexOffset, c.indexCount);
        putInts(st, replay.locRestart, c.restart ? 1 : 0, c.restartIndex);
        computeBinder.setUniformBlockOverrides(c.uniformBlocks);

        final long invocations = (long) c.count * c.instances;
        final long groups = (invocations + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
        for (long g0 = 0; g0 < groups; g0 += VoxelizationDispatcher.MAX_GROUPS_PER_DISPATCH) {
            putInts(st, replay.locInvocationBase, (int) (g0 * WORKGROUP_SIZE));
            computeBinder.pushPendingComputeUniforms(st);
            computeBinder.dispatchInBatch(pass, (int) Math.min(VoxelizationDispatcher.MAX_GROUPS_PER_DISPATCH, groups - g0), 1, 1);
        }
    }

    private void putFloats(ContextState st, int location, float[] source, int len) {
        if (location < 0 || len == 0) return;
        final float[] value = pipelineApplier.reuseOrAlloc(st, location, len);
        if (value == null) return;
        System.arraycopy(source, 0, value, 0, len);
        pipelineApplier.putUniform(st, location, value);
    }

    private void putInts(ContextState st, int location, int... ints) {
        if (location < 0) return;
        final float[] value = pipelineApplier.reuseOrAlloc(st, location, ints.length);
        if (value == null) return;
        for (int i = 0; i < ints.length; i++) value[i] = Float.intBitsToFloat(ints[i]);
        pipelineApplier.putUniform(st, location, value);
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) LOG.warn(message);
    }
}
