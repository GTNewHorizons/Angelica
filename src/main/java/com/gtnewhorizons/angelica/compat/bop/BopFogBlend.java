package com.gtnewhorizons.angelica.compat.bop;

public final class BopFogBlend {

    public static final int NO_FOG = -1;

    @FunctionalInterface
    public interface ColorSource {
        /** Packed 0xRRGGBB, or {@link #NO_FOG}. */
        int fogColour(int x, int y, int z);
    }

    private static Object cachedWorld;
    private static int cachedGeneration;
    private static int cachedX, cachedY, cachedZ, cachedDistance;
    private static float interiorR, interiorG, interiorB, interiorWeight;

    private BopFogBlend() {}

    public static void invalidate() {
        cachedWorld = null;
    }

    public static void accumulate(Object world, int generation, ColorSource source, double posX, double posZ,
        int playerX, int playerY, int playerZ, int distance, float[] out) {

        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;

        final double xDiffLow = 1 - (posX - playerX);
        final double xDiffHigh = posX - playerX;
        final double zDiffLow = 1 - (posZ - playerZ);
        final double zDiffHigh = posZ - playerZ;

        if (distance <= 0) {
            cell(source, 0, 0, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            return;
        }

        if (world != cachedWorld || generation != cachedGeneration || playerX != cachedX || playerY != cachedY
            || playerZ != cachedZ || distance != cachedDistance) {
            recomputeInterior(source, playerX, playerY, playerZ, distance);
            cachedWorld = world;
            cachedGeneration = generation;
            cachedX = playerX;
            cachedY = playerY;
            cachedZ = playerZ;
            cachedDistance = distance;
        }

        out[0] = interiorR;
        out[1] = interiorG;
        out[2] = interiorB;
        out[3] = interiorWeight;

        for (int z = -distance; z <= distance; ++z) {
            cell(source, -distance, z, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            cell(source, distance, z, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
        }
        for (int x = -distance + 1; x <= distance - 1; ++x) {
            cell(source, x, -distance, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            cell(source, x, distance, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
        }
    }

    private static void recomputeInterior(ColorSource source, int playerX, int playerY, int playerZ, int distance) {
        float r = 0, g = 0, b = 0, weight = 0;
        for (int x = -distance + 1; x <= distance - 1; ++x) {
            for (int z = -distance + 1; z <= distance - 1; ++z) {
                final int colour = source.fogColour(playerX + x, playerY, playerZ + z);
                if (colour == NO_FOG) continue;
                r += (colour & 0xFF0000) >> 16;
                g += (colour & 0x00FF00) >> 8;
                b += colour & 0x0000FF;
                weight += 1;
            }
        }
        interiorR = r;
        interiorG = g;
        interiorB = b;
        interiorWeight = weight;
    }

    private static void cell(ColorSource source, int x, int z, double xDiffLow, double xDiffHigh, double zDiffLow,
        double zDiffHigh, int playerX, int playerY, int playerZ, int distance, float[] out) {

        final int colour = source.fogColour(playerX + x, playerY, playerZ + z);
        if (colour == NO_FOG) return;

        float rPart = (colour & 0xFF0000) >> 16;
        float gPart = (colour & 0x00FF00) >> 8;
        float bPart = colour & 0x0000FF;
        float weightPart = 1;

        if (x == -distance) {
            rPart *= xDiffLow;
            gPart *= xDiffLow;
            bPart *= xDiffLow;
            weightPart *= xDiffLow;
        } else if (x == distance) {
            rPart *= xDiffHigh;
            gPart *= xDiffHigh;
            bPart *= xDiffHigh;
            weightPart *= xDiffHigh;
        }

        if (z == -distance) {
            rPart *= zDiffLow;
            gPart *= zDiffLow;
            bPart *= zDiffLow;
            weightPart *= zDiffLow;
        } else if (z == distance) {
            rPart *= zDiffHigh;
            gPart *= zDiffHigh;
            bPart *= zDiffHigh;
            weightPart *= zDiffHigh;
        }

        out[0] += rPart;
        out[1] += gPart;
        out[2] += bPart;
        out[3] += weightPart;
    }
}
