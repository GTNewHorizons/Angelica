package org.embeddedt.archaicfix.lighting.api;

import net.minecraft.world.EnumSkyBlock;

public interface IChunkLighting {
    int getCachedLightFor(EnumSkyBlock enumSkyBlock, int xIn, int yIn, int zIn);
}
