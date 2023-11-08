package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraftforge.common.util.ForgeDirection;

public enum Axis {
    X,
    Y,
    Z;

    public static Axis fromDirection(ForgeDirection dir) {
        return switch (dir) {
            case DOWN, UP -> Y;
            case NORTH, SOUTH -> Z;
            case WEST, EAST -> X;
            default -> null;
        };

    }
}
