package com.gtnewhorizons.angelica.compat.mojang;

import org.joml.Vector3i;

// ChunkPosition maybe?
public class ChunkSectionPos extends Vector3i {

    public static int getSectionCoord(int coord) {
        return coord >> 4;
    }
    public static int getBlockCoord(int sectionCoord) {
        return sectionCoord << 4;
    }

    private ChunkSectionPos(int x, int y, int z) {
        super(x, y, z);
    }
    public static ChunkSectionPos from(int x, int y, int z) {
        return new ChunkSectionPos(x, y, z);
    }

    public static ChunkSectionPos from(BlockPos pos) {
        return new ChunkSectionPos(getSectionCoord(pos.getX()), getSectionCoord(pos.getY()), getSectionCoord(pos.getZ()));
    }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long)x & 4194303L) << 42;
        l |= ((long)y & 1048575L) << 0;
        l |= ((long)z & 4194303L) << 20;
        return l;
    }

    public static int getLocalCoord(int coord) {
        return coord & 15;
    }

    public static short packLocal(BlockPos pos) {
        int i = getLocalCoord(pos.x);
        int j = getLocalCoord(pos.y);
        int k = getLocalCoord(pos.z);
        return (short)(i << 8 | k << 4 | j << 0);
    }

    public long asLong() {
        return asLong(this.x, this.y, this.z);
    }

    public int getSectionX() {
        return this.x;
    }

    public int getSectionY() {
        return this.y;
    }

    public int getSectionZ() {
        return this.z;
    }

    public int getMinX() {
        return this.x << 4;
    }

    public int getMinY() {
        return this.y << 4;
    }

    public int getMinZ() {
        return this.z << 4;
    }

    public int getMaxX() {
        return (this.x << 4) + 15;
    }

    public int getMaxY() {
        return (this.y << 4) + 15;
    }

    public int getMaxZ() {
        return (this.z << 4) + 15;
    }

    public ChunkPos toChunkPos() {
        return new ChunkPos(this.getSectionX(), this.getSectionZ());
    }
}
