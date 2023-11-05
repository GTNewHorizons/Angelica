package com.gtnewhorizons.angelica.compat.mojang;

import org.joml.Vector3i;

public class ChunkSectionPos extends Vector3i {
    private ChunkSectionPos(int x, int y, int z) {
        super(x, y, z);
    }
    public static ChunkSectionPos from(int x, int y, int z) {
        return new ChunkSectionPos(x, y, z);
    }


    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long)x & 4194303L) << 42;
        l |= ((long)y & 1048575L) << 0;
        l |= ((long)z & 4194303L) << 20;
        return l;
    }
    public long asLong() {
        return asLong(this.x, this.y, this.z);
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
}
