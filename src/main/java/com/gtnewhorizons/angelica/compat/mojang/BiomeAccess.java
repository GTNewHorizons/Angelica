package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.world.biome.BiomeGenBase;

public class BiomeAccess {
    public interface Storage {
        BiomeGenBase getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ);
    }
}
