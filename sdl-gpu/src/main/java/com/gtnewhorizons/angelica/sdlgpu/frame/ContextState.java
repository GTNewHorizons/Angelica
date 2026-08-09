package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineCache;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.sdl.SDL_GPUBufferBinding;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.sdl.SDL_GPUViewport;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Per-thread (per-GL-context) client state for {@link SDLGPURenderBackend}.
 */
public final class ContextState {

    public final Thread owner = Thread.currentThread();

    public static final int MAX_INDEXED_BUFFERS = 16;
    public static final int MAX_IMAGE_UNITS = 8;
    public static final int MAX_SAMPLERS = 16;
    public static final int MAX_STORAGE_BUFFERS_PER_STAGE = 8;
    public static final int MAX_VERTEX_ATTRIBS = 16;
    public static final int MAX_COLOR_ATTACHMENTS = 8;

    public static final class PixelStoreState {
        public int unpackAlignment = 4;
        public int unpackRowLength = 0;
        public int unpackSkipPixels = 0;
        public int unpackSkipRows = 0;
        public int packAlignment = 4;
        public boolean isDefault() {
            return unpackAlignment == 4 && unpackRowLength == 0 && unpackSkipPixels == 0 && unpackSkipRows == 0 && packAlignment == 4;
        }
    }

    public final PixelStoreState pixelStore = new PixelStoreState();

    public static final class VAOState {
        public final boolean[] attribEnabled = new boolean[MAX_VERTEX_ATTRIBS];
        public int attribEnabledMask;
        public final int[] attribSize = new int[MAX_VERTEX_ATTRIBS];
        public final int[] attribType = new int[MAX_VERTEX_ATTRIBS];
        public final boolean[] attribNormalized = new boolean[MAX_VERTEX_ATTRIBS];
        public final boolean[] attribIsInteger = new boolean[MAX_VERTEX_ATTRIBS];
        public final int[] attribStride = new int[MAX_VERTEX_ATTRIBS];
        public final long[] attribOffset = new long[MAX_VERTEX_ATTRIBS];
        public final int[] attribVBO = new int[MAX_VERTEX_ATTRIBS];
        public final int[] attribRelativeOffset = new int[MAX_VERTEX_ATTRIBS];
        public final int[] attribBinding = new int[MAX_VERTEX_ATTRIBS];
        public final int[] bindingBuffer = new int[MAX_VERTEX_ATTRIBS];
        public final long[] bindingOffset = new long[MAX_VERTEX_ATTRIBS];
        public final int[] bindingStride = new int[MAX_VERTEX_ATTRIBS];
        public final int[] bindingDivisor = new int[MAX_VERTEX_ATTRIBS];
        public int elementBuffer;
        public VAOState() { for (int i = 0; i < MAX_VERTEX_ATTRIBS; i++) attribBinding[i] = i; }
    }

    public float clearR, clearG, clearB, clearA;
    public float viewportX, viewportY, viewportW, viewportH;
    public float viewportDepthNear = 0.0f;
    public float viewportDepthFar = 1.0f;
    public int scissorX, scissorY, scissorW, scissorH;
    public boolean scissorEnabled;
    public int activeTextureUnit;
    public int boundProgram;
    public int autoPushedProgram;

    public final PipelineCache pipeline = new PipelineCache();

    public int slotWrites;
    public int slotWritesElided;
    public int ssboBinds;

    public float blendColorR, blendColorG, blendColorB, blendColorA;
    public int stencilRef;
    public float depthClearValue = 1.0f;
    public int stencilClearValue;
    public boolean primitiveRestartEnabled;
    public int primitiveRestartSentinel;

    public boolean viewportDirty = true;
    public boolean scissorDirty = true;
    public boolean blendColorDirty = true;
    public long lastBoundPipeline;
    public long lastAppliedRenderPassGen;

    public int lastAppliedStencilRef = Integer.MIN_VALUE;
    public long lastBoundEboHandle;
    public int lastBoundEboIndexSize = -1;
    public int lastBoundEboOffset;
    public int samplerBindGen;
    public int lastAppliedSamplerBindGen = -1;
    public int lastAppliedSamplerProgram;
    public long lastAppliedSamplerCb;
    public int lastAppliedStorageTexBindGen = -1;
    public int lastAppliedStorageTexProgram;
    public long lastAppliedStorageTexCb;
    public int ssboBindGen;
    public int lastAppliedStorageBufBindGen = -1;
    public int lastAppliedStorageBufProgram;
    public long lastAppliedStorageBufCb;
    public int pendingMutationGen;
    public int lastFlushedSamplerBindGen = -1;
    public int lastFlushedProgram = -1;
    public int lastFlushedPendingMutationGen = -1;
    public long lastPushedCbVs;
    public long lastPushedCbFs;
    public int lastPushedProgramVs;
    public int lastPushedProgramFs;
    public int uboRangeGen;
    public int lastPushedUboGenVs = -1;
    public int lastPushedUboGenFs = -1;
    public ShaderManager.ProgramObject boundProgramObj;

    private final Reference2ObjectOpenHashMap<ShaderManager.ProgramObject, UniformStaging> uniformStagingByProgram = new Reference2ObjectOpenHashMap<>();
    private ShaderManager.ProgramObject stagingProg;
    private UniformStaging currentUniformStaging;

    public UniformStaging uniformStaging(ShaderManager.ProgramObject prog) {
        if (prog == stagingProg) return currentUniformStaging;
        UniformStaging us = uniformStagingByProgram.get(prog);
        if (us == null) {
            us = new UniformStaging();
            us.allocate(prog);
            uniformStagingByProgram.put(prog, us);
        }
        stagingProg = prog;
        currentUniformStaging = us;
        return us;
    }

    public void releaseUniformStaging(ShaderManager.ProgramObject prog) {
        final UniformStaging us = uniformStagingByProgram.remove(prog);
        if (us != null) us.free();
        if (stagingProg == prog) { stagingProg = null; currentUniformStaging = null; }
    }

    public static final class UniformBlockState {
        private ByteBuffer buf;
        public FloatBuffer fb;
        public boolean dirty;
        public int glId;
        public int allocSize;
        public int size;
        public boolean flushedThisFrame;

        private long[] valueHash;

        public FloatBuffer staging(int sizeBytes) {
            if (buf == null || buf.capacity() < sizeBytes) {
                if (buf != null) MemoryUtil.memFree(buf);
                buf = MemoryUtil.memCalloc(sizeBytes);
                fb = buf.asFloatBuffer();
                valueHash = new long[(sizeBytes >> 2) + 1];
            }
            return fb;
        }

        public int hashSlot(int byteOffset) {
            final long[] h = valueHash;
            if (h == null) return -1;
            final int i = byteOffset >> 2;
            return (i >= 0 && i < h.length) ? i : -1;
        }

        public long hash(int slot) {
            return valueHash[slot];
        }

        public void setHash(int slot, long value) {
            valueHash[slot] = value;
        }

        public ByteBuffer bytes() {
            return buf;
        }

        public void free() {
            if (buf != null) {
                MemoryUtil.memFree(buf);
                buf = null;
                fb = null;
            }
        }
    }

    public final UniformBlockState[] uniformBlocks = { new UniformBlockState(), new UniformBlockState() };

    public boolean anyUniformBlockDirty() {
        return uniformBlocks[0].dirty || uniformBlocks[1].dirty;
    }

    public int boundArrayBuffer;
    public int boundIndirectBuffer;
    public int boundUniformBuffer;
    public int boundCopyReadBuffer;
    public int boundCopyWriteBuffer;
    public int boundSSBO;
    public int boundPixelUnpackBuffer;
    public int boundPixelPackBuffer;

    public int getBoundBuffer(int target) {
        return switch (target) {
            case GL15.GL_ARRAY_BUFFER -> boundArrayBuffer;
            case GL15.GL_ELEMENT_ARRAY_BUFFER -> currentVao.elementBuffer;
            case GL31.GL_UNIFORM_BUFFER -> boundUniformBuffer;
            case GL30.GL_PIXEL_UNPACK_BUFFER -> boundPixelUnpackBuffer;
            case GL30.GL_PIXEL_PACK_BUFFER -> boundPixelPackBuffer;
            case GL40.GL_DRAW_INDIRECT_BUFFER -> boundIndirectBuffer;
            case GL31.GL_COPY_READ_BUFFER -> boundCopyReadBuffer;
            case GL31.GL_COPY_WRITE_BUFFER -> boundCopyWriteBuffer;
            case GL43.GL_SHADER_STORAGE_BUFFER -> boundSSBO;
            default -> 0;
        };
    }
    public final int[] boundUboByIndex = new int[MAX_INDEXED_BUFFERS];
    public final int[] uboRangeOffset = new int[MAX_INDEXED_BUFFERS];
    public final int[] uboRangeSize = new int[MAX_INDEXED_BUFFERS];
    public final int[] boundSsboByIndex = new int[MAX_INDEXED_BUFFERS];
    public final int[] boundStorageTextureByUnit = new int[MAX_IMAGE_UNITS];
    public final int[] boundStorageTextureLevel = new int[MAX_IMAGE_UNITS];
    public final boolean[] boundStorageTextureLayered = new boolean[MAX_IMAGE_UNITS];
    public final int[] boundStorageTextureLayer = new int[MAX_IMAGE_UNITS];
    public final int[] boundStorageTextureAccess = new int[MAX_IMAGE_UNITS];
    public final int[] boundStorageTextureFormat = new int[MAX_IMAGE_UNITS];
    public final int[] boundTextures = new int[32];
    public final int[] boundSamplerObjects = new int[32];

    public boolean computeBatchRequested;
    public long computeBatchPass;
    public int computeBatchProgram;
    public int computeBatchRwBufCount = -1;
    public int computeBatchRwTexCount = -1;
    public final long[] computeBatchRwBufs = new long[MAX_INDEXED_BUFFERS];
    public final long[] computeBatchRwTexs = new long[MAX_IMAGE_UNITS];
    public final int[] computeBatchRwTexLevels = new int[MAX_IMAGE_UNITS];

    public int mappedBufferGlId;
    public ByteBuffer mappedStagingBuffer;
    public long mappedOffset;
    public long mappedLength;
    public boolean mappedInvalidate;
    public int mappedAccessFlags;

    public final SDL_GPUTextureSamplerBinding.Buffer fragSamplerBindings = SDL_GPUTextureSamplerBinding.calloc(MAX_SAMPLERS);
    public final SDL_GPUTextureSamplerBinding.Buffer vertSamplerBindings = SDL_GPUTextureSamplerBinding.calloc(MAX_SAMPLERS);
    public final long[] lastFragSamplerTex = new long[MAX_SAMPLERS];
    public final long[] lastFragSamplerSmp = new long[MAX_SAMPLERS];
    public final long[] lastVertSamplerTex = new long[MAX_SAMPLERS];
    public final long[] lastVertSamplerSmp = new long[MAX_SAMPLERS];
    public int lastFragSamplerProgram;
    public int lastVertSamplerProgram;

    public final PointerBuffer fragStorageTexBindings = PointerBuffer.allocateDirect(MAX_IMAGE_UNITS);
    public final PointerBuffer vertStorageTexBindings = PointerBuffer.allocateDirect(MAX_IMAGE_UNITS);
    public final long[] lastFragStorageTex = new long[MAX_IMAGE_UNITS];
    public final long[] lastVertStorageTex = new long[MAX_IMAGE_UNITS];
    public int lastFragStorageTexProgram;
    public int lastVertStorageTexProgram;

    public final PointerBuffer fragStorageBufBindings = PointerBuffer.allocateDirect(MAX_STORAGE_BUFFERS_PER_STAGE);
    public final PointerBuffer vertStorageBufBindings = PointerBuffer.allocateDirect(MAX_STORAGE_BUFFERS_PER_STAGE);
    public final long[] lastFragStorageBuf = new long[MAX_STORAGE_BUFFERS_PER_STAGE];
    public final long[] lastVertStorageBuf = new long[MAX_STORAGE_BUFFERS_PER_STAGE];
    public int lastFragStorageBufProgram;
    public int lastVertStorageBufProgram;

    public boolean deferUploads;
    public int drawsSincePersistentDrain;

    public boolean needsSeed = true;
    public long frameHighestEnqueuedSeq;

    public int boundFboId;
    public int boundReadFboId;

    public final LongOpenHashSet clearedTexturesThisFrame = new LongOpenHashSet();
    public final LongOpenHashSet clearedStencilTexturesThisFrame = new LongOpenHashSet();

    public final LongOpenHashSet pendingColorTextures = new LongOpenHashSet();
    public final Long2ObjectOpenHashMap<float[]> pendingColorValues = new Long2ObjectOpenHashMap<>();
    public final LongOpenHashSet pendingDepthTextures = new LongOpenHashSet();
    public final Long2FloatOpenHashMap pendingDepthValues = new Long2FloatOpenHashMap();
    public final LongOpenHashSet pendingStencilTextures = new LongOpenHashSet();
    public final Long2IntOpenHashMap pendingStencilValues = new Long2IntOpenHashMap();
    public boolean pendingSwapchainClear;
    public float pendingSwapchainR, pendingSwapchainG, pendingSwapchainB, pendingSwapchainA;
    public boolean pendingSwapchainDepthClear;
    public boolean pendingSwapchainStencilClear;
    public float pendingSwapchainDepth;
    public int pendingSwapchainStencil;

    public final LongArrayList samplerFlushColorHandles = new LongArrayList();
    public final IntArrayList samplerFlushColorGlIds = new IntArrayList();
    public final LongArrayList samplerFlushDepthHandles = new LongArrayList();
    public final IntArrayList samplerFlushDepthGlIds = new IntArrayList();

    public boolean[] materializeFlushConsumed = new boolean[MAX_COLOR_ATTACHMENTS * 4];
    public final int[] materializeFlushBatchIdx = new int[MAX_COLOR_ATTACHMENTS];

    public long vboBindingsAddr;
    public SDL_GPUBufferBinding eboBinding;
    public SDL_GPUViewport cachedViewport;
    public SDL_Rect cachedScissor;
    public SDL_FColor cachedBlendColor;

    public long attribStateGen = 1;
    public long nextAttribStateGen = 1;
    public void bumpAttribStateGen() { attribStateGen = ++nextAttribStateGen; }
    public long lastAppliedVboBindGen;
    public int lastAppliedVboBindProgram;
    public long lastAppliedVboBindCb;
    public int boundVAO;
    public final VAOState defaultVaoState = new VAOState();
    public VAOState currentVao = defaultVaoState;

    public long fanIndexBuffer;
    public int fanIndexBufferCapacity;
    public int fanIndexBufferOffset;

    public int[] eboSplitFirsts = new int[16];
    public int[] eboSplitCounts = new int[16];

    public void ensureEboSplitScratch(int min) {
        if (eboSplitFirsts.length < min) {
            final int n = Math.max(min, eboSplitFirsts.length * 2);
            eboSplitFirsts = new int[n];
            eboSplitCounts = new int[n];
        }
    }

    public int attribDefaultsDirtyMask;
    public final float[] attribDefaults = new float[MAX_VERTEX_ATTRIBS * 4];
    public int attribDefaultsRingBase;
    public int ringAdvancesThisFrame;
    public int ringChunkBaseOffset;
    public int ringChunkUsedBlocks;
    public ByteBuffer ringChunkStaging;

    public ContextState() {
        vboBindingsAddr = MemoryUtil.nmemCalloc(MAX_VERTEX_ATTRIBS, SDL_GPUBufferBinding.SIZEOF);
        eboBinding = SDL_GPUBufferBinding.calloc();
        cachedViewport = SDL_GPUViewport.calloc();
        cachedScissor = SDL_Rect.calloc();
        cachedBlendColor = SDL_FColor.calloc();
    }
}
