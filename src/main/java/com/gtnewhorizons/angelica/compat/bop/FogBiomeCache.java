package com.gtnewhorizons.angelica.compat.bop;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class FogBiomeCache {

    private static final int MAX_ENTRIES = 16384;

    private static final Long2ObjectOpenHashMap<BiomeGenBase> CACHE = new Long2ObjectOpenHashMap<>();
    private static World lastWorld;
    private static int generation;

    private FogBiomeCache() {}

    public static int generation() {
        return generation;
    }

    public static BiomeGenBase get(World world, int x, int z) {
        if (world != lastWorld) {
            CACHE.clear();
            lastWorld = world;
            generation++;
        }
        final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        BiomeGenBase biome = CACHE.get(key);
        if (biome == null) {
            biome = world.getBiomeGenForCoords(x, z);
            if (world.getChunkProvider().chunkExists(x >> 4, z >> 4)) {
                if (CACHE.size() >= MAX_ENTRIES) CACHE.clear();
                CACHE.put(key, biome);
                generation++;
            }
        }
        return biome;
    }
}
