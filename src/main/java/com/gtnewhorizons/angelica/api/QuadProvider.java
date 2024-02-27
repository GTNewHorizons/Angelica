package com.gtnewhorizons.angelica.api;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.nd.Quad;
import com.gtnewhorizons.angelica.utils.ObjectPooler;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.Random;

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

        final int cin = block.colorMultiplier(world, pos.x, pos.y, pos.z);
        return (0xFF << 24) | ((cin & B_MASK) << 16) | (cin & G_MASK) | ((cin & R_MASK) >>> 16);
    }

    List<Quad> getQuads(IBlockAccess world, BlockPos pos, Block block, int meta, ForgeDirection dir, Random random, int color, ObjectPooler<Quad> quadPool);
}
