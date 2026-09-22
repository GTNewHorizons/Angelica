package com.gtnewhorizons.angelica.compat.bop;

import biomesoplenty.client.fog.IBiomeFog;
import java.util.Arrays;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class FogColourGrid {

    public static final int NO_FOG = BopFogBlend.NO_FOG;

    private static final int SIZE = 128;
    private static final int MASK = SIZE - 1;
    private static final long EMPTY = Long.MIN_VALUE;
    private static final FogColourGrid INSTANCE = new FogColourGrid();

    private final long[] keys = new long[SIZE * SIZE];
    private final int[] colours = new int[SIZE * SIZE];
    private World gridWorld;
    private boolean lastCacheable;
    private boolean interiorValid;
    private int interiorX;
    private int interiorY;
    private int interiorZ;
    private int interiorDistance;
    private float interiorR;
    private float interiorG;
    private float interiorB;
    private float interiorWeight;

    private FogColourGrid() {
        reset();
    }

    public static void invalidate() {
        INSTANCE.reset();
    }

    private void reset() {
        Arrays.fill(keys, EMPTY);
        gridWorld = null;
        lastCacheable = false;
        interiorValid = false;
    }

    private void useWorld(World world) {
        FogBiomeCache.useWorld(world);
        if (world != gridWorld) {
            reset();
            gridWorld = world;
        }
    }

    public static int colour(World world, int x, int y, int z) {
        INSTANCE.useWorld(world);
        return INSTANCE.lookup(world, x, y, z);
    }

    static void accumulateInterior(World world, int x, int y, int z, int distance, float[] out) {
        INSTANCE.useWorld(world);
        if (!INSTANCE.interiorValid || INSTANCE.interiorX != x || INSTANCE.interiorY != y || INSTANCE.interiorZ != z || INSTANCE.interiorDistance != distance) {
            INSTANCE.rebuildInterior(world, x, y, z, distance);
        }
        out[0] = INSTANCE.interiorR;
        out[1] = INSTANCE.interiorG;
        out[2] = INSTANCE.interiorB;
        out[3] = INSTANCE.interiorWeight;
    }

    private void rebuildInterior(World world, int playerX, int playerY, int playerZ, int distance) {
        float r = 0, g = 0, b = 0, weight = 0;
        boolean cacheable = true;
        interiorValid = false;
        for (int x = -distance + 1; x <= distance - 1; ++x) {
            for (int z = -distance + 1; z <= distance - 1; ++z) {
                final int color = lookup(world, playerX + x, playerY, playerZ + z);
                cacheable &= lastCacheable;
                if (color == NO_FOG) continue;
                r += (color & 0xFF0000) >> 16;
                g += (color & 0x00FF00) >> 8;
                b += color & 0x0000FF;
                weight += 1;
            }
        }
        interiorR = r;
        interiorG = g;
        interiorB = b;
        interiorWeight = weight;
        interiorX = playerX;
        interiorY = playerY;
        interiorZ = playerZ;
        interiorDistance = distance;
        interiorValid = cacheable;
    }

    private int lookup(World world, int x, int y, int z) {
        final long key = ((long) (x & 0x3FFFFFF) << 35) | ((long) (z & 0x3FFFFFF) << 9) | (y & 0x1FF);
        final int idx = ((x & MASK) << 7) | (z & MASK);

        if (keys[idx] == key) {
            lastCacheable = true;
            return colours[idx];
        }

        final BiomeGenBase biome = FogBiomeCache.get(world, x, z);
        final int result = (biome instanceof IBiomeFog fog) ? (fog.getFogColour(x, y, z) & 0xFFFFFF) : NO_FOG;
        lastCacheable = FogBiomeCache.lastWasCacheable();

        if (lastCacheable) {
            keys[idx] = key;
            colours[idx] = result;
        }

        return result;
    }
}
