package com.gtnewhorizons.angelica.compat.bop;

import net.minecraft.world.World;

public final class BopFogBlend {

    public static final int NO_FOG = -1;

    private BopFogBlend() {}

    public static void invalidate() {
        FogBiomeCache.invalidate();
    }

    public static void accumulate(World world, double posX, double posZ, int playerX, int playerY, int playerZ,
        int distance, float[] out) {

        out[0] = 0;
        out[1] = 0;
        out[2] = 0;
        out[3] = 0;

        final double xDiffLow = 1 - (posX - playerX);
        final double xDiffHigh = posX - playerX;
        final double zDiffLow = 1 - (posZ - playerZ);
        final double zDiffHigh = posZ - playerZ;

        if (distance <= 0) {
            cell(world, 0, 0, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            return;
        }

        FogColourGrid.accumulateInterior(world, playerX, playerY, playerZ, distance, out);

        for (int z = -distance; z <= distance; ++z) {
            cell(world, -distance, z, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            cell(world, distance, z, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
        }
        for (int x = -distance + 1; x <= distance - 1; ++x) {
            cell(world, x, -distance, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
            cell(world, x, distance, xDiffLow, xDiffHigh, zDiffLow, zDiffHigh, playerX, playerY, playerZ, distance, out);
        }
    }

    private static void cell(World world, int x, int z, double xDiffLow, double xDiffHigh, double zDiffLow,
        double zDiffHigh, int playerX, int playerY, int playerZ, int distance, float[] out) {

        final int color = FogColourGrid.colour(world, playerX + x, playerY, playerZ + z);
        if (color == NO_FOG) return;

        float rPart = (color & 0xFF0000) >> 16;
        float gPart = (color & 0x00FF00) >> 8;
        float bPart = color & 0x0000FF;
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
