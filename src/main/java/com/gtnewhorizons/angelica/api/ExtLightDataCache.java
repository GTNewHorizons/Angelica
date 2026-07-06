package com.gtnewhorizons.angelica.api;

import net.minecraft.world.IBlockAccess;

/**
 * Interface for {@code LightDataCache} exposing its block access reference.
 */
public interface ExtLightDataCache {

    IBlockAccess angelica$getBlockAccess();
}
