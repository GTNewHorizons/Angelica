package com.gtnewhorizons.angelica.compat.mojang;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;

public enum Axis {
    X,
    Y,
    Z;

    public static Axis fromDirection(ModelQuadFacing dir) {
        return switch (dir) {
            case DOWN, UP -> Y;
            case NORTH, SOUTH -> Z;
            case WEST, EAST -> X;
            case UNASSIGNED -> null;
        };

    }
}
