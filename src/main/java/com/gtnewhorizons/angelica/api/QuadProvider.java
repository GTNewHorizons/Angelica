package com.gtnewhorizons.angelica.api;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

public interface QuadProvider {

    int R_MASK = 0xFF << 16;
    int G_MASK = 0xFF << 8;
    int B_MASK = 0xFF;

    /**
     * Returns the color packed as ABGR. If you want to vary colors within a block, just ignore the color passed into
     * {@link #getQuads} and use whatever you want. You might want to override this to return a constant if you're
     * extending a block with an expensive color call, like leaves.
     */
    default int getColor(IBlockAccess world, BlockPos pos, Block block, int meta, Random random) {

        final int cin = block.colorMultiplier(world, pos.getX(), pos.getY(), pos.getZ());
        return (0xFF << 24) | ((cin & B_MASK) << 16) | (cin & G_MASK) | ((cin & R_MASK) >>> 16);
    }

    /**
     * If you need to allocate new quads, set this to true. If true, the quads returned by {@link #getQuads} are
     * recycled, and you should not keep a reference to them. Example: stone can return a static list every time, but a
     * modded block which adds or removes quads based on location would need dynamic quads.
     */
    default boolean isDynamic() {
        return false;
    }

    /**
     * Provide quads to render. If you need new quads, they should be obtained with the passed supplier and
     * {@link #isDynamic} overridden to return true. If so, all quads in the list are recycled and references to them
     * should not be kept.
     */
    List<QuadView> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool);
}
