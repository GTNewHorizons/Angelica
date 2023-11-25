package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraftforge.common.util.ForgeDirection;

public final class VoxelShapes {

    public static VoxelShape fullCube() {
        return VoxelShape.FULL_CUBE;
    }

    public static VoxelShape empty() {
        return VoxelShape.EMPTY;
    }

    public static boolean matchesAnywhere(VoxelShape shape1, VoxelShape shape2, Object predicate) {
        return false;
    }

    public static VoxelShape cuboid(double v, double v1, double v2, double v3, float height, double v4) {
        return null;
    }

    public static boolean isSideCovered(VoxelShape threshold, VoxelShape shape, ForgeDirection dir) {
        return true;
    }
}
