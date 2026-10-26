package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.World;

/**
 * Remembers the lightmap the tile entity dispatcher looks up per tile per frame, for one client tick.
 * Light only changes inside the tick, so the cache is dropped by age instead of by light updates.
 */
public final class TesrLightCache {

    private static final int MISSING = Integer.MIN_VALUE;

    private static final Long2IntOpenHashMap CACHE = new Long2IntOpenHashMap();

    static {
        CACHE.defaultReturnValue(MISSING);
    }

    private static int tick;
    private static int cachedTick = Integer.MIN_VALUE;

    private TesrLightCache() {}

    public static void onClientTick() {
        tick++;
    }

    public static void clear() {
        CACHE.clear();
        cachedTick = Integer.MIN_VALUE;
    }

    public static int getLightBrightnessForSkyBlocks(World world, int x, int y, int z, int minBlockLight) {
        if (minBlockLight != 0 || !GLStateManager.isMainThread()) {
            return world.getLightBrightnessForSkyBlocks(x, y, z, minBlockLight);
        }

        if (tick != cachedTick) {
            CACHE.clear();
            cachedTick = tick;
        }

        final long key = key(x, y, z);
        int packed = CACHE.get(key);
        if (packed == MISSING) {
            packed = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
            CACHE.put(key, packed);
        }
        return packed;
    }

    // 26 bits for x and z cover the world border, 8 bits for y; no two positions share a key
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 34) | ((long) (y & 0xFF) << 26) | (long) (z & 0x3FFFFFF);
    }
}
