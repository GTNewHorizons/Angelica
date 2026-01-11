package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages deferred chunk rebuilds for dynamic lights with optional frustum culling. Instead of scheduling chunk rebuilds immediately
 * when light sources move, this tracks pending rebuilds and only schedules visible chunks.
 */
public class ChunkRebuildManager {

    /** Maps packed chunk position to ticks waiting */
    private final Long2IntOpenHashMap pendingRebuilds = new Long2IntOpenHashMap();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Maximum ticks a pending rebuild can wait before being forced through */
    private static int maxTicksWaiting = 100;

    public static void setMaxTicksWaiting(int ticks) {
        maxTicksWaiting = Math.max(20, Math.min(ticks, 600)); // Clamp 1-30 seconds
    }

    /** Reusable list for collecting chunks to rebuild outside of lock */
    private final LongArrayList toRebuild = new LongArrayList();

    public void requestRebuild(int x, int y, int z) {
        final long packed = CoordinatePacker.pack(x, y, z);
        lock.writeLock().lock();
        try {
            // Reset wait counter if already pending, otherwise add new entry
            pendingRebuilds.put(packed, 0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void requestRemoval(int x, int y, int z) {
        // Removal and rebuild are now treated the same - both need chunk rebuild
        requestRebuild(x, y, z);
    }

    public void processVisible(Viewport viewport, @NotNull IDynamicLightWorldRenderer renderer) {
        toRebuild.clear();

        // Phase 1: Collect chunks to rebuild while holding lock
        lock.writeLock().lock();
        try {
            var iterator = pendingRebuilds.long2IntEntrySet().fastIterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                long packed = entry.getLongKey();
                int ticksWaiting = entry.getIntValue();

                int x = CoordinatePacker.unpackX(packed);
                int y = CoordinatePacker.unpackY(packed);
                int z = CoordinatePacker.unpackZ(packed);

                // Check if chunk is visible or we've waited too long
                boolean shouldProcess;
                if (viewport == null) {
                    // No frustum available yet, process immediately to avoid visual glitches
                    shouldProcess = true;
                } else if (ticksWaiting >= maxTicksWaiting) {
                    // Waited too long, force process
                    shouldProcess = true;
                } else {
                    // Check visibility
                    shouldProcess = isChunkVisible(viewport, x, y, z);
                }

                if (shouldProcess) {
                    toRebuild.add(packed);
                    iterator.remove();
                } else {
                    // Increment wait counter
                    entry.setValue(ticksWaiting + 1);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }

        // Phase 2: Schedule rebuilds outside of lock to avoid contention
        for (int i = 0; i < toRebuild.size(); i++) {
            long packed = toRebuild.getLong(i);
            renderer.scheduleRebuildForChunk(
                CoordinatePacker.unpackX(packed),
                CoordinatePacker.unpackY(packed),
                CoordinatePacker.unpackZ(packed),
                false
            );
        }
    }

    private boolean isChunkVisible(Viewport viewport, int chunkX, int chunkY, int chunkZ) {
        // Convert chunk section coords to block coords
        int blockX = chunkX << 4;
        int blockY = chunkY << 4;
        int blockZ = chunkZ << 4;

        // Check if the 16x16x16 chunk section box is visible
        return viewport.isBoxVisible(
            blockX, blockY, blockZ,
            blockX + 16, blockY + 16, blockZ + 16
        );
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            pendingRebuilds.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getPendingCount() {
        lock.readLock().lock();
        try {
            return pendingRebuilds.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
