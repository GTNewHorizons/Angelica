package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDL_GPUBufferLocation;
import org.lwjgl.sdl.SDL_GPUBufferRegion;
import org.lwjgl.sdl.SDL_GPUTextureRegion;
import org.lwjgl.sdl.SDL_GPUTextureTransferInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferLocation;
import org.lwjgl.sdl.SDLError;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.device.GpuDeviceLostException;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLGPU.*;

public final class TransferThread {
    private static final long P_TRANSFER_BUSY_PCT = Tracy.plotHandle("sdl.transferBusyPct", TracyBackend.PLOT_FORMAT_PERCENTAGE);
    private static final long P_TRANSFER_BYTES = Tracy.plotHandle("sdl.transferBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_TRANSFER_SUBMITS = Tracy.plotHandle("sdl.transferSubmits");
    private static final long P_TRANSFER_WAIT_US = Tracy.plotHandle("sdl.transferWaitUs");
    private static final long P_TRANSFER_UNPARKS_SENT = Tracy.plotHandle("sdl.transferUnparksSent");
    private static final long P_TRANSFER_UNPARKS_ELIDED = Tracy.plotHandle("sdl.transferUnparksElided");
    private static final Tracy.ZoneId Z_SDL_UPLOAD = Tracy.zoneId("sdlUpload", Tracy.COLOR_WORKER);

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU-Transfer");
    private boolean warnedNullTextureUpload;

    private static final AtomicLong SEQ = new AtomicLong();

    public static long nextSeq() { return SEQ.incrementAndGet(); }
    public static long currentSeq() { return SEQ.get(); }

    private final Device device;
    private final ResourceManager resourceManager;
    private final Thread thread;
    private final ConcurrentLinkedQueue<DeferredUpload> queue = new ConcurrentLinkedQueue<>();
    private volatile long submittedSeq;
    private volatile long flushRequestedSeq;
    private volatile boolean shutdown;
    private volatile boolean parked;
    private volatile long unparksSent;
    private volatile long unparksElided;
    private long lastUnparksSent;
    private long lastUnparksElided;

    // Debug stats
    private int frameUploads;
    private long frameBytes;
    private int frameCBSubmits;
    private long maxWaitNs;
    private long totalCBSubmits;
    private long totalBytes;
    private int pendingFrameUploads;
    private long pendingFrameBytes;
    private int pendingFrameCBSubmits;
    private long pendingActiveNanos;
    private long lastStatsResetNanos;
    private volatile long pendingFrameWaitNanos;

    private static final long FLUSH_COALESCE_BYTES = 8L * 1024 * 1024;
    private static final int FLUSH_COALESCE_COMMANDS = 128;
    private static final long WAKE_COALESCE_ENQUEUES = 16;

    private long openCB;
    private long openCopyPass;
    private long openHighestSeq;
    private long openBytes;
    private int openCount;
    private final LongArrayList pendingReturnHandles = new LongArrayList();
    private final LongArrayList pendingReturnSizes = new LongArrayList();

    private final SDL_GPUTransferBufferLocation srcLoc = SDL_GPUTransferBufferLocation.calloc();
    private final SDL_GPUBufferRegion dstRegion = SDL_GPUBufferRegion.calloc();
    private final SDL_GPUBufferLocation srcBufLoc = SDL_GPUBufferLocation.calloc();
    private final SDL_GPUBufferLocation dstBufLoc = SDL_GPUBufferLocation.calloc();
    private final SDL_GPUTextureTransferInfo texXferInfo = SDL_GPUTextureTransferInfo.calloc();
    private final SDL_GPUTextureRegion texRegion = SDL_GPUTextureRegion.calloc();

    private static final ConcurrentLinkedQueue<PendingFree> FREE_POOL = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PendingFree> pendingFrees = new ConcurrentLinkedQueue<>();

    public TransferThread(Device device, ResourceManager resourceManager) {
        this.device = device;
        this.resourceManager = resourceManager;
        this.thread = new Thread(this::run, "SDL-GPU-Transfer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public Thread getThread() { return thread; }

    public long getSubmittedSeq() { return submittedSeq; }

    public void enqueue(DeferredUpload upload) {
        queue.offer(upload);
        if (shouldWakeOnEnqueue(upload.seq())) wake();
    }

    static boolean shouldWakeOnEnqueue(long seq) {
        return (seq & (WAKE_COALESCE_ENQUEUES - 1)) == 0;
    }

    public void wake() {
        if (parked) {
            unparksSent++;
            LockSupport.unpark(thread);
        } else {
            unparksElided++;
        }
    }

    public void freeAfterSeq(ByteBuffer buf, long seq) {
        if (buf == null) return;
        if (seq <= submittedSeq) { MemoryUtil.memFree(buf); return; }
        PendingFree pf = FREE_POOL.poll();
        if (pf == null) pf = new PendingFree();
        pf.seq = seq;
        pf.buf = buf;
        pendingFrees.offer(pf);
        wake();
    }

    private void drainDuePendingFrees() {
        final long sub = submittedSeq;
        PendingFree head = pendingFrees.peek();
        while (head != null && head.seq <= sub) {
            pendingFrees.poll();
            MemoryUtil.memFree(head.buf);
            head.buf = null;
            FREE_POOL.offer(head);
            head = pendingFrees.peek();
        }
    }

    private void drainAllPendingFreesOnShutdown() {
        PendingFree pf;
        while ((pf = pendingFrees.poll()) != null) {
            MemoryUtil.memFree(pf.buf);
            pf.buf = null;
            FREE_POOL.offer(pf);
        }
    }

    private final Object submittedLock = new Object();

    public void requestFlushUpTo(long seq) {
        if (seq <= submittedSeq || seq <= flushRequestedSeq) return;
        flushRequestedSeq = seq;
        LockSupport.unpark(thread);
    }

    public void awaitSubmittedUpTo(long seq) {
        if (submittedSeq >= seq) return;
        if (seq > flushRequestedSeq) flushRequestedSeq = seq;
        LockSupport.unpark(thread);
        final long startNs = System.nanoTime();
        synchronized (submittedLock) {
            while (submittedSeq < seq) {
                try {
                    submittedLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        final long waitNs = System.nanoTime() - startNs;
        if (waitNs > maxWaitNs) maxWaitNs = waitNs;
        pendingFrameWaitNanos += waitNs;
    }

    public void shutdown() {
        shutdown = true;
        LockSupport.unpark(thread);
    }

    public void resetFrameStats() {
        frameUploads = pendingFrameUploads;
        frameBytes = pendingFrameBytes;
        frameCBSubmits = pendingFrameCBSubmits;
        if (Tracy.ENABLED) {
            final long now = System.nanoTime();
            final long wall = now - lastStatsResetNanos;
            lastStatsResetNanos = now;
            Tracy.plotInt(P_TRANSFER_BUSY_PCT, wall > 0 ? Math.min(100, pendingActiveNanos * 100 / wall) : 0);
            Tracy.plotInt(P_TRANSFER_BYTES, pendingFrameBytes);
            Tracy.plotInt(P_TRANSFER_SUBMITS, pendingFrameCBSubmits);
            Tracy.plotInt(P_TRANSFER_WAIT_US, pendingFrameWaitNanos / 1000);
            Tracy.plotInt(P_TRANSFER_UNPARKS_SENT, unparksSent - lastUnparksSent);
            Tracy.plotInt(P_TRANSFER_UNPARKS_ELIDED, unparksElided - lastUnparksElided);
            lastUnparksSent = unparksSent;
            lastUnparksElided = unparksElided;
        }
        pendingFrameWaitNanos = 0;
        pendingFrameUploads = 0;
        pendingFrameBytes = 0;
        pendingFrameCBSubmits = 0;
        pendingActiveNanos = 0;
        maxWaitNs = 0;
    }

    static boolean shouldFlush(boolean hasOpenCB, long flushRequestedSeq, long submittedSeq, long openBytes, int openCount) {
        if (!hasOpenCB) return false;
        return flushRequestedSeq > submittedSeq || openBytes >= FLUSH_COALESCE_BYTES || openCount >= FLUSH_COALESCE_COMMANDS;
    }

    static boolean shouldPublishRetired(boolean hasOpenCB, long openHighestSeq, long submittedSeq) {
        return !hasOpenCB && openHighestSeq > submittedSeq;
    }

    private void publishSubmittedSeq() {
        if (openHighestSeq <= submittedSeq) return;
        synchronized (submittedLock) {
            submittedSeq = openHighestSeq;
            submittedLock.notifyAll();
        }
        drainDuePendingFrees();
    }

    private void run() {
        LOG.info("Transfer thread started");
        try {
            while (!shutdown) {
                final long workStart = Tracy.ENABLED ? System.nanoTime() : 0L;
                // Drain all pending uploads
                DeferredUpload upload = queue.poll();
                if (upload != null) {
                    do {
                        processOne(upload);
                        upload = queue.poll();
                    } while (upload != null);
                }

                if (shouldFlush(openCB != 0, flushRequestedSeq, submittedSeq, openBytes, openCount)) {
                    flushOpenCB();
                } else if (shouldPublishRetired(openCB != 0, openHighestSeq, submittedSeq)) {
                    publishSubmittedSeq();
                    openHighestSeq = 0;
                }

                if (Tracy.ENABLED) pendingActiveNanos += System.nanoTime() - workStart;

                if (queue.isEmpty() && !shutdown) {
                    parked = true;
                    if (queue.isEmpty() && !shutdown && flushRequestedSeq <= submittedSeq) {
                        LockSupport.parkNanos(1_000_000L); // 1ms
                    }
                    parked = false;
                }
            }
            if (openCB != 0) flushOpenCB();
        } catch (GpuDeviceLostException e) {
            LOG.error("Transfer thread stopping: {}", e.getMessage());
        } finally {
            synchronized (submittedLock) {
                submittedSeq = Long.MAX_VALUE;
                submittedLock.notifyAll();
            }
        }
        drainAllPendingFreesOnShutdown();
        srcLoc.free();
        dstRegion.free();
        srcBufLoc.free();
        dstBufLoc.free();
        texXferInfo.free();
        texRegion.free();
        LOG.info("Transfer thread stopped");
    }

    private void processOne(DeferredUpload upload) {
        Tracy.beginZone(Z_SDL_UPLOAD);
        try {
            processOneInner(upload);
        } finally {
            Tracy.endZone();
        }
    }

    private void processOneInner(DeferredUpload upload) {
        if (upload instanceof StagingReadUpload sr) {
            final long size = sr.size;
            final long xfer = resourceManager.acquireTransferBufferThreadSafe(size);
            if (xfer == 0) {
                LOG.error("Failed to acquire transfer buffer for StagingRead (size {})", size);
                StagingReadUpload.release(sr);
                return;
            }
            final long mapSize = resourceManager.getTransferBufferMapSize(size);
            final ByteBuffer mapped = SDL_MapGPUTransferBuffer(device.getDevice(), xfer, true, mapSize);
            if (mapped != null) {
                final ByteBuffer src = sr.stagingBuffer;
                final int prevPos = src.position();
                final int prevLim = src.limit();
                src.limit((int) (sr.srcOffset + size)).position((int) sr.srcOffset);
                mapped.put(src);
                src.limit(prevLim).position(prevPos);
            }
            SDL_UnmapGPUTransferBuffer(device.getDevice(), xfer);
            recordBufferUpload(xfer, size, sr.dstGpuBuffer, sr.dstOffset, sr.seq, sr.cycle);
            StagingReadUpload.release(sr);
        } else if (upload instanceof PreCopiedUpload pc) {
            recordBufferUpload(pc.transferBuffer, pc.size, pc.dstGpuBuffer, pc.dstOffset, pc.seq, pc.cycle);
            PreCopiedUpload.release(pc);
        } else if (upload instanceof GpuCopyUpload gc) {
            recordGpuCopy(gc.srcHandle, gc.dstHandle, gc.readOffset, gc.writeOffset, gc.size, gc.seq);
            GpuCopyUpload.release(gc);
        } else if (upload instanceof TextureRegionUpload tr) {
            recordTextureUpload(tr.transferBuffer, tr.texHandle, tr.x, tr.y, tr.w, tr.h, tr.level, tr.size, tr.seq);
            TextureRegionUpload.release(tr);
        }
    }

    private boolean ensureOpenCB() {
        if (openCB != 0) return true;
        openCB = SDL_AcquireGPUCommandBuffer(device.getDevice());
        if (openCB == 0) {
            LOG.error("Failed to acquire GPU command buffer: {}", SDLError.SDL_GetError());
            return false;
        }
        openCopyPass = SDL_BeginGPUCopyPass(openCB);
        return true;
    }

    private void retireSeq(long seq) {
        openHighestSeq = Math.max(openHighestSeq, seq);
    }

    private void recordBufferUpload(long transferBuffer, long size, long dstGpuBuffer, long dstOffset, long seq, boolean cycle) {
        if (transferBuffer == 0) {
            retireSeq(seq);
            return;
        }
        if (!ensureOpenCB()) {
            resourceManager.returnTransferBufferThreadSafe(transferBuffer, size);
            retireSeq(seq);
            return;
        }
        srcLoc.transfer_buffer(transferBuffer).offset(0);
        dstRegion.buffer(dstGpuBuffer).offset((int) dstOffset).size((int) size);
        SDL_UploadToGPUBuffer(openCopyPass, srcLoc, dstRegion, cycle);

        openHighestSeq = Math.max(openHighestSeq, seq);
        openBytes += size;
        openCount++;
        pendingReturnHandles.add(transferBuffer);
        pendingReturnSizes.add(size);
    }

    private void recordGpuCopy(long srcHandle, long dstHandle, long readOffset, long writeOffset, long size, long seq) {
        if (!ensureOpenCB()) {
            retireSeq(seq);
            return;
        }
        srcBufLoc.buffer(srcHandle).offset((int) readOffset);
        dstBufLoc.buffer(dstHandle).offset((int) writeOffset);
        SDL_CopyGPUBufferToBuffer(openCopyPass, srcBufLoc, dstBufLoc, (int) size, false);

        openHighestSeq = Math.max(openHighestSeq, seq);
        openBytes += size;
        openCount++;
    }

    private void recordTextureUpload(long transferBuffer, long texHandle, int x, int y, int w, int h, int level, long size, long seq) {
        if (transferBuffer == 0) {
            retireSeq(seq);
            return;
        }
        if (texHandle == 0) {
            if (!warnedNullTextureUpload) {
                warnedNullTextureUpload = true;
                LOG.warn("Deferred texture upload with no destination texture; dropped (level={} {}x{} at {},{})", level, w, h, x, y);
            }
            resourceManager.returnTransferBufferThreadSafe(transferBuffer, size);
            retireSeq(seq);
            return;
        }
        if (!ensureOpenCB()) {
            resourceManager.returnTransferBufferThreadSafe(transferBuffer, size);
            retireSeq(seq);
            return;
        }
        texXferInfo.transfer_buffer(transferBuffer).offset(0);
        texRegion.texture(texHandle).mip_level(level).x(x).y(y).z(0).w(w).h(h).d(1);
        SDL_UploadToGPUTexture(openCopyPass, texXferInfo, texRegion, false);

        openHighestSeq = Math.max(openHighestSeq, seq);
        openBytes += size;
        openCount++;
        pendingReturnHandles.add(transferBuffer);
        pendingReturnSizes.add(size);
    }

    private void flushOpenCB() {
        if (openCB == 0) return;

        SDL_EndGPUCopyPass(openCopyPass);
        if (!SDL_SubmitGPUCommandBuffer(openCB)) {
            device.reportGpuFailure("submit transfer command buffer");
        }

        publishSubmittedSeq();

        for (int i = 0, n = pendingReturnHandles.size(); i < n; i++) {
            resourceManager.returnTransferBufferThreadSafe(pendingReturnHandles.getLong(i), pendingReturnSizes.getLong(i));
        }
        pendingReturnHandles.clear();
        pendingReturnSizes.clear();

        pendingFrameUploads += openCount;
        pendingFrameBytes += openBytes;
        pendingFrameCBSubmits++;
        totalCBSubmits++;
        totalBytes += openBytes;

        openCB = 0;
        openCopyPass = 0;
        openHighestSeq = 0;
        openBytes = 0;
        openCount = 0;
    }

    public String getDebugInfo() {
        if (totalCBSubmits == 0) return "Transfer: idle";
        return String.format("Transfer: %d uploads, %s, %d CBs, wait %.2fms (total: %d CBs, %s), unparks %d sent/%d elided",
            frameUploads,
            formatBytes(frameBytes),
            frameCBSubmits,
            maxWaitNs / 1_000_000.0,
            totalCBSubmits,
            formatBytes(totalBytes),
            unparksSent,
            unparksElided);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private static final class PendingFree {
        long seq;
        ByteBuffer buf;
    }

    public sealed interface DeferredUpload permits StagingReadUpload, PreCopiedUpload, GpuCopyUpload, TextureRegionUpload {
        public long seq();
    }

    public static final class StagingReadUpload implements DeferredUpload {
        private static final ConcurrentLinkedQueue<StagingReadUpload> POOL = new ConcurrentLinkedQueue<>();

        public ByteBuffer stagingBuffer;
        public long srcOffset;
        public long size;
        public long dstGpuBuffer;
        public long dstOffset;
        public long seq;
        public boolean cycle;

        private StagingReadUpload() {}

        public static StagingReadUpload acquire(ByteBuffer stagingBuffer, long srcOffset, long size, long dstGpuBuffer, long dstOffset, long seq, boolean cycle) {
            StagingReadUpload obj = POOL.poll();
            if (obj == null) obj = new StagingReadUpload();
            obj.stagingBuffer = stagingBuffer;
            obj.srcOffset = srcOffset;
            obj.size = size;
            obj.dstGpuBuffer = dstGpuBuffer;
            obj.dstOffset = dstOffset;
            obj.seq = seq;
            obj.cycle = cycle;
            return obj;
        }

        public static void release(StagingReadUpload obj) {
            obj.stagingBuffer = null;
            POOL.offer(obj);
        }

        @Override public long seq() { return seq; }
    }

    public static final class PreCopiedUpload implements DeferredUpload {
        private static final ConcurrentLinkedQueue<PreCopiedUpload> POOL = new ConcurrentLinkedQueue<>();

        public long transferBuffer;
        public long size;
        public long dstGpuBuffer;
        public long dstOffset;
        public long seq;
        public boolean cycle;

        private PreCopiedUpload() {}

        public static PreCopiedUpload acquire(long transferBuffer, long size, long dstGpuBuffer, long dstOffset, long seq, boolean cycle) {
            PreCopiedUpload obj = POOL.poll();
            if (obj == null) obj = new PreCopiedUpload();
            obj.transferBuffer = transferBuffer;
            obj.size = size;
            obj.dstGpuBuffer = dstGpuBuffer;
            obj.dstOffset = dstOffset;
            obj.seq = seq;
            obj.cycle = cycle;
            return obj;
        }

        public static void release(PreCopiedUpload obj) {
            POOL.offer(obj);
        }

        @Override public long seq() { return seq; }
    }

    public static final class GpuCopyUpload implements DeferredUpload {
        private static final ConcurrentLinkedQueue<GpuCopyUpload> POOL = new ConcurrentLinkedQueue<>();

        public long srcHandle;
        public long dstHandle;
        public long readOffset;
        public long writeOffset;
        public long size;
        public long seq;

        private GpuCopyUpload() {}

        public static GpuCopyUpload acquire(long srcHandle, long dstHandle, long readOffset,
                                     long writeOffset, long size, long seq) {
            GpuCopyUpload obj = POOL.poll();
            if (obj == null) obj = new GpuCopyUpload();
            obj.srcHandle = srcHandle;
            obj.dstHandle = dstHandle;
            obj.readOffset = readOffset;
            obj.writeOffset = writeOffset;
            obj.size = size;
            obj.seq = seq;
            return obj;
        }

        public static void release(GpuCopyUpload obj) {
            POOL.offer(obj);
        }

        @Override public long seq() { return seq; }
    }

    public static final class TextureRegionUpload implements DeferredUpload {
        private static final ConcurrentLinkedQueue<TextureRegionUpload> POOL = new ConcurrentLinkedQueue<>();

        public long transferBuffer;
        public long texHandle;
        public int x;
        public int y;
        public int w;
        public int h;
        public int level;
        public long size;
        public long seq;

        private TextureRegionUpload() {}

        public static TextureRegionUpload acquire(long transferBuffer, long texHandle, int x, int y, int w, int h, int level, long size, long seq) {
            TextureRegionUpload obj = POOL.poll();
            if (obj == null) obj = new TextureRegionUpload();
            obj.transferBuffer = transferBuffer;
            obj.texHandle = texHandle;
            obj.x = x;
            obj.y = y;
            obj.w = w;
            obj.h = h;
            obj.level = level;
            obj.size = size;
            obj.seq = seq;
            return obj;
        }

        public static void release(TextureRegionUpload obj) {
            POOL.offer(obj);
        }

        @Override public long seq() { return seq; }
    }
}
