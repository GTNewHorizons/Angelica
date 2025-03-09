package org.embeddedt.embeddium.impl.util;

import org.embeddedt.embeddium.impl.common.util.MathUtil;

public class PositionUtil {

    private static final long MAX_UNSIGNED_32BIT_INT = 4294967295L;

    public static long packChunk(int x, int z) {
        return (((long)z & MAX_UNSIGNED_32BIT_INT) << 32L) | ((long)x & MAX_UNSIGNED_32BIT_INT);
    }

    public static int unpackChunkX(long key) {
        return (int)(key & MAX_UNSIGNED_32BIT_INT);
    }

    public static int unpackChunkZ(long key) {
        return (int)((key >>> 32) & MAX_UNSIGNED_32BIT_INT);
    }

    public static long packSection(int x, int y, int z) {
        return (((long)x & SECTION_XZ_MASK) << 42L) | (((long)y & SECTION_Y_MASK) << 0L) | (((long)z & SECTION_XZ_MASK) << 20L);
    }

    public static int posToSectionCoord(double coord) {
        return posToSectionCoord(MathUtil.mojfloor(coord));
    }

    public static int posToSectionCoord(int coord) {
        return coord >> 4;
    }

    public static int sectionToBlockCoord(int sec, int block) {
        return (sec << 4) + block;
    }

    private static final long SECTION_XZ_MASK = 4194303L;
    private static final long SECTION_Y_MASK = 1048575L;
}
