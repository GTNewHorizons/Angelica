package com.gtnewhorizons.angelica.compat.mojang;

// ChunkCoordIntPair
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
}