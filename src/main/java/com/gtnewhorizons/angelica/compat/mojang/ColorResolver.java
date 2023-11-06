package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.world.biome.BiomeGenBase;

public interface ColorResolver {

    int getColor(BiomeGenBase biome, int x, int z);
}
