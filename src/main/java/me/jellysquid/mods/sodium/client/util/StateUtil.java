package me.jellysquid.mods.sodium.client.util;

import net.minecraft.block.Block;

public class StateUtil {

    public static boolean areBoundsFullCube(Block b) {

        if (b.getBlockBoundsMaxX() < 1)
            return false;
        if (b.getBlockBoundsMinX() > 0)
            return false;
        if (b.getBlockBoundsMaxY() < 1)
            return false;
        if (b.getBlockBoundsMinY() > 0)
            return false;
        if (b.getBlockBoundsMaxZ() < 1)
            return false;
        if (b.getBlockBoundsMinZ() > 1)
            return false;

        return true;
    }
}
