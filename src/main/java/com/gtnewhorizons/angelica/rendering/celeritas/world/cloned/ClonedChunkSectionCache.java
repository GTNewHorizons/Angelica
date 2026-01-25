package com.gtnewhorizons.angelica.rendering.celeritas.world.cloned;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import net.minecraft.world.World;

import java.util.concurrent.TimeUnit;

public class ClonedChunkSectionCache {
    private static final int MAX_CACHE_SIZE = 512;
    private static final long MAX_CACHE_DURATION = TimeUnit.SECONDS.toNanos(5);

    private final World world;
    private final Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> byPosition = new Long2ReferenceLinkedOpenHashMap<>();
    private long time;

    public ClonedChunkSectionCache(World world) {
        this.world = world;
        this.time = getMonotonicTimeSource();
    }

    public synchronized void cleanup() {
        this.time = getMonotonicTimeSource();
        this.byPosition.values().removeIf(entry -> this.time > (entry.getLastUsedTimestamp() + MAX_CACHE_DURATION));
    }

    public synchronized ClonedChunkSection acquire(int x, int y, int z) {
        final long key = ChunkSectionPos.asLong(x, y, z);
        ClonedChunkSection section = this.byPosition.getAndMoveToLast(key);

        if (section == null) {
            while (this.byPosition.size() >= MAX_CACHE_SIZE) {
                this.byPosition.removeFirst();
            }
            section = this.createSection(x, y, z);
        }

        section.setLastUsedTimestamp(this.time);
        return section;
    }

    private ClonedChunkSection createSection(int x, int y, int z) {
        final ClonedChunkSection section = new ClonedChunkSection(this, this.world);
        final ChunkSectionPos pos = ChunkSectionPos.from(x, y, z);
        section.init(pos);
        this.byPosition.putAndMoveToLast(pos.asLong(), section);
        return section;
    }

    public synchronized void invalidate(int x, int y, int z) {
        this.byPosition.remove(ChunkSectionPos.asLong(x, y, z));
    }

    private static long getMonotonicTimeSource() {
        return System.nanoTime();
    }
}
