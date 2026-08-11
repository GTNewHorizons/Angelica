package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.sdl.SDLGPU.*;

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
        if (SDL_QueryGPUFence(device.getDevice(), fence)) return GL32.GL_ALREADY_SIGNALED;
        if (timeout == 0) return GL32.GL_TIMEOUT_EXPIRED;
        Tracy.beginZone(Z_SDL_FENCE_WAIT);
        try (var stack = MemoryStack.stackPush()) {
            final PointerBuffer fences = stack.mallocPointer(1).put(0, fence);
            SDL_WaitForGPUFences(device.getDevice(), true, fences);
        } finally {
            Tracy.endZone();
        }
        return GL32.GL_CONDITION_SATISFIED;
    }

    public void deleteSync(long sync) {
        final long fence = fenceMap.remove(sync);
        if (fence == 0 || device.getDevice() == 0) return;
        final int newCount = fenceRefcounts.addTo(fence, -1) - 1;
        if (newCount <= 0) {
            fenceRefcounts.remove(fence);
            SDL_ReleaseGPUFence(device.getDevice(), fence);
        }
    }

    public void dispose() {
        final long dev = device.getDevice();
        if (dev != 0) {
            for (long fence : fenceRefcounts.keySet()) {
                SDL_ReleaseGPUFence(dev, fence);
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
        return SDL_QueryGPUFence(device.getDevice(), fence);
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
        if (SDL_QueryGPUFence(device.getDevice(), fence)) return;
        Tracy.beginZone(Z_SDL_FENCE_WAIT);
        try (var stack = MemoryStack.stackPush()) {
            final PointerBuffer fences = stack.mallocPointer(1).put(0, fence);
            SDL_WaitForGPUFences(device.getDevice(), true, fences);
        } finally {
            Tracy.endZone();
        }
    }
}
