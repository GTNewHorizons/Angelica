package org.embeddedt.archaicfix.lighting.api;

import net.minecraft.world.EnumSkyBlock;

public interface ILightingEngine {
    void scheduleLightUpdate(EnumSkyBlock lightType, int xIn, int yIn, int zIn);

    void processLightUpdates();

    void processLightUpdatesForType(EnumSkyBlock lightType);
}
