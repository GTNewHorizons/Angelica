package com.gtnewhorizons.angelica.dynamiclights;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
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

    /** Reusable lists for processVisible - only used from render thread */
    private final LongArrayList candidates = new LongArrayList();
    private final LongArrayList toRebuild = new LongArrayList();
    private final LongArrayList toIncrement = new LongArrayList();

    public static void setMaxTicksWaiting(int ticks) {
        maxTicksWaiting = Math.max(20, Math.min(ticks, 600)); // Clamp 1-30 seconds (20-600 ticks)
    }

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

    public void processVisible(Viewport viewport, @NotNull IDynamicLightWorldRenderer renderer) {
        candidates.clear();
        toRebuild.clear();
        toIncrement.clear();

        // Phase 1: Collect all pending chunks with read lock (fast)
        lock.readLock().lock();
        try {
            if (pendingRebuilds.isEmpty()) {
                return;
            }
            // Collect candidates that might need processing
            final var iterator = pendingRebuilds.long2IntEntrySet().fastIterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                final long packed = entry.getLongKey();
                final int ticksWaiting = entry.getIntValue();

                // Force process if no viewport or waited too long
                if (viewport == null || ticksWaiting >= maxTicksWaiting) {
                    toRebuild.add(packed);
                } else {
                    candidates.add(packed);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        // Phase 2: Check visibility
        for (int i = 0; i < candidates.size(); i++) {
            long packed = candidates.getLong(i);
            int x = CoordinatePacker.unpackX(packed);
            int y = CoordinatePacker.unpackY(packed);
            int z = CoordinatePacker.unpackZ(packed);

            if (isChunkVisible(viewport, x, y, z)) {
                toRebuild.add(packed);
            } else {
                toIncrement.add(packed);
            }
        }

        // Phase 3: Update map with write lock (brief)
        if (!toRebuild.isEmpty() || !toIncrement.isEmpty()) {
            lock.writeLock().lock();
            try {
                // Remove chunks we're going to rebuild
                LongIterator rebuildIter = toRebuild.iterator();
                while (rebuildIter.hasNext()) {
                    pendingRebuilds.remove(rebuildIter.nextLong());
                }
                LongIterator incIter = toIncrement.iterator();
                while (incIter.hasNext()) {
                    long packed = incIter.nextLong();
                    int current = pendingRebuilds.get(packed);
                    if (pendingRebuilds.containsKey(packed)) {
                        pendingRebuilds.put(packed, current + 1);
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        // Phase 4: Schedule rebuilds outside of lock
        for (int i = 0; i < toRebuild.size(); i++) {
            final long packed = toRebuild.getLong(i);
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
