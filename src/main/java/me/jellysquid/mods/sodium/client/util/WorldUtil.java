package me.jellysquid.mods.sodium.client.util;

import com.gtnewhorizons.angelica.compat.mojang.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.joml.Vector3d;

/**
 * Contains methods stripped from BlockState or FluidState that didn't actually need to be there. Technically these
 * could be a mixin to Block or Fluid, but that's annoying while not actually providing any benefit.
 */
public class WorldUtil {

    public static Vector3d getVelocity(IBlockAccess world, BlockPos pos) {

        Vector3d velocity = new Vector3d();
        ForgeDirection[] news = new ForgeDirection[]{ ForgeDirection.NORTH, ForgeDirection.EAST, ForgeDirection.WEST, ForgeDirection.SOUTH };
        BlockPos target = new BlockPos.Mutable();
        target.set(pos);
        Fluid thisFluid = FluidRegistry.lookupFluidForBlock(world.getBlock(pos.x, pos.y, pos.z));
        int meta = world.getBlockMetadata(pos.x, pos.y, pos.z);

        // for each orthogonally adjacent fluid, add the height delta
        for (ForgeDirection d : news) {

            target.add(d.offsetX, 0, d.offsetZ);
            Fluid orthoFluid = FluidRegistry.lookupFluidForBlock(world.getBlock(pos.x, pos.y, pos.z));
            int orthoMeta = world.getBlockMetadata(pos.x, pos.y, pos.z);
            double mult;

            // blocks always add 0.9, for some reason
            if (orthoFluid == null) {
                mult = 0.9;
            } else {

                mult = WorldUtil.getFluidHeight(thisFluid, meta) - WorldUtil.getFluidHeight(orthoFluid, orthoMeta);
            }

            velocity.add(d.offsetX * mult, 0, d.offsetZ * mult);
            target.add(-d.offsetX, 0, -d.offsetZ);
        }

        return velocity.normalize();
    }

    /**
     * Returns true if any block in a 3x3x3 cube is not the same fluid and not an opaque full cube.
     * Equivalent to FluidState::method_15756 in modern.
     */
    public static boolean method_15756(IBlockAccess world, BlockPos pos, Fluid fluid) {
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                Block block = world.getBlock(pos.x, pos.y, pos.z);
                if (!block.isOpaqueCube() && FluidRegistry.lookupFluidForBlock(block) != fluid) {
                    return true;
                }
            }
        }

        return false;
    }

    public static VoxelShape getCullingShape(Block block) {
        return block.renderAsNormalBlock() ? VoxelShapes.fullCube() : VoxelShapes.empty();
    }

    /**
     * Returns fluid height as a percentage of the block; 0 is none and 1 is full.
     */
    public static float getFluidHeight(Fluid fluid, int meta) {
        return fluid == null ? 0 : 1 - BlockLiquid.getLiquidHeightPercent(meta);
    }

    // I believe forge mappings in modern say BreakableBlock, while yarn says TransparentBlock.
    // I have a sneaking suspicion isOpaque is neither, but it works for now
    public static boolean shouldDisplayFluidOverlay(Block block) {
        return !block.getMaterial().isOpaque() || block.getMaterial() == Material.leaves;
    }
}
