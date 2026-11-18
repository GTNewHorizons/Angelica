package com.gtnewhorizons.angelica.compat.thaumcraft;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import thaumcraft.common.config.ConfigBlocks;

import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_X;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Y;
import static com.gtnewhorizons.angelica.compat.thaumcraft.PortalRenderer.AXIS_Z;

/**
 * Which of a portable hole's or eldritch nothing's six planes TC draws, and where.
 */
public final class PortalFaces {
    public static final int NOT_BUILT = -1;

    private static final int UP = 1;
    private static final int DOWN = 2;
    private static final int NORTH = 4;
    private static final int SOUTH = 8;
    private static final int WEST = 16;
    private static final int EAST = 32;

    private static final float HOLE_NEAR = 0.001F;
    private static final float HOLE_FAR = 0.999F;
    private static final float HOLE_FAR_GRAY = 0.5F;
    private static final float NOTHING_FAR_GRAY = 1.0F;

    private PortalFaces() {
    }

    public static int holeMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        if (isHoleWall(world.getBlock(x, y + 1, z))) mask |= UP;
        if (isHoleWall(world.getBlock(x, y - 1, z))) mask |= DOWN;
        if (isHoleWall(world.getBlock(x, y, z - 1))) mask |= NORTH;
        if (isHoleWall(world.getBlock(x, y, z + 1))) mask |= SOUTH;
        if (isHoleWall(world.getBlock(x - 1, y, z))) mask |= WEST;
        if (isHoleWall(world.getBlock(x + 1, y, z))) mask |= EAST;
        return mask;
    }

    public static int nothingMask(IBlockAccess world, int x, int y, int z) {
        int mask = 0;
        if (!world.getBlock(x, y + 1, z).isOpaqueCube()) mask |= UP;
        if (!world.getBlock(x, y - 1, z).isOpaqueCube()) mask |= DOWN;
        if (!world.getBlock(x, y, z - 1).isOpaqueCube()) mask |= NORTH;
        if (!world.getBlock(x, y, z + 1).isOpaqueCube()) mask |= SOUTH;
        if (!world.getBlock(x - 1, y, z).isOpaqueCube()) mask |= WEST;
        if (!world.getBlock(x + 1, y, z).isOpaqueCube()) mask |= EAST;
        return mask;
    }

    private static boolean isHoleWall(Block block) {
        return block.isOpaqueCube() && block != ConfigBlocks.blockHole;
    }

    public static void addHoleFaces(int mask, boolean layered, double x, double y, double z) {
        if ((mask & UP) != 0) {
            final double plane = y + HOLE_FAR;
            face(layered, HOLE_FAR_GRAY, AXIS_Y, false, plane, x, plane, z + 1.0, x, plane, z, x + 1.0, plane, z + 1.0);
        }
        if ((mask & DOWN) != 0) {
            final double plane = y + HOLE_NEAR;
            face(layered, HOLE_FAR_GRAY, AXIS_Y, true, plane, x, plane, z, x, plane, z + 1.0, x + 1.0, plane, z);
        }
        if ((mask & NORTH) != 0) {
            final double plane = z + HOLE_NEAR;
            face(layered, HOLE_FAR_GRAY, AXIS_Z, true, plane, x, y + 1.0, plane, x, y, plane, x + 1.0, y + 1.0, plane);
        }
        if ((mask & SOUTH) != 0) {
            final double plane = z + HOLE_FAR;
            face(layered, HOLE_FAR_GRAY, AXIS_Z, false, plane, x, y, plane, x, y + 1.0, plane, x + 1.0, y, plane);
        }
        if ((mask & WEST) != 0) {
            final double plane = x + HOLE_NEAR;
            face(layered, HOLE_FAR_GRAY, AXIS_X, true, plane, plane, y + 1.0, z, plane, y + 1.0, z + 1.0, plane, y, z);
        }
        if ((mask & EAST) != 0) {
            final double plane = x + HOLE_FAR;
            face(layered, HOLE_FAR_GRAY, AXIS_X, false, plane, plane, y, z, plane, y, z + 1.0, plane, y + 1.0, z);
        }
    }

    public static void addNothingFaces(int mask, boolean layered, double x, double y, double z) {
        if ((mask & UP) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_Y, true, y + 1.0, x, y + 1.0, z, x, y + 1.0, z + 1.0, x + 1.0, y + 1.0, z);
        }
        if ((mask & DOWN) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_Y, false, y, x, y, z + 1.0, x, y, z, x + 1.0, y, z + 1.0);
        }
        if ((mask & NORTH) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_Z, false, z, x, y, z, x, y + 1.0, z, x + 1.0, y, z);
        }
        if ((mask & SOUTH) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_Z, true, z + 1.0, x, y + 1.0, z + 1.0, x, y, z + 1.0, x + 1.0, y + 1.0, z + 1.0);
        }
        if ((mask & WEST) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_X, false, x, x, y, z, x, y, z + 1.0, x, y + 1.0, z);
        }
        if ((mask & EAST) != 0) {
            face(layered, NOTHING_FAR_GRAY, AXIS_X, true, x + 1.0, x + 1.0, y + 1.0, z, x + 1.0, y + 1.0, z + 1.0, x + 1.0, y, z);
        }
    }

    private static void face(boolean layered, float farGray, int axis, boolean negative, double plane, double x0, double y0, double z0, double x1, double y1, double z1, double x3, double y3, double z3) {
        if (layered) {
            PortalRenderer.addFace(false, axis, negative, plane, x0, y0, z0, x1, y1, z1, x3, y3, z3);
        } else {
            PortalRenderer.addFarFace(axis, farGray, x0, y0, z0, x1, y1, z1, x3, y3, z3);
        }
    }
}
