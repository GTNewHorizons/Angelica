package com.gtnewhorizons.angelica.render;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.biome.BiomeGenBase;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Random;

/**
 * Per-column rain and snow data, sampled once and reused instead of recomputed for every column of every frame.
 */
public final class WeatherColumns {

    private static final float TEMPERATURE_PER_BLOCK = 0.05F / 30.0F;
    private static final int TEMPERATURE_REFERENCE_Y = 65;

    private static final int REFRESH_TICKS = 20;

    private final Random random = new Random();

    private int size;
    private int[] keyX = new int[0];
    private int[] keyZ = new int[0];
    private boolean[] present = new boolean[0];
    private boolean[] precipitates = new boolean[0];
    private boolean[] strikesLightning = new boolean[0];
    private int[] height = new int[0];
    private int[] hash = new int[0];
    private float[] temperatureAtReference = new float[0];
    private float[] temperatureBelow65 = new float[0];
    private int[] light = new int[0];
    private int[] lightY = new int[0];
    private float[] jitter = new float[0];

    private int refreshCursor;
    private int revision;
    private int lastTick = Integer.MIN_VALUE;
    private int originX = Integer.MIN_VALUE;
    private int originZ = Integer.MIN_VALUE;
    private WeakReference<WorldClient> lastWorld = new WeakReference<>(null);

    public int revision() {
        return revision;
    }

    public void update(WorldClient world, int cameraBlockX, int cameraBlockZ, int radius, int tick) {
        final int wanted = radius * 2 + 1;
        final boolean resized = wanted != size;
        if (resized) resize(wanted);

        final boolean worldChanged = lastWorld.get() != world;
        if (worldChanged) {
            lastWorld = new WeakReference<>(world);
            Arrays.fill(present, false);
            originX = Integer.MIN_VALUE;
            originZ = Integer.MIN_VALUE;
            revision++;
        }

        final int slots = size * size;
        if (tick != lastTick) {
            lastTick = tick;
            final int slice = Math.max(1, slots / REFRESH_TICKS);
            for (int i = 0; i < slice; i++) {
                if (++refreshCursor >= slots) refreshCursor = 0;
                if (present[refreshCursor]) sample(world, keyX[refreshCursor], keyZ[refreshCursor], refreshCursor);
            }
        }

        if (!resized && cameraBlockX == originX && cameraBlockZ == originZ) return;
        originX = cameraBlockX;
        originZ = cameraBlockZ;

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                final int x = cameraBlockX + dx;
                final int z = cameraBlockZ + dz;
                final int slot = slotOf(x, z);
                if (present[slot] && keyX[slot] == x && keyZ[slot] == z) continue;
                sample(world, x, z, slot);
            }
        }
    }

    public boolean precipitates(int x, int z) {
        return precipitates[slotOf(x, z)];
    }

    public int slotAt(int x, int z) {
        return slotOf(x, z);
    }

    public boolean precipitatesAtSlot(int slot) {
        return precipitates[slot];
    }

    public int heightAtSlot(int slot) {
        return height[slot];
    }

    public int hashAtSlot(int slot) {
        return hash[slot];
    }

    public float jitterAtSlot(int slot, int index) {
        return jitter[slot * 4 + index];
    }

    public float temperatureAtSlot(int slot, int y) {
        if (y <= 64) return temperatureBelow65[slot];
        return temperatureAtReference[slot] - (y - TEMPERATURE_REFERENCE_Y) * TEMPERATURE_PER_BLOCK;
    }

    public int lightAtSlot(WorldClient world, int slot, int x, int z, int y) {
        if (lightY[slot] != y) {
            lightY[slot] = y;
            light[slot] = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
        }
        return light[slot];
    }

    public boolean covers(int x, int z) {
        if (size == 0) return false;
        final int slot = slotOf(x, z);
        return present[slot] && keyX[slot] == x && keyZ[slot] == z;
    }

    public boolean strikesLightning(int x, int z) {
        return strikesLightning[slotOf(x, z)];
    }

    public int heightAt(int x, int z) {
        return height[slotOf(x, z)];
    }

    public int hashAt(int x, int z) {
        return hash[slotOf(x, z)];
    }

    public float temperatureAt(int x, int z, int y) {
        final int slot = slotOf(x, z);
        if (y <= 64) return temperatureBelow65[slot];
        return temperatureAtReference[slot] - (y - TEMPERATURE_REFERENCE_Y) * TEMPERATURE_PER_BLOCK;
    }

    public int lightAt(WorldClient world, int x, int z, int y) {
        final int slot = slotOf(x, z);
        if (lightY[slot] != y) {
            lightY[slot] = y;
            light[slot] = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
        }
        return light[slot];
    }

    public float jitterAt(int x, int z, int index) {
        return jitter[slotOf(x, z) * 4 + index];
    }

    private int slotOf(int x, int z) {
        return Math.floorMod(x, size) * size + Math.floorMod(z, size);
    }

    private void resize(int wanted) {
        size = wanted;
        final int slots = wanted * wanted;
        keyX = new int[slots];
        keyZ = new int[slots];
        present = new boolean[slots];
        precipitates = new boolean[slots];
        strikesLightning = new boolean[slots];
        height = new int[slots];
        hash = new int[slots];
        temperatureAtReference = new float[slots];
        temperatureBelow65 = new float[slots];
        light = new int[slots];
        lightY = new int[slots];
        jitter = new float[slots * 4];
        refreshCursor = 0;
    }

    private void sample(WorldClient world, int x, int z, int slot) {
        final boolean sameColumn = present[slot] && keyX[slot] == x && keyZ[slot] == z;
        final boolean wasPrecipitating = precipitates[slot];
        final int wasHeight = height[slot];

        keyX[slot] = x;
        keyZ[slot] = z;
        present[slot] = true;
        lightY[slot] = Integer.MIN_VALUE;

        final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        strikesLightning[slot] = biome.canSpawnLightningBolt();
        precipitates[slot] = strikesLightning[slot] || biome.getEnableSnow();
        if (!sameColumn || precipitates[slot] != wasPrecipitating) revision++;
        if (!precipitates[slot]) return;

        height[slot] = world.getPrecipitationHeight(x, z);
        if (height[slot] != wasHeight) revision++;
        temperatureAtReference[slot] = biome.getFloatTemperature(x, TEMPERATURE_REFERENCE_Y, z);
        temperatureBelow65[slot] = biome.getFloatTemperature(x, 64, z);

        // Vanilla overflows these in int before widening
        hash[slot] = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761;
        random.setSeed((long) x * x * 3121 + x * 45238971L ^ (long) z * z * 418711 + z * 13761L);

        final int base = slot * 4;
        jitter[base] = random.nextFloat();
        jitter[base + 1] = (float) random.nextGaussian();
        jitter[base + 2] = random.nextFloat();
        jitter[base + 3] = (float) random.nextGaussian();
    }
}
