package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidBase;

public final class HiddenFluidTops {
    public static final int SEARCH_RADIUS = 2;
    private static final int SEARCH_WIDTH = 2 * SEARCH_RADIUS + 1;

    private static final int BLOCKED = 0;
    private static final int COVERED = 1;
    private static final int OPEN = 2;
    private static final int[] STEP_X = {1, -1, 0, 0};
    private static final int[] STEP_Z = {0, 0, 1, -1};

    private HiddenFluidTops() {}

    public static boolean isTopHidden(IBlockAccess world, Block fluid, int x, int y, int z, int topOffset) {
        if (world.getBlock(x, y, z) != fluid) return false;
        if (!isFullLevel(world, fluid, x, y, z) || !world.getBlock(x, y + topOffset, z).isOpaqueCube()) return false;
        return !hasOpeningNearby(world, fluid, x, y, z, topOffset);
    }

    private static boolean hasOpeningNearby(IBlockAccess world, Block fluid, int x, int y, int z, int topOffset) {
        int covered = 1 << (SEARCH_RADIUS + SEARCH_RADIUS * SEARCH_WIDTH);
        int checked = 0;
        int blocked = 0;
        int unchecked;
        while ((unchecked = covered & ~checked) != 0) {
            final int cell = Integer.numberOfTrailingZeros(unchecked);
            checked |= 1 << cell;
            final int cellX = cell % SEARCH_WIDTH;
            final int cellZ = cell / SEARCH_WIDTH;
            for (int step = 0; step < 4; step++) {
                final int nextX = cellX + STEP_X[step];
                final int nextZ = cellZ + STEP_Z[step];
                if (nextX < 0 || nextZ < 0 || nextX >= SEARCH_WIDTH || nextZ >= SEARCH_WIDTH) continue;
                final int bit = 1 << (nextX + nextZ * SEARCH_WIDTH);
                if (((covered | blocked) & bit) != 0) continue;
                final int kind = classify(world, fluid, x + nextX - SEARCH_RADIUS, y, z + nextZ - SEARCH_RADIUS, topOffset);
                if (kind == OPEN) return true;
                if (kind == COVERED) {
                    covered |= bit;
                } else {
                    blocked |= bit;
                }
            }
        }
        return false;
    }

    private static int classify(IBlockAccess world, Block fluid, int x, int y, int z, int topOffset) {
        final Block block = world.getBlock(x, y, z);
        if (block.isOpaqueCube()) return BLOCKED;
        if (!isSameFluid(fluid, block)) return OPEN;
        final Block blockOnTop = world.getBlock(x, y + topOffset, z);
        if (isSameFluid(fluid, blockOnTop)) return BLOCKED;
        return blockOnTop.isOpaqueCube() && isFullLevel(world, fluid, x, y, z) ? COVERED : OPEN;
    }

    private static boolean isSameFluid(Block fluid, Block other) {
        if (fluid instanceof BlockLiquid) return other instanceof BlockLiquid && other.getMaterial() == fluid.getMaterial();
        return other == fluid;
    }

    private static boolean isFullLevel(IBlockAccess world, Block fluid, int x, int y, int z) {
        final int meta = world.getBlockMetadata(x, y, z);
        return fluid instanceof BlockFluidBase forgeFluid ? meta == forgeFluid.getMaxRenderHeightMeta() : meta == 0;
    }
}
