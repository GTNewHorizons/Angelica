package me.jellysquid.mods.sodium.common.util;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.VoxelShape;
import com.gtnewhorizons.angelica.compat.mojang.VoxelShapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.IFluidBlock;
import org.joml.Vector3d;

/**
 * Contains methods stripped from BlockState or FluidState that didn't actually need to be there. Technically these
 * could be a mixin to Block or Fluid, but that's annoying while not actually providing any benefit.
 */
public class WorldUtil {

    public static Vector3d getVelocity(IBlockAccess world, BlockPos pos, Fluid fluid) {

        Vector3d velocity = new Vector3d();
        int meta = world.getBlockMetadata(pos.x, pos.y, pos.z);

        BlockPos target = new BlockPos.Mutable();
        target.set(pos);

        // for each orthogonally adjacent fluid, add the height delta
        for (ForgeDirection d : DirectionUtil.HORIZONTAL_DIRECTIONS) {

            target.add(d.offsetX, 0, d.offsetZ);
            Block oBlock = world.getBlock(target.x, target.y, target.z);
            Fluid oFluid = getFluid(oBlock);
            int oMeta = world.getBlockMetadata(target.x, target.y, target.z);

            if (!isEmptyOrSame(fluid, oFluid)) continue;

            float oHeight = getFluidHeight(oFluid, oMeta);
            float delta = 0.0f;

            if (oHeight == 0.0f) {

                BlockPos loTarget = target.down();
                Fluid loFluid = getFluid(world.getBlock(loTarget.x, loTarget.y, loTarget.z));
                int loMeta = world.getBlockMetadata(loTarget.x, loTarget.y, loTarget.z);
                oHeight = getFluidHeight(loFluid, loMeta);
                if (!oBlock.getMaterial().blocksMovement() && isEmptyOrSame(fluid, loFluid) && oHeight > 0.0f) {
                    delta = getFluidHeight(fluid, meta) - oHeight + 0.9f;
                }
            } else if (oHeight > 0.0f) {
                delta = getFluidHeight(fluid, meta) - oHeight;
            }

            if (delta == 0.0f) continue;

            velocity.add(d.offsetX * delta, 0, d.offsetZ * delta);
            target.set(pos.x, pos.y, pos.z);
        }

        if (velocity.x == velocity.z && velocity.x == velocity.z)
            return velocity.zero();
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
                if (!block.isOpaqueCube() && getFluid(block) != fluid) {
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

    public static Fluid getFluid(Block b) {
        return b instanceof IFluidBlock ? ((IFluidBlock) b).getFluid() : null;
    }

    /**
     * Equivalent to method_15748 in 1.16.5
     */
    public static boolean isEmptyOrSame(Fluid fluid, Fluid otherFluid) {
        return otherFluid == null || fluid == otherFluid;
    }
}
