package com.gtnewhorizons.angelica.compat.mojang;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.minecraftforge.common.util.ForgeDirection;

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

    public static Axis fromDirection(ForgeDirection dir) {
        return switch (dir) {
            case DOWN, UP -> Y;
            case NORTH, SOUTH -> Z;
            case WEST, EAST -> X;
            case UNKNOWN -> null;
        };
    }

    public static Axis fromName(String dir) {
        return switch (dir) {
            case "y" -> Y;
            case "z" -> Z;
            case "x" -> X;
            default -> null;
        };

    }

    public enum Direction {
        POSITIVE,
        NEGATIVE;

        public static Direction fromDirection(ForgeDirection dir) {
            return switch (dir) {
                case DOWN, NORTH, WEST -> NEGATIVE;
                case UP, SOUTH, EAST -> POSITIVE;
                case UNKNOWN -> null;
            };
        }
    }
}
