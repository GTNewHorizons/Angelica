package com.gtnewhorizons.angelica.compat.mojang;

public interface BiomeAccessType {

    Biome getBiome(long seed, int x, int y, int z, BiomeAccess.Storage storage);
}
