package me.jellysquid.mods.sodium.common.util;

import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Contains a number of cached arrays to avoid allocations since calling Enum#values() requires the backing array to
 * be cloned every time.
 */
public class DirectionUtil {
    public static final ForgeDirection[] ALL_DIRECTIONS = ForgeDirection.VALID_DIRECTIONS;

    public static final int DIRECTION_COUNT = ALL_DIRECTIONS.length;

    // Provides the same order as enumerating ForgeDirection and checking the axis of each value
    public static final ForgeDirection[] HORIZONTAL_DIRECTIONS = new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST };

    public static final Vector3i[] STEP = {

        //DOWN
        new Vector3i(0, -1, 0),
        // UP
        new Vector3i(0, 1, 0),
        // NORTH
        new Vector3i(0, 0, -1),
        // SOUTH
        new Vector3i(0, 0, 1),
        // WEST
        new Vector3i(-1, 0, 0),
        // EAST
        new Vector3i(1, 0, 0),
        // UNKNOWN
        new Vector3i(0, 0, 0)
    };
}
