package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import org.embeddedt.embeddium.impl.biome.BiomeColorCache;

public class SmoothBiomeColorCache extends BiomeColorCache<BiomeGenBase, SmoothBiomeColorCache.ColorType> {
    private static final ThreadLocal<SmoothBiomeColorCache> activeCache = new ThreadLocal<>();

    public static void setActiveCache(SmoothBiomeColorCache cache) {
        activeCache.set(cache);
    }

    public static void clearActiveCache() {
        activeCache.remove();
    }

    public static SmoothBiomeColorCache getActiveCache() {
        return activeCache.get();
    }

    public SmoothBiomeColorCache(IBlockAccess blockAccess) {
        super((x, y, z) -> blockAccess.getBiomeGenForCoords(x, z), 3);
    }

    @Override
    protected int resolveColor(ColorType type, BiomeGenBase biome, int relativeX, int relativeY, int relativeZ) {
        return switch (type) {
            case GRASS -> biome.getBiomeGrassColor(relativeX, relativeY, relativeZ);
            case FOLIAGE -> biome.getBiomeFoliageColor(relativeX, relativeY, relativeZ);
            case WATER -> biome.getWaterColorMultiplier();
        };
    }

    public enum ColorType {
        GRASS,
        FOLIAGE,
        WATER
    }
}
