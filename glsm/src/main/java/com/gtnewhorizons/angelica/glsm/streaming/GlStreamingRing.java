package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Persistent-mapped fence-reclaimed ring over any buffer target. Callers reserve a range and
 * memCopy into address() + offset themselves.
 */
final class GlStreamingRing {

    private final int target;
    private final int capacity;
    private final ByteBuffer mappedBuffer;
    private final long mappedAddress;
    private int bufferId;

    private static final long SYNC_WAIT_SLICE_NANOS = 10_000_000L;
    private static final long SYNC_WAIT_TOTAL_NANOS = 5_000_000_000L;

    private final ObjectArrayFIFOQueue<FencedRegion> fenceQueue = new ObjectArrayFIFOQueue<>();
    private int writePos;
    private int remaining;
    private int pendingBytes;
    private int wraps;
    private int fencesIssued;
    private int forcedReclaims;

    private GlStreamingRing(int target, int capacity, int bufferId, ByteBuffer mappedBuffer) {
        this.target = target;
        this.capacity = capacity;
        this.bufferId = bufferId;
        this.mappedBuffer = mappedBuffer;
        this.mappedAddress = memAddress0(mappedBuffer);
        this.remaining = capacity;
    }

    static GlStreamingRing create(int target, int capacity) {
        return create(target, capacity, SystemProperties.FORCE_ORPHAN_STREAMING);
    }

    static GlStreamingRing create(int target, int capacity, boolean forceOrphan) {
        if (forceOrphan || !RenderSystem.supportsBufferStorage()) return null;
        final int id = RENDER_BACKEND.genBuffers();
        RENDER_BACKEND.bindBuffer(target, id);
        final int storageFlags = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_COHERENT_BIT | GL44.GL_CLIENT_STORAGE_BIT;
        RenderSystem.bufferStorage(target, capacity, storageFlags);
        final int mapFlags = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_COHERENT_BIT;
        final ByteBuffer mapped = RENDER_BACKEND.mapBufferRange(target, 0, capacity, mapFlags);
        RENDER_BACKEND.bindBuffer(target, 0);
        if (mapped == null) {
            GLStateManager.glDeleteBuffers(id);
            GLStateManager.LOGGER.warn("Persistent map failed for streaming ring (target=0x{}, {} bytes)", Integer.toHexString(target), capacity);
            return null;
        }
        GLStateManager.LOGGER.info("Streaming ring created (target=0x{}, {}KB)", Integer.toHexString(target), capacity / 1024);
        return new GlStreamingRing(target, capacity, id, mapped);
    }

    /** Byte offset of a reserved range, or -1 when it can never be served. */
    long reserve(int bytes, int alignment) {
        if (bytes > capacity) return -1;
        if (remaining < capacity / 2) {
            reclaim();
        }
        int offset = (writePos + alignment - 1) / alignment * alignment;
        int waste = offset - writePos;
        if (offset + bytes > capacity) {
            waste = capacity - writePos;
            offset = 0;
            wraps++;
        }
        if (!ensureRemaining(waste + bytes)) return -1;
        writePos = offset + bytes;
        remaining -= waste + bytes;
        pendingBytes += waste + bytes;
        if (pendingBytes >= capacity / 4) {
            fence();
        }
        return offset;
    }

    private boolean ensureRemaining(int needed) {
        while (remaining < needed) {
            if (fenceQueue.isEmpty()) {
                if (pendingBytes == 0) return false;
                fence();
            }
            syncOldest();
        }
        return true;
    }

    private void fence() {
        if (pendingBytes > 0) {
            fenceQueue.enqueue(new FencedRegion(RENDER_BACKEND.fenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0), pendingBytes));
            pendingBytes = 0;
            fencesIssued++;
        }
    }

    private static boolean signaled(int status) {
        return status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED;
    }

    private void reclaim() {
        while (!fenceQueue.isEmpty()) {
            final FencedRegion region = fenceQueue.first();
            if (!signaled(RENDER_BACKEND.clientWaitSync(region.fenceId, 0, 0L))) break;
            GLStateManager.glDeleteSync(region.fenceId);
            fenceQueue.dequeue();
            remaining += region.bytes;
        }
    }

    private void syncOldest() {
        final FencedRegion region = fenceQueue.dequeue();
        forcedReclaims++;
        final long deadline = System.nanoTime() + SYNC_WAIT_TOTAL_NANOS;
        long remainingNanos = SYNC_WAIT_TOTAL_NANOS;
        int status;
        do {
            status = RENDER_BACKEND.clientWaitSync(region.fenceId, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, Math.min(SYNC_WAIT_SLICE_NANOS, remainingNanos));
            remainingNanos = deadline - System.nanoTime();
        } while (status == GL32.GL_TIMEOUT_EXPIRED && remainingNanos > 0);
        if (!signaled(status)) {
            GLStateManager.LOGGER.warn("Streaming ring fence wait did not signal (status=0x{}); reclaiming anyway", Integer.toHexString(status));
        }
        GLStateManager.glDeleteSync(region.fenceId);
        remaining += region.bytes;
        reclaim();
    }

    void endFrame() {
        fence();
        reclaim();
    }

    long address() { return mappedAddress; }
    ByteBuffer mapped() { return mappedBuffer; }
    int bufferId() { return bufferId; }
    int capacity() { return capacity; }
    int remaining() { return remaining; }
    int wraps() { return wraps; }
    int fencesIssued() { return fencesIssued; }
    int forcedReclaims() { return forcedReclaims; }

    void destroy() {
        while (!fenceQueue.isEmpty()) {
            GLStateManager.glDeleteSync(fenceQueue.dequeue().fenceId);
        }
        if (bufferId != 0) {
            RENDER_BACKEND.bindBuffer(target, bufferId);
            RENDER_BACKEND.unmapBuffer(target);
            RENDER_BACKEND.bindBuffer(target, 0);
            GLStateManager.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }

    private record FencedRegion(long fenceId, int bytes) {}
}
