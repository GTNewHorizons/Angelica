package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.world.biome.BiomeGenBase;

public interface BiomeAccessType {

    BiomeGenBase getBiome(long seed, int x, int y, int z, BiomeAccess.Storage storage);
}
