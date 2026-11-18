package com.gtnewhorizons.angelica.compat.bop;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public final class FogBiomeCache {

    private static final int MAX_ENTRIES = 16384;

    private static final Long2ObjectOpenHashMap<BiomeGenBase> CACHE = new Long2ObjectOpenHashMap<>();
    private static World cachedWorld;
    private static boolean lastCacheable;

    private FogBiomeCache() {}

    public static boolean lastWasCacheable() {
        return lastCacheable;
    }

    public static void invalidate() {
        CACHE.clear();
        cachedWorld = null;
        lastCacheable = false;
        FogColourGrid.invalidate();
    }

    static void useWorld(World world) {
        if (world != cachedWorld) {
            CACHE.clear();
            cachedWorld = world;
            FogColourGrid.invalidate();
        }
    }

    public static BiomeGenBase get(World world, int x, int z) {
        useWorld(world);
        final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        BiomeGenBase biome = CACHE.get(key);
        if (biome == null) {
            biome = world.getBiomeGenForCoords(x, z);
            lastCacheable = world.getChunkProvider().chunkExists(x >> 4, z >> 4);
            if (lastCacheable) {
                if (CACHE.size() >= MAX_ENTRIES) CACHE.clear();
                CACHE.put(key, biome);
            }
        } else {
            lastCacheable = true;
        }
        return biome;
    }
}
