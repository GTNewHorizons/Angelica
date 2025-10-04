package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;

// See if we can merge/mixin/extend ChunkCoordIntPair?
public class ChunkPos {
    public static long INT_MASK   = (1L << Integer.SIZE) - 1;

    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos pos) {
        this.x = pos.getX() >> 4;
        this.z = pos.getZ() >> 4;
    }

    public ChunkPos(long pos) {
        this.x = (int)pos;
        this.z = (int)(pos >> 32);
    }

    public static ChunkPos of(Chunk chunk) {
        return new ChunkPos(chunk.xPosition, chunk.zPosition);
    }

    public static int getPackedX(long pos) {
        return (int)(pos & INT_MASK);
    }

    public static int getPackedZ(long pos) {
        return (int)(pos >>> 32 & INT_MASK);
    }

    public long toLong() {
        return toLong(this.x, this.z);
    }

    public static long toLong(int x, int z) {
        return (long)x & 4294967295L | ((long)z & 4294967295L) << 32;
    }

    public int getRegionX() {
        return this.x >> 5;
    }

    public int getRegionZ() {
        return this.z >> 5;
    }

    public ChunkCoordIntPair toChunkCoord() {
        return new ChunkCoordIntPair(this.x, this.z);
    }

    @Override
    public int hashCode()
    {
        final int i = 1664525 * this.x + 1013904223;
        final int j = 1664525 * (this.z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof ChunkPos lv) {
            return this.x == lv.x && this.z == lv.z;
        }
        return false;
    }
}
