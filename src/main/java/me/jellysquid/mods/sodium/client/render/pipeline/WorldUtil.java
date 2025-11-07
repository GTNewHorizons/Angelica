package me.jellysquid.mods.sodium.client.render.pipeline;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import org.joml.Vector3d;

/**
 * Contains methods stripped from BlockState or FluidState that didn't actually need to be there. Technically these
 * could be a mixin to Block or Fluid, but that's annoying while not actually providing any benefit.
 */
public class WorldUtil {

    public static Vector3d getVelocity(IBlockAccess world, int x, int y, int z, Block thizz) {

        Vector3d velocity = new Vector3d();
        int decay = getEffectiveFlowDecay(world, x, y, z, thizz);

        for (var dire : ModelQuadFacing.HORIZONTAL_DIRECTIONS) {

            int adjX = x + dire.getStepX();
            int adjZ = z + dire.getStepZ();

            int adjDecay = getEffectiveFlowDecay(world, adjX, y, adjZ, thizz);

            if (adjDecay < 0) {

                if (!world.getBlock(adjX, y, adjZ).getMaterial().blocksMovement()) {

                    adjDecay = getEffectiveFlowDecay(world, adjX, y - 1, adjZ, thizz);

                    if (adjDecay >= 0) {

                        adjDecay -= (decay - 8);
                        velocity = velocity.add((adjX - x) * adjDecay, 0, (adjZ - z) * adjDecay);
                    }
                }
            } else {

                adjDecay -= decay;
                velocity = velocity.add((adjX - x) * adjDecay, 0, (adjZ - z) * adjDecay);
            }
        }

        if (world.getBlockMetadata(x, y, z) >= 8) {

            if (thizz.isBlockSolid(world, x, y, z - 1, 2) || thizz.isBlockSolid(world, x, y, z + 1, 3)
                    || thizz.isBlockSolid(world, x - 1, y, z, 4)
                    || thizz.isBlockSolid(world, x + 1, y, z, 5)
                    || thizz.isBlockSolid(world, x, y + 1, z - 1, 2)
                    || thizz.isBlockSolid(world, x, y + 1, z + 1, 3)
                    || thizz.isBlockSolid(world, x - 1, y + 1, z, 4)
                    || thizz.isBlockSolid(world, x + 1, y + 1, z, 5)) {
                velocity = velocity.normalize().add(0.0D, -6.0D, 0.0D);
            }
        }

        if (velocity.x == 0 && velocity.y == 0 && velocity.z == 0) return velocity.zero();
        return velocity.normalize();
    }

    /**
     * Returns true if any block in a 3x3x3 cube is not the same fluid and not an opaque full cube. Equivalent to
     * FluidState::method_15756 in modern.
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

    /**
     * Returns fluid height as a percentage of the block; 0 is none and 1 is full.
     */
    public static float getFluidHeight(Fluid fluid, int meta) {
        return fluid == null ? 0 : 1 - BlockLiquid.getLiquidHeightPercent(meta);
    }

    /**
     * Returns the flow decay but converts values indicating falling liquid (values >=8) to their effective source block
     * value of zero
     */
    public static int getEffectiveFlowDecay(IBlockAccess world, int x, int y, int z, Block thiz) {

        if (world.getBlock(x, y, z).getMaterial() != thiz.getMaterial()) {

            return -1;
        } else {

            int decay = world.getBlockMetadata(x, y, z);
            return decay >= 8 ? 0 : decay;
        }
    }

    // I believe forge mappings in modern say BreakableBlock, while yarn says TransparentBlock.
    // I have a sneaking suspicion isOpaque is neither, but it works for now
    public static boolean shouldDisplayFluidOverlay(Block block) {
        return !block.getMaterial().isOpaque() || block.getMaterial() == Material.leaves;
    }

    public static boolean isFluidBlock(Block block) {
        return block instanceof IFluidBlock || block instanceof BlockLiquid;
    }

    public static Fluid getFluid(Block b) {
        if (b instanceof IFluidBlock fluidBlock) return fluidBlock.getFluid();
        if (b instanceof BlockLiquid) {
            if (b.getMaterial() == Material.water) return FluidRegistry.WATER;
            if (b.getMaterial() == Material.lava) return FluidRegistry.LAVA;
        }
        return null;
    }

    /**
     * Equivalent to method_15748 in 1.16.5
     */
    public static boolean isEmptyOrSame(Fluid fluid, Fluid otherFluid) {
        return otherFluid == null || fluid == otherFluid;
    }

    /**
     * Equivalent to method_15749 in 1.16.5
     */
    public static boolean method_15749(IBlockAccess world, Fluid thiz, BlockPos pos, ForgeDirection dir) {
        Block b = world.getBlock(pos.x, pos.y, pos.z);
        Fluid f = getFluid(b);
        if (f == thiz) {
            return false;
        }
        if (dir == ForgeDirection.UP) {
            return true;
        }
        return b.getMaterial() != Material.ice && b.isSideSolid(world, pos.x, pos.y, pos.z, dir);
    }
}
