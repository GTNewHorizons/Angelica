package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import com.gtnewhorizons.angelica.sdlgpu.util.ThreadRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gtnewhorizons.angelica.sdlgpu.resource.UploadArena;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.sdl.SDL_GPUBlitInfo;
import org.lwjgl.sdl.SDL_GPUBlitRegion;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilTargetInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.sdl.SDLSurface.SDL_FLIP_NONE;
import static org.lwjgl.system.MemoryStack.*;

/**
 * Per-frame command buffer lifecycle: acquire, render pass, submit. Emulates OpenGL's per-context client state.
 */
public final class FrameManager {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final Tracy.ZoneId Z_SDL_FENCE_WAIT = Tracy.zoneId("sdlFenceWait", Tracy.COLOR_SWAP);
    private static final Tracy.ZoneId Z_SDL_SUBMIT = Tracy.zoneId("sdlSubmit", Tracy.COLOR_SWAP);
    private static final Tracy.ZoneId Z_SDL_ACQUIRE_WAIT = Tracy.zoneId("sdlAcquireWait", Tracy.COLOR_SWAP);

    private final Device device;
    private ResourceManager resourceManager;

    public static final class FrameState {
        public final Thread owner = Thread.currentThread();
        public long commandBuffer;
        public long renderPass;
        public long copyPass;
        public boolean computePassOpen;
        public boolean frameActive;
        public long currentColorTarget;
        public long currentDepthTarget;
        public long activeLayoutHash;
        public long frameNumber;
        public boolean clearedThisFrame;
        public boolean depthClearedThisFrame;
        public boolean fbo0UsedThisFrame;
        public boolean presentedThisFrame;
        public boolean swapchainUnavailable;
        public boolean windowThreadChecked;
        public long renderPassGeneration;

        public boolean wantFenceOnNextSubmit;
        public long lastAcquiredFence;

        public long lastPlottedRenderPassGen;
        public int computePassesThisFrame;
        public int submitsThisFrame;
        public long uniformPushBytesThisFrame;
        public int uniformPushesThisFrame;
        public int uniformPushSkipsThisFrame;
        public int externalUboPushesThisFrame;
        public int externalUboPushSkipsThisFrame;
        public int persistentDrainsThisFrame;
        public final int[] uniformBlockFlushesThisFrame = new int[2];
        public final long[] uniformBlockBytesThisFrame = new long[2];
        public int perFrameBlockPassBreaksThisFrame;
        public int batchBreakPipelineThisFrame;
        public int batchBreakSamplerThisFrame;
        public int stateAppliesThisFrame;
        public long acquireWaitNanosThisFrame;
        public volatile long lastFrameGateNanos;
        public volatile long lastFrameGateEndNanos;
        public int midFrameSubmitsThisFrame;
        public int mipGensThisFrame;
        public int mipGenSubmitsThisFrame;
        public int uploadFlushSubmitsThisFrame;
        public int blitsThisFrame;
        public int presentSkipsThisFrame;
        public int emptyFramesThisFrame;
        public int droppedDrawsThisFrame;
        public int computeBatchJoinsThisFrame;
        public int clearPassesThisFrame;
        public int swapchainPassesThisFrame;
        public int fboPassesThisFrame;
        public int arenaSubmitsThisFrame;
        public int arenaOverflowFlushesThisFrame;
        public int copyPassesThisFrame;
        public int materializedClearPassesThisFrame;
        public long lastEndFrameNanos;

        public long pendingUploadCommandBuffer;
        public long pendingUploadBytes;
        public int pendingUploadCommands;

        public long batchXfer;
        public ByteBuffer batchMapped;
        public int batchOffset;

        public long arenaXfer;
        public ByteBuffer arenaMapped;
        public long arenaCommandBuffer;
        public long arenaCopyPass;
        public final UploadArena arena = new UploadArena();

        public final LongArrayList pendingTexHandles = new LongArrayList();
        public final IntArrayList pendingTexX = new IntArrayList();
        public final IntArrayList pendingTexY = new IntArrayList();
        public final IntArrayList pendingTexW = new IntArrayList();
        public final IntArrayList pendingTexH = new IntArrayList();
        public final IntArrayList pendingTexLevels = new IntArrayList();
        public final IntArrayList pendingTexOffsets = new IntArrayList();

        public volatile boolean flushRequested;
        public volatile boolean syncOnNextFlush;
        public final IntOpenHashSet pendingMipGen = new IntOpenHashSet();

        public final Int2ObjectOpenHashMap<ByteBuffer> uboPushViews = new Int2ObjectOpenHashMap<>();
        public final Int2ObjectOpenHashMap<ByteBuffer> uboPushViewSource = new Int2ObjectOpenHashMap<>();
    }

    public ByteBuffer getUboPushView(int bufferGlId, ByteBuffer shadow) {
        final FrameState f = frame();
        final ByteBuffer existing = f.uboPushViews.get(bufferGlId);
        if (existing != null && f.uboPushViewSource.get(bufferGlId) == shadow) {
            existing.position(0).limit(shadow.capacity());
            return existing;
        }
        final ByteBuffer view = shadow.duplicate();
        view.position(0).limit(shadow.capacity());
        f.uboPushViews.put(bufferGlId, view);
        f.uboPushViewSource.put(bufferGlId, shadow);
        return view;
    }

    private volatile boolean globalSyncAtNextBeginFrame;

    private final ThreadRegistry<FrameState> registeredFrames = new ThreadRegistry<>(new FrameState[0]);

    private final ThreadLocal<FrameState> tlFrame = new ThreadLocal<>();

    public FrameState frame() {
        final FrameState sole = registeredFrames.sole();
        if (sole != null && sole.owner == Thread.currentThread()) return sole;
        final FrameState f = tlFrame.get();
        return f != null ? f : registerFrame();
    }

    private FrameState registerFrame() {
        final FrameState f = new FrameState();
        tlFrame.set(f);
        registeredFrames.add(f);
        return f;
    }

    public int registeredFrameCount() {
        return registeredFrames.size();
    }

    public FrameState busiestFrame() {
        final FrameState[] frames = registeredFrames.snapshot();
        FrameState best = null;
        for (final FrameState f : frames) {
            if (best == null || f.frameNumber > best.frameNumber) best = f;
        }
        return best != null ? best : frame();
    }

    public static final long FBO0_LAYOUT_HASH = 0x5CA1AB1E5CA1AB1EL;

    private final OffscreenTarget finalTarget = new OffscreenTarget();

    public OffscreenTarget finalTarget() { return finalTarget; }

    public void destroyFinalTarget() {
        if (resourceManager != null) finalTarget.destroy(resourceManager);
    }

    public static final long MAX_PENDING_UPLOAD_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_PENDING_UPLOAD_COMMANDS = 256;

    private Runnable beforeEndCopyPass;


    public FrameManager(Device device) {
        this.device = device;
    }

    public void setResourceManager(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public void beginFrame() {
        final FrameState f = frame();
        if (f.frameActive) {
            LOG.warn("beginFrame() called while frame already active on thread {}", Thread.currentThread().getName());
            return;
        }

        flushPendingUploadCommandBuffer(f);
        if (globalSyncAtNextBeginFrame) {
            globalSyncAtNextBeginFrame = false;
            Tracy.beginZone(Z_SDL_FENCE_WAIT);
            try { SDL_WaitForGPUIdle(device.getDevice()); }
            finally { Tracy.endZone(); }
        }

        f.commandBuffer = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (f.commandBuffer == 0) {
            throw new RuntimeException("Failed to acquire GPU command buffer: " + SDLError.SDL_GetError());
        }

        f.swapchainUnavailable = false;
        f.presentedThisFrame = false;

        maintainFinalTarget();

        f.frameActive = true;
        f.renderPass = 0;
        f.currentColorTarget = 0;
        f.currentDepthTarget = 0;
        f.frameNumber++;
        f.clearedThisFrame = false;
        f.depthClearedThisFrame = false;
        f.fbo0UsedThisFrame = false;
    }

    private static final long FINAL_TARGET_SIZE_POLL_NANOS = 1_000_000_000L;

    private long finalTargetPollNanos;
    private volatile Thread mainThread;

    public void setMainThread(Thread thread) {
        this.mainThread = thread;
    }

    // Resizing releases a texture an in-flight present may still reference, so drain before ensureSize.
    private void maintainFinalTarget() {
        if (resourceManager == null) return;
        final Thread owner = mainThread;
        if (owner != null && Thread.currentThread() != owner) return;

        final long now = System.nanoTime();
        final boolean poll = finalTargetPollNanos == 0L || now - finalTargetPollNanos >= FINAL_TARGET_SIZE_POLL_NANOS;
        if (!poll && !device.wasWindowResized() && finalTarget.isReady()) return;
        if (poll) finalTargetPollNanos = now;

        final long size = device.getWindowSizeInPixels();
        if (size == 0L) return;
        final int width = (int) (size >> 32);
        final int height = (int) size;
        if (finalTarget.isReady() && width == finalTarget.width() && height == finalTarget.height()) return;

        if (presenter != null) presenter.drain();
        finalTarget.ensureSize(device, resourceManager, width, height);
    }

    public void markFrameEmpty(FrameState f) {
        f.emptyFramesThisFrame++;
    }

    static boolean swapchainClearNeedsPassBreak(ContextState st, FrameState f) {
        return (st.pendingSwapchainClear || st.pendingSwapchainDepthClear || st.pendingSwapchainStencilClear) && f.renderPass != 0 && f.activeLayoutHash == FBO0_LAYOUT_HASH;
    }

    public boolean ensureFbo0RenderPass(FrameState f, ContextState st) {
        final boolean clearColor = st.pendingSwapchainClear;
        if (swapchainClearNeedsPassBreak(st, f)) {
            endRenderPassIfActive(f);
        }
        final boolean applied = ensureFbo0RenderPass(f,
            clearColor ? st.pendingSwapchainR : st.clearR,
            clearColor ? st.pendingSwapchainG : st.clearG,
            clearColor ? st.pendingSwapchainB : st.clearB,
            clearColor ? st.pendingSwapchainA : st.clearA,
            clearColor,
            st.pendingSwapchainDepthClear, st.pendingSwapchainDepthClear ? st.pendingSwapchainDepth : st.depthClearValue,
            st.pendingSwapchainStencilClear, st.pendingSwapchainStencilClear ? st.pendingSwapchainStencil : st.stencilClearValue);
        if (!applied) return false;
        st.pendingSwapchainClear = false;
        st.pendingSwapchainDepthClear = false;
        st.pendingSwapchainStencilClear = false;
        return true;
    }

    private boolean ensureFbo0RenderPass(FrameState f, float clearR, float clearG, float clearB, float clearA, boolean clear, boolean clearDepth, float depthValue, boolean clearStencil, int stencilValue) {
        if (!finalTarget.isReady() || f.commandBuffer == 0) return false;
        if (f.renderPass != 0 && f.activeLayoutHash == FBO0_LAYOUT_HASH) {
            return false;
        }

        endActiveEncoders(f);

        if (!f.clearedThisFrame) {
            clear = true;
        }

        final long depthTexture = resourceManager != null ? resourceManager.getOrCreateSwapchainDepthStencil(finalTarget.width(), finalTarget.height()) : 0L;
        if (depthTexture != 0 && !f.depthClearedThisFrame) {
            clearDepth = true;
            clearStencil = true;
        }

        try (var stack = stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer colorTargets = SDL_GPUColorTargetInfo.calloc(1, stack);
            final long ctAddr = colorTargets.get(0).address();
            MemoryAccess.putAddress(ctAddr + SDL_GPUColorTargetInfo.TEXTURE, finalTarget.colorTexture());
            MemoryAccess.putInt(ctAddr + SDL_GPUColorTargetInfo.LOAD_OP, clear ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
            MemoryAccess.putInt(ctAddr + SDL_GPUColorTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);

            if (clear) {
                final long ccAddr = ctAddr + SDL_GPUColorTargetInfo.CLEAR_COLOR;
                MemoryAccess.putFloat(ccAddr + SDL_FColor.R, clearR);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.G, clearG);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.B, clearB);
                MemoryAccess.putFloat(ccAddr + SDL_FColor.A, clearA);
                f.clearedThisFrame = true;
            }

            SDL_GPUDepthStencilTargetInfo depthTarget = null;
            if (depthTexture != 0) {
                depthTarget = SDL_GPUDepthStencilTargetInfo.calloc(stack);
                final long dtAddr = depthTarget.address();
                MemoryAccess.putAddress(dtAddr + SDL_GPUDepthStencilTargetInfo.TEXTURE, depthTexture);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.LOAD_OP, clearDepth ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STORE_OP, SDL_GPU_STOREOP_STORE);
                MemoryAccess.putFloat(dtAddr + SDL_GPUDepthStencilTargetInfo.CLEAR_DEPTH, depthValue);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_LOAD_OP, clearStencil ? SDL_GPU_LOADOP_CLEAR : SDL_GPU_LOADOP_LOAD);
                MemoryAccess.putInt(dtAddr + SDL_GPUDepthStencilTargetInfo.STENCIL_STORE_OP, SDL_GPU_STOREOP_STORE);
                MemoryAccess.putByte(dtAddr + SDL_GPUDepthStencilTargetInfo.CLEAR_STENCIL, (byte) stencilValue);
                if (clearDepth && clearStencil) f.depthClearedThisFrame = true;
            }

            if (preRenderPassHook != null) preRenderPassHook.run();
            assertNoEncoderActive(f, "SDL_BeginGPURenderPass(swapchain)");
            f.renderPass = SDL_BeginGPURenderPass(f.commandBuffer, colorTargets, depthTarget);
            if (f.renderPass == 0) {
                throw new RuntimeException("Failed to begin render pass: " + SDLError.SDL_GetError());
            }
            f.renderPassGeneration++;
            f.swapchainPassesThisFrame++;
            f.currentColorTarget = finalTarget.colorTexture();
            f.currentDepthTarget = depthTexture;
            f.activeLayoutHash = FBO0_LAYOUT_HASH;
            f.fbo0UsedThisFrame = true;
            finalTarget.markContent();
        }
        return true;
    }


    private volatile Runnable preRenderPassHook;
    private volatile Runnable beforeSubmit;
    private volatile Runnable afterPresent;

    public void setPreRenderPassHook(Runnable hook) { this.preRenderPassHook = hook; }

    public void noteComputePassBegun() {
        final FrameState f = frame();
        f.computePassesThisFrame++;
        f.computePassOpen = true;
    }

    public void noteComputePassEnded() { frame().computePassOpen = false; }

    public void noteComputeBatchJoin() { frame().computeBatchJoinsThisFrame++; }

    public void noteUniformPushBytes(long bytes) {
        final FrameState f = frame();
        f.uniformPushBytesThisFrame += bytes;
        f.uniformPushesThisFrame++;
    }

    public void noteUniformPushSkipped() { frame().uniformPushSkipsThisFrame++; }

    public void noteExternalUboPush() { frame().externalUboPushesThisFrame++; }

    public void noteExternalUboPushSkipped() { frame().externalUboPushSkipsThisFrame++; }

    public void notePerFrameBlockPassBreak() { frame().perFrameBlockPassBreaksThisFrame++; }

    public void noteBatchBreakPipeline() { frame().batchBreakPipelineThisFrame++; }

    public void noteBatchBreakSampler() { frame().batchBreakSamplerThisFrame++; }

    public void noteStateApply() { frame().stateAppliesThisFrame++; }

    public void notePersistentDrain() { frame().persistentDrainsThisFrame++; }

    public void noteUniformBlockFlush(int block, long bytes) {
        final FrameState f = frame();
        f.uniformBlockFlushesThisFrame[block]++;
        f.uniformBlockBytesThisFrame[block] += bytes;
    }

    public void noteMipGen() { frame().mipGensThisFrame++; }

    public void noteMipGenSubmit() { frame().mipGenSubmitsThisFrame++; }

    public void noteBlit() { frame().blitsThisFrame++; }

    public void noteClearPass() { frame().clearPassesThisFrame++; }

    public void noteMaterializedClearPass() { frame().materializedClearPassesThisFrame++; }

    public long beginRenderPass(SDL_GPUColorTargetInfo.Buffer colorTargets, SDL_GPUDepthStencilTargetInfo depthTarget) {
        final FrameState f = frame();
        endActiveEncoders(f);
        if (preRenderPassHook != null) preRenderPassHook.run();
        assertNoEncoderActive(f, "SDL_BeginGPURenderPass");
        f.renderPass = SDL_BeginGPURenderPass(f.commandBuffer, colorTargets, depthTarget);
        if (f.renderPass == 0) {
            throw new RuntimeException("Failed to begin render pass: " + SDLError.SDL_GetError());
        }
        f.renderPassGeneration++;
        f.fboPassesThisFrame++;
        f.currentColorTarget = colorTargets != null && colorTargets.remaining() > 0 ? colorTargets.texture() : 0;
        f.currentDepthTarget = depthTarget != null ? MemoryAccess.getAddress(depthTarget.address() + SDL_GPUDepthStencilTargetInfo.TEXTURE) : 0;
        return f.renderPass;
    }

    public void endRenderPassIfActive() {
        endRenderPassIfActive(frame());
    }

    public void endRenderPassIfActive(FrameState f) {
        if (f.renderPass != 0) {
            SDL_EndGPURenderPass(f.renderPass);
            f.renderPass = 0;
            f.currentColorTarget = 0;
            f.currentDepthTarget = 0;
            f.activeLayoutHash = 0;
        }
    }

    private void endActiveEncoders(FrameState f) {
        endCopyPassIfActive(f);
        endRenderPassIfActive(f);
    }

    /** Dev-only check */
    private void assertNoEncoderActive(FrameState f, String about) {
        if (!SystemProperties.SDL_ENCODER_ASSERTIONS) return;
        if (f.renderPass != 0 || f.copyPass != 0 || f.computePassOpen) {
            LOG.error("Encoder invariant violated before {}: renderPass={} copyPass={} computePassOpen={} on CB={}", about, f.renderPass, f.copyPass, f.computePassOpen, f.commandBuffer);
            endActiveEncoders(f);
            if (SystemProperties.SDL_ENCODER_ASSERTIONS_FATAL) {
                throw new IllegalStateException("Encoder invariant violated before " + about);
            }
        }
    }

    public long ensureCopyPass() {
        final FrameState f = frame();
        if (f.copyPass != 0) {
            if (f.commandBuffer == 0 && shouldAutoSubmitPendingUpload(f)) {
                endCopyPassIfActive(f);
                flushPendingUploadCommandBuffer(f);
            } else {
                return f.copyPass;
            }
        }
        endActiveEncoders(f);
        final long cb = getCommandBuffer(f);
        if (cb == 0) return 0;
        assertNoEncoderActive(f, "SDL_BeginGPUCopyPass");
        f.copyPass = SDL_BeginGPUCopyPass(cb);
        if (Tracy.ENABLED) f.copyPassesThisFrame++;
        return f.copyPass;
    }

    public long getCopyPass() {
        return frame().copyPass;
    }

    public void setBeforeEndCopyPassCallback(Runnable callback) {
        this.beforeEndCopyPass = callback;
    }

    public void setBeforeSubmitCallback(Runnable callback) {
        this.beforeSubmit = callback;
    }

    public void setAfterPresentCallback(Runnable callback) {
        this.afterPresent = callback;
    }

    public void requestFlushOnAllRegisteredFrames() {
        for (final FrameState f : registeredFrames.snapshot()) {
            f.flushRequested = true;
        }
    }

    public void requestSyncFlushOnAllRegisteredFrames() {
        globalSyncAtNextBeginFrame = true;
        for (final FrameState f : registeredFrames.snapshot()) {
            f.flushRequested = true;
            f.syncOnNextFlush = true;
        }
    }

    public void endCopyPassIfActive() {
        endCopyPassIfActive(frame());
    }

    private void endCopyPassIfActive(FrameState f) {
        if (f.copyPass != 0) {
            if (beforeEndCopyPass != null) beforeEndCopyPass.run();
            SDL_EndGPUCopyPass(f.copyPass);
            f.copyPass = 0;
        }
    }

    public void endFrame() {
        final FrameState f = frame();
        if (!f.frameActive) return;

        if (beforeSubmit != null) beforeSubmit.run();

        endCopyPassIfActive(f);
        endRenderPassIfActive(f);

        if (f.commandBuffer != 0) {
            Tracy.beginZone(Z_SDL_SUBMIT);
            try {
                if (f.wantFenceOnNextSubmit) {
                    f.wantFenceOnNextSubmit = false;
                    if (f.lastAcquiredFence != 0) SDL_ReleaseGPUFence(device.getDevice(), f.lastAcquiredFence);
                    f.lastAcquiredFence = SDL_SubmitGPUCommandBufferAndAcquireFence(f.commandBuffer);
                    if (f.lastAcquiredFence == 0) {
                        device.reportGpuFailure("submit+acquireFence GPU command buffer");
                    }
                } else if (!SDL_SubmitGPUCommandBuffer(f.commandBuffer)) {
                    device.reportGpuFailure("submit GPU command buffer");
                }
            } finally {
                Tracy.endZone();
            }
            f.submitsThisFrame++;
            f.commandBuffer = 0;
        }

        drainWindowPresentCounters(f);

        if (Tracy.ENABLED && resourceManager != null) {
            SdlFramePlots.emit(f, resourceManager);
        }

        f.midFrameSubmitsThisFrame = 0;

        f.lastPlottedRenderPassGen = f.renderPassGeneration;
        f.computePassesThisFrame = 0;
        f.submitsThisFrame = 0;
        f.uniformPushBytesThisFrame = 0;
        f.uniformPushesThisFrame = 0;
        f.uniformPushSkipsThisFrame = 0;
        f.externalUboPushesThisFrame = 0;
        f.externalUboPushSkipsThisFrame = 0;
        f.persistentDrainsThisFrame = 0;
        Arrays.fill(f.uniformBlockFlushesThisFrame, 0);
        Arrays.fill(f.uniformBlockBytesThisFrame, 0L);
        f.perFrameBlockPassBreaksThisFrame = 0;
        f.batchBreakPipelineThisFrame = 0;
        f.batchBreakSamplerThisFrame = 0;
        f.stateAppliesThisFrame = 0;
        f.acquireWaitNanosThisFrame = 0;
        f.mipGensThisFrame = 0;
        f.mipGenSubmitsThisFrame = 0;
        f.uploadFlushSubmitsThisFrame = 0;
        f.blitsThisFrame = 0;
        f.clearPassesThisFrame = 0;
        f.swapchainPassesThisFrame = 0;
        f.fboPassesThisFrame = 0;
        f.arenaSubmitsThisFrame = 0;
        f.arenaOverflowFlushesThisFrame = 0;
        f.copyPassesThisFrame = 0;
        f.materializedClearPassesThisFrame = 0;
        f.presentSkipsThisFrame = 0;
        f.emptyFramesThisFrame = 0;
        f.droppedDrawsThisFrame = 0;
        f.computeBatchJoinsThisFrame = 0;

        f.frameActive = false;
    }

    public void submitMidFrame() {
        final FrameState f = frame();
        if (f.commandBuffer == 0) return;
        endCopyPassIfActive(f);
        endRenderPassIfActive(f);
        Tracy.beginZone(Z_SDL_SUBMIT);
        try {
            if (f.wantFenceOnNextSubmit) {
                f.wantFenceOnNextSubmit = false;
                if (f.lastAcquiredFence != 0) SDL_ReleaseGPUFence(device.getDevice(), f.lastAcquiredFence);
                f.lastAcquiredFence = SDL_SubmitGPUCommandBufferAndAcquireFence(f.commandBuffer);
                if (f.lastAcquiredFence == 0) {
                    device.reportGpuFailure("mid-frame submit+acquireFence");
                }
            } else if (!SDL_SubmitGPUCommandBuffer(f.commandBuffer)) {
                device.reportGpuFailure("mid-frame submit");
            }
        } finally {
            Tracy.endZone();
        }
        f.submitsThisFrame++;
        f.midFrameSubmitsThisFrame++;
        f.commandBuffer = 0;
        if (f.frameActive) {
            reacquireCommandBuffer();
        }
    }

    public void presentFrame() {
        final FrameState f = frame();
        if (f.frameActive) {
            endFrame();
        }
        presentFinalTarget();
        beginFrame();
    }

    public long getCommandBuffer() {
        return getCommandBuffer(frame());
    }

    public static void warnDroppedOutsideFrame(boolean alreadyWarned, String operation) {
        if (alreadyWarned) return;
        LOG.warn("{} was issued with no live command buffer and has been dropped; the caller runs after the frame was " + "submitted (thread={})", operation, Thread.currentThread().getName(), new Throwable("call site"));
    }

    public long getCommandBuffer(FrameState f) {
        if (f.flushRequested) {
            if (f.commandBuffer == 0 && f.pendingUploadCommandBuffer != 0) {
                endCopyPassIfActive(f);
                flushPendingUploadCommandBuffer(f);
            }
            f.flushRequested = false;
        }
        if (f.commandBuffer != 0) return f.commandBuffer;
        return getOrCreatePendingUploadCommandBuffer(f);
    }

    public void recordUploadCommands(long bytes, int commands) {
        final FrameState f = frame();
        f.pendingUploadBytes += bytes;
        f.pendingUploadCommands += commands;
    }

    public boolean shouldAutoSubmitPendingUpload() {
        return shouldAutoSubmitPendingUpload(frame());
    }

    private boolean shouldAutoSubmitPendingUpload(FrameState f) {
        return f.pendingUploadBytes >= MAX_PENDING_UPLOAD_BYTES || f.pendingUploadCommands >= MAX_PENDING_UPLOAD_COMMANDS;
    }

    private long getOrCreatePendingUploadCommandBuffer(FrameState f) {
        if (f.pendingUploadCommandBuffer == 0) {
            f.pendingUploadCommandBuffer = SDL_AcquireGPUCommandBuffer(device.getDevice());
            if (f.pendingUploadCommandBuffer == 0) {
                LOG.error("Failed to acquire pending upload command buffer: {}", SDLError.SDL_GetError());
            }
        }
        return f.pendingUploadCommandBuffer;
    }

    public void flushPendingUploadCommandBuffer() {
        flushPendingUploadCommandBuffer(frame());
    }

    private void flushPendingUploadCommandBuffer(FrameState f) {
        final boolean sync = f.syncOnNextFlush;
        if (f.pendingUploadCommandBuffer == 0) {
            if (sync) {
                f.syncOnNextFlush = false;
                Tracy.beginZone(Z_SDL_FENCE_WAIT);
                try { SDL_WaitForGPUIdle(device.getDevice()); }
                finally { Tracy.endZone(); }
            }
            return;
        }
        endCopyPassIfActive(f);
        if (f.pendingUploadCommandBuffer != 0) {
            Tracy.beginZone(Z_SDL_SUBMIT);
            final boolean submitted;
            try {
                submitted = SDL_SubmitGPUCommandBuffer(f.pendingUploadCommandBuffer);
            } finally {
                Tracy.endZone();
            }
            if (!submitted) {
                device.reportGpuFailure("submit pending upload command buffer");
            }
            f.submitsThisFrame++;
            f.uploadFlushSubmitsThisFrame++;
            f.pendingUploadCommandBuffer = 0;
            f.pendingUploadBytes = 0;
            f.pendingUploadCommands = 0;
        }
        if (sync) {
            f.syncOnNextFlush = false;
            Tracy.beginZone(Z_SDL_FENCE_WAIT);
            try { SDL_WaitForGPUIdle(device.getDevice()); }
            finally { Tracy.endZone(); }
        }
    }

    public long getRenderPass() {
        return frame().renderPass;
    }

    public long getRenderPassGeneration() {
        return frame().renderPassGeneration;
    }

    public long getRenderPass(FrameState f) { return f.renderPass; }

    public long getRenderPassGeneration(FrameState f) { return f.renderPassGeneration; }

    public boolean isFrameActive(FrameState f) { return f.frameActive; }

    public boolean isRenderPassActive(FrameState f) { return f.renderPass != 0; }

    public long getCurrentColorTarget(FrameState f) { return f.currentColorTarget; }

    public long getCurrentDepthTarget(FrameState f) { return f.currentDepthTarget; }

    public long getActiveLayoutHash(FrameState f) { return f.activeLayoutHash; }

    public long getFbo0Texture() {
        return finalTarget.colorTexture();
    }

    public int getFbo0Width() {
        return finalTarget.width();
    }

    public int getFbo0Height() {
        return finalTarget.height();
    }

    public int getSwapchainFormat() {
        return device.getSwapchainTextureFormat();
    }

    public boolean isFrameActive() {
        return frame().frameActive;
    }

    public boolean isRenderPassActive() {
        return frame().renderPass != 0;
    }

    public long getCurrentColorTarget() {
        return frame().currentColorTarget;
    }

    public long getCurrentDepthTarget() {
        return frame().currentDepthTarget;
    }

    public long getActiveLayoutHash() {
        return frame().activeLayoutHash;
    }

    public void setActiveLayoutHash(long h) {
        frame().activeLayoutHash = h;
    }

    public long getFrameNumber() {
        return frame().frameNumber;
    }

    public boolean isFbo0UsedThisFrame() {
        return frame().fbo0UsedThisFrame;
    }

    public void reacquireCommandBuffer() {
        final FrameState f = frame();
        flushPendingUploadCommandBuffer(f);
        f.copyPass = 0;
        f.commandBuffer = SDL_AcquireGPUCommandBuffer(device.getDevice());
        f.renderPass = 0;
        f.currentColorTarget = 0;
        f.currentDepthTarget = 0;

        if (f.frameActive) {
            f.clearedThisFrame = false;
        }
    }

    public void releaseThreadState() {
        final FrameState f = tlFrame.get();
        if (f == null) return;
        if (resourceManager != null) resourceManager.flushUploadArena(f);
        flushPendingUploadCommandBuffer(f);
        releaseFrameStateResources(f);
        tlFrame.remove();
        registeredFrames.remove(f);
    }

    private void releaseFrameStateResources(FrameState f) {
        if (f.batchXfer != 0) {
            if (f.batchMapped != null) {
                SDL_UnmapGPUTransferBuffer(device.getDevice(), f.batchXfer);
                f.batchMapped = null;
            }
            resourceManager.releaseTransferBufferHandle(f.batchXfer);
            f.batchXfer = 0;
        }
        if (f.arenaXfer != 0) {
            if (f.arenaMapped != null) {
                SDL_UnmapGPUTransferBuffer(device.getDevice(), f.arenaXfer);
                f.arenaMapped = null;
            }
            resourceManager.releaseTransferBufferHandle(f.arenaXfer);
            f.arenaXfer = 0;
        }
        f.arena.reset();
        f.pendingTexHandles.clear();
        f.pendingTexX.clear();
        f.pendingTexY.clear();
        f.pendingTexW.clear();
        f.pendingTexH.clear();
        f.pendingTexLevels.clear();
        f.pendingTexOffsets.clear();
        f.uboPushViews.clear();
        f.uboPushViewSource.clear();

        if (f.lastAcquiredFence != 0) {
            SDL_ReleaseGPUFence(device.getDevice(), f.lastAcquiredFence);
            f.lastAcquiredFence = 0;
        }
    }

    public void releaseAllRegisteredFrames() {
        for (final FrameState f : registeredFrames.snapshot()) {
            releaseFrameStateResources(f);
        }
        registeredFrames.clear();
    }

    public long lastFrameGateNanos() {
        return frame().lastFrameGateNanos;
    }

    public long lastFrameGateEndNanos() {
        return frame().lastFrameGateEndNanos;
    }

    private Presenter presenter;

    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    private final FrameState windowFrame = new FrameState();

    public FrameState windowFrame() {
        return windowFrame;
    }

    public void presentFinalTarget() {
        if (!finalTarget.hasContent()) return;
        final long texture = finalTarget.colorTexture();
        final int width = finalTarget.width();
        final int height = finalTarget.height();
        final Presenter p = presenter;
        if (p != null && p.isEngaged()) {
            final FrameState f = frame();
            if (f.presentedThisFrame) return;
            f.presentedThisFrame = true;
            p.requestPresent(texture, width, height);
        } else {
            presentBlit(frame(), texture, width, height, SDL_FLIP_NONE);
        }
    }

    private final AtomicLong windowAcquireWaitNanos = new AtomicLong();
    private final AtomicInteger windowPresentSkips = new AtomicInteger();

    private void drainWindowPresentCounters(FrameState f) {
        if (f == windowFrame) return;
        final long acquire = windowAcquireWaitNanos.getAndSet(0L);
        if (acquire != 0L) {
            f.acquireWaitNanosThisFrame += acquire;
            f.lastFrameGateNanos = windowFrame.lastFrameGateNanos;
            f.lastFrameGateEndNanos = windowFrame.lastFrameGateEndNanos;
        }
        f.presentSkipsThisFrame += windowPresentSkips.getAndSet(0);
    }

    private void noteAcquireWait(FrameState f, long waitNanos) {
        if (f == windowFrame) windowAcquireWaitNanos.addAndGet(waitNanos);
        else f.acquireWaitNanosThisFrame += waitNanos;
    }

    private void notePresentSkip(FrameState f) {
        if (f == windowFrame) windowPresentSkips.incrementAndGet();
        else f.presentSkipsThisFrame++;
    }

    public void presentOnWindowThread(long srcTexture, int srcW, int srcH) {
        presentBlit(windowFrame, srcTexture, srcW, srcH, SDL_FLIP_NONE);
        windowFrame.presentedThisFrame = false;
        windowFrame.swapchainUnavailable = false;
    }

    public boolean presentBlit(FrameState f, long srcTexture, int srcW, int srcH, int flipMode) {
        if (srcTexture == 0 || srcW <= 0 || srcH <= 0) return false;
        if (f.presentedThisFrame || f.swapchainUnavailable) return false;

        if (!f.windowThreadChecked) {
            f.windowThreadChecked = true;
            final Thread self = Thread.currentThread();
            final Thread windowThread = device.getWindowThread();
            if (windowThread != null && windowThread != self) {
                LOG.warn("Swapchain acquired on '{}' but the SDL window was created on '{}'; SDL requires acquire on the window-creating thread", self.getName(), windowThread.getName());
            } else {
                LOG.info("First swapchain acquire on thread={}", self.getName());
            }
        }

        final long window = device.getClaimedWindow();
        if (window == 0) return false;

        final long cb = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (cb == 0) return false;

        final long t0 = System.nanoTime();
        final long tex;
        final int w;
        final int h;
        final boolean callOk;
        try (var stack = stackPush()) {
            final PointerBuffer pTexture = stack.pointers(0);
            final IntBuffer pWidth = stack.ints(0);
            final IntBuffer pHeight = stack.ints(0);
            Tracy.beginZone(Z_SDL_ACQUIRE_WAIT);
            try {
                callOk = SDL_WaitAndAcquireGPUSwapchainTexture(cb, window, pTexture, pWidth, pHeight);
            } finally {
                Tracy.endZone();
            }
            tex = pTexture.get(0);
            w = pWidth.get(0);
            h = pHeight.get(0);
        } catch (RuntimeException re) {
            SDL_CancelGPUCommandBuffer(cb);
            f.swapchainUnavailable = true;
            throw re;
        }
        final long tEnd = System.nanoTime();
        f.lastFrameGateNanos = tEnd - t0;
        f.lastFrameGateEndNanos = tEnd;
        noteAcquireWait(f, tEnd - t0);

        if (!callOk || tex == 0) {
            SDL_CancelGPUCommandBuffer(cb);
            f.swapchainUnavailable = true;
            notePresentSkip(f);
            return false;
        }

        try (MemoryStack stack = stackPush()) {
            final long info = stack.ncalloc(SDL_GPUBlitInfo.ALIGNOF, 1, SDL_GPUBlitInfo.SIZEOF);
            writeBlitRegion(info + SDL_GPUBlitInfo.SOURCE, srcTexture, srcW, srcH);
            writeBlitRegion(info + SDL_GPUBlitInfo.DESTINATION, tex, w, h);
            MemoryAccess.putInt(info + SDL_GPUBlitInfo.FILTER, SDL_GPU_FILTER_LINEAR);
            MemoryAccess.putInt(info + SDL_GPUBlitInfo.FLIP_MODE, flipMode);
            nSDL_BlitGPUTexture(cb, info);
        }

        if (!SDL_SubmitGPUCommandBuffer(cb)) {
            device.reportGpuFailure("submit present blit");
            return false;
        }
        f.presentedThisFrame = true;
        if (afterPresent != null) afterPresent.run();
        return true;
    }

    private static void writeBlitRegion(long region, long texture, int w, int h) {
        MemoryAccess.putAddress(region + SDL_GPUBlitRegion.TEXTURE, texture);
        MemoryAccess.putInt(region + SDL_GPUBlitRegion.W, w);
        MemoryAccess.putInt(region + SDL_GPUBlitRegion.H, h);
    }
}
