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
        switch (dir) {
            case DOWN:
                return DOWN;
            case UP:
                return UP;
            case NORTH:
                return NORTH;
            case SOUTH:
                return SOUTH;
            case WEST:
                return WEST;
            case EAST:
                return EAST;
            default:
                return UNASSIGNED;
        }
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
        return UNASSIGNED;
    }

    public ModelQuadFacing getOpposite() {
        switch (this) {
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            case EAST:
                return WEST;
            case WEST:
                return EAST;
            case SOUTH:
                return NORTH;
            case NORTH:
                return SOUTH;
            default:
                return UNASSIGNED;
        }
    }
}
