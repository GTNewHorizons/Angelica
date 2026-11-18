package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class SkyColorCache {
    private final double[][] colors = new double[4][3];
    private World lastWorld;
    private long lastTick;
    private int lastX, lastY, lastZ, lastRadius;

    public int getColor(World world, double x, double y, double z, int radius) {
        final int blockX = (int) Math.floor(x);
        final int blockY = (int) Math.floor(y);
        final int blockZ = (int) Math.floor(z);
        if (radius == 0) {
            return sample(world, blockX, blockY, blockZ);
        }

        final long tick = world.getTotalWorldTime();
        if (world != lastWorld || tick != lastTick || blockX != lastX || blockY != lastY || blockZ != lastZ || radius != lastRadius) {
            updateAverages(world, blockX, blockY, blockZ, radius);
            lastWorld = world;
            lastTick = tick;
            lastX = blockX;
            lastY = blockY;
            lastZ = blockZ;
            lastRadius = radius;
        }

        final double dx = x - blockX;
        final double dz = z - blockZ;
        int color = 0;
        for (int channel = 0; channel < 3; channel++) {
            final double north = colors[0][channel] + (colors[1][channel] - colors[0][channel]) * dx;
            final double south = colors[2][channel] + (colors[3][channel] - colors[2][channel]) * dx;
            color = (color << 8) | (int) Math.round(north + (south - north) * dz);
        }
        return color;
    }

    private void updateAverages(World world, int x, int y, int z, int radius) {
        for (double[] color : colors) {
            color[0] = color[1] = color[2] = 0;
        }

        for (int dx = -radius; dx <= radius + 1; dx++) {
            for (int dz = -radius; dz <= radius + 1; dz++) {
                final int color = sample(world, x + dx, y, z + dz);
                final int red = (color >> 16) & 255;
                final int green = (color >> 8) & 255;
                final int blue = color & 255;
                if (dx <= radius) {
                    if (dz <= radius) add(colors[0], red, green, blue);
                    if (dz > -radius) add(colors[2], red, green, blue);
                }
                if (dx > -radius) {
                    if (dz <= radius) add(colors[1], red, green, blue);
                    if (dz > -radius) add(colors[3], red, green, blue);
                }
            }
        }
        final double count = (radius * 2 + 1) * (radius * 2 + 1);
        for (double[] color : colors) {
            color[0] /= count;
            color[1] /= count;
            color[2] /= count;
        }
    }

    private static void add(double[] sum, int red, int green, int blue) {
        sum[0] += red;
        sum[1] += green;
        sum[2] += blue;
    }

    private static int sample(World world, int x, int y, int z) {
        final BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        return biome.getSkyColorByTemp(biome.getFloatTemperature(x, y, z));
    }
}
