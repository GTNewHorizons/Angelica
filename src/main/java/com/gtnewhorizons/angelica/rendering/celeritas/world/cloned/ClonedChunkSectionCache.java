package com.gtnewhorizons.angelica.rendering.celeritas.world.cloned;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

public class ClonedChunkSectionCache {
    private static final int MAX_CACHE_SIZE = 512;
    private static final long MAX_CACHE_DURATION = TimeUnit.SECONDS.toNanos(5);
    private static final int MAX_FREE = 64;

    private final World world;
    private final Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> byPosition = new Long2ReferenceLinkedOpenHashMap<>();
    private final ArrayDeque<ClonedChunkSection> free = new ArrayDeque<>();
    private long time;

    public ClonedChunkSectionCache(World world) {
        this.world = world;
        this.time = getMonotonicTimeSource();
    }

    public synchronized void cleanup() {
        this.time = getMonotonicTimeSource();
        while (!this.byPosition.isEmpty()) {
            final ClonedChunkSection oldest = this.byPosition.get(this.byPosition.firstLongKey());
            if (this.time <= oldest.getLastUsedTimestamp() + MAX_CACHE_DURATION) {
                break;
            }
            evict(this.byPosition.removeFirst());
        }
    }

    public synchronized ClonedChunkSection acquire(int x, int y, int z) {
        final long key = ChunkSectionPos.asLong(x, y, z);
        ClonedChunkSection section = this.byPosition.getAndMoveToLast(key);

        if (section == null) {
            while (this.byPosition.size() >= MAX_CACHE_SIZE) {
                evict(this.byPosition.removeFirst());
            }
            section = this.createSection(key, x, y, z);
        }

        section.refs++;
        section.setLastUsedTimestamp(this.time);
        return section;
    }

    private ClonedChunkSection createSection(long key, int x, int y, int z) {
        ClonedChunkSection section = this.free.pollLast();
        if (section == null) {
            section = new ClonedChunkSection(this, this.world);
        }
        section.init(ChunkSectionPos.from(x, y, z));
        section.cached = true;
        this.byPosition.putAndMoveToLast(key, section);
        return section;
    }

    public synchronized void invalidate(int x, int y, int z) {
        final ClonedChunkSection section = this.byPosition.remove(ChunkSectionPos.asLong(x, y, z));
        if (section != null) {
            evict(section);
        }
    }

    public synchronized void release(ClonedChunkSection[] sections) {
        for (final ClonedChunkSection section : sections) {
            if (section != null) {
                section.refs--;
                recycleIfIdle(section);
            }
        }
    }

    private void evict(ClonedChunkSection section) {
        section.cached = false;
        recycleIfIdle(section);
    }

    private void recycleIfIdle(ClonedChunkSection section) {
        if (!section.cached && section.refs == 0 && this.free.size() < MAX_FREE) {
            this.free.addLast(section);
        }
    }

    private static long getMonotonicTimeSource() {
        return System.nanoTime();
    }
}
