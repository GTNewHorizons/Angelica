package com.gtnewhorizons.angelica.api;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * <p>Modern versions have BlockState, which allows you to query properties in a fairly standardized way. We don't have
 * that on 1.7.10, so let's roll our own. Blocks can implement this to convert metas to properties.
 */
public interface BlockState {

    default boolean hasFacing() { return false; }

    default ForgeDirection getFacing(int meta) { return ForgeDirection.UNKNOWN; }
}
