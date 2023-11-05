package com.gtnewhorizons.angelica.compat.mojang;

public class BiomeAccess {
    public interface Storage {
        Biome getBiomeForNoiseGen(int biomeX, int biomeY, int biomeZ);
    }
}
