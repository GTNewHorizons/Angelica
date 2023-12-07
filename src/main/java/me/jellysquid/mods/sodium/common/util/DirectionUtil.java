package me.jellysquid.mods.sodium.common.util;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Contains a number of cached arrays to avoid allocations since calling Enum#values() requires the backing array to
 * be cloned every time.
 */
public class DirectionUtil {
    public static final ForgeDirection[] ALL_DIRECTIONS = ForgeDirection.VALID_DIRECTIONS;

    public static final int DIRECTION_COUNT = ALL_DIRECTIONS.length;

    // Provides the same order as enumerating ForgeDirection and checking the axis of each value
    public static final ForgeDirection[] HORIZONTAL_DIRECTIONS = new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST };

}
