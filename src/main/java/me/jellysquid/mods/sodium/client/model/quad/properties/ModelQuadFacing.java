package me.jellysquid.mods.sodium.client.model.quad.properties;

import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;

public enum ModelQuadFacing {
    UP,
    DOWN,
    EAST,
    WEST,
    SOUTH,
    NORTH,
    UNASSIGNED;

    public static final ModelQuadFacing[] VALUES = ModelQuadFacing.values();
    public static final int COUNT = VALUES.length;

    public static ModelQuadFacing fromDirection(ForgeDirection dir) {
        return switch (dir) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> UNASSIGNED;
        };
    }

    public static ForgeDirection toDirection(ModelQuadFacing dir) {
        return switch (dir) {
            case DOWN -> ForgeDirection.DOWN;
            case UP -> ForgeDirection.UP;
            case NORTH -> ForgeDirection.NORTH;
            case SOUTH -> ForgeDirection.SOUTH;
            case WEST -> ForgeDirection.WEST;
            case EAST -> ForgeDirection.EAST;
            default -> ForgeDirection.UNKNOWN;
        };
    }

    public static ModelQuadFacing fromVector(Vector3f normal) {
        if(normal.x == 0f) {
            if(normal.y == 0f) {
                if(normal.z > 0) {
                    return SOUTH;
                } else if(normal.z < 0) {
                    return NORTH;
                }
            } else if(normal.z == 0f) {
                if(normal.y > 0) {
                    return UP;
                } else if(normal.y < 0) {
                    return DOWN;
                }
            }
        } else if(normal.y == 0f && (normal.z == 0f)) {
            if(normal.x > 0) {
                return EAST;
            } else if(normal.x < 0) {
                return WEST;
            }
        }
        return UP; // Default in 1.16.5
    }

    public ModelQuadFacing getOpposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case EAST -> WEST;
            case WEST -> EAST;
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
            default -> UNASSIGNED;
        };
    }
}
