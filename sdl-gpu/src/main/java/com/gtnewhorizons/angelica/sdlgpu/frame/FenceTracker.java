package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.device.FenceWait;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.opengl.GL32;

public final class FenceTracker {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final Tracy.ZoneId Z_SDL_FENCE_WAIT = Tracy.zoneId("sdlFenceWait", Tracy.COLOR_SWAP);

    private final Device device;
    private final FrameManager frameManager;

    private long nextFenceId = 1;
    private final Long2LongOpenHashMap fenceMap = new Long2LongOpenHashMap();
    private final LongArrayList pendingFenceSyncs = new LongArrayList();
    private final Long2IntOpenHashMap fenceRefcounts = new Long2IntOpenHashMap();

    private Runnable unresolvedFenceFlush;
    private int unresolvedFlushes;
    private long unresolvedFlushSync;

    public FenceTracker(Device device, FrameManager frameManager) {
        this.device = device;
        this.frameManager = frameManager;
    }

    public void setUnresolvedFenceFlush(Runnable flush) {
        this.unresolvedFenceFlush = flush;
    }

    public int getUnresolvedFlushes() { return unresolvedFlushes; }

    private long flushForUnresolved(long sync) {
        if (unresolvedFenceFlush == null) return 0L;
        if (unresolvedFlushSync == sync) return 0L;
        unresolvedFlushSync = sync;
        unresolvedFlushes++;
        if ((unresolvedFlushes & (unresolvedFlushes - 1)) == 0) {
            LOG.warn("clientWaitSync on an unsubmitted fence forced a mid-frame flush (#{}, thread={})", unresolvedFlushes, Thread.currentThread().getName());
        }
        unresolvedFenceFlush.run();
        return fenceMap.get(sync);
    }

    public long fenceSync() {
        final long id = nextFenceId++;
        fenceMap.put(id, 0L);
        pendingFenceSyncs.add(id);
        frameManager.frame().wantFenceOnNextSubmit = true;
        return id;
    }

    public void resolvePendingFences() {
        unresolvedFlushSync = 0L;
        if (pendingFenceSyncs.isEmpty()) return;
        final long fence = frameManager.frame().lastAcquiredFence;
        frameManager.frame().lastAcquiredFence = 0;
        for (int i = 0; i < pendingFenceSyncs.size(); i++) {
            final long id = pendingFenceSyncs.getLong(i);
            if (fence != 0) {
                fenceMap.put(id, fence);
                fenceRefcounts.addTo(fence, 1);
            } else {
                fenceMap.remove(id);
            }
        }
        pendingFenceSyncs.clear();
    }

    public int clientWaitSync(long sync, int flags, long timeout) {
        if (!fenceMap.containsKey(sync)) return GL32.GL_ALREADY_SIGNALED;
        long fence = fenceMap.get(sync);
        if (fence == 0) {
            if ((flags & GL32.GL_SYNC_FLUSH_COMMANDS_BIT) == 0) return GL32.GL_TIMEOUT_EXPIRED;
            fence = flushForUnresolved(sync);
            if (fence == 0) return GL32.GL_TIMEOUT_EXPIRED;
        }
        if (FenceWait.isSignaled(device, fence)) return GL32.GL_ALREADY_SIGNALED;
        if (timeout == 0) return GL32.GL_TIMEOUT_EXPIRED;
        final boolean signaled;
        Tracy.beginZone(Z_SDL_FENCE_WAIT);
        try {
            signaled = FenceWait.await(device, fence, timeout);
        } finally {
            Tracy.endZone();
        }
        return signaled ? GL32.GL_CONDITION_SATISFIED : GL32.GL_TIMEOUT_EXPIRED;
    }

    public void deleteSync(long sync) {
        final long fence = fenceMap.remove(sync);
        if (fence == 0 || device.getDevice() == 0) return;
        final int newCount = fenceRefcounts.addTo(fence, -1) - 1;
        if (newCount <= 0) {
            fenceRefcounts.remove(fence);
            device.fenceReleaser().release(fence);
        }
    }

    public void dispose() {
        if (device.getDevice() != 0) {
            for (long fence : fenceRefcounts.keySet()) {
                device.fenceReleaser().release(fence);
            }
        }
        fenceRefcounts.clear();
        fenceMap.clear();
        pendingFenceSyncs.clear();
    }

    public boolean isFenceSignaled(long sync) {
        if (!fenceMap.containsKey(sync)) return true;
        final long fence = fenceMap.get(sync);
        if (fence == 0) return false;
        return FenceWait.isSignaled(device, fence);
    }

    public int getSyncStatus(long sync) {
        return isFenceSignaled(sync) ? GL32.GL_SIGNALED : GL32.GL_UNSIGNALED;
    }

    public void waitSync(long sync) {
        if (!fenceMap.containsKey(sync)) return;
        long fence = fenceMap.get(sync);
        if (fence == 0) {
            flushForUnresolved(sync);
            return;
        }
        Tracy.beginZone(Z_SDL_FENCE_WAIT);
        try {
            FenceWait.await(device, fence, FenceWait.FOREVER);
        } finally {
            Tracy.endZone();
        }
    }
}
