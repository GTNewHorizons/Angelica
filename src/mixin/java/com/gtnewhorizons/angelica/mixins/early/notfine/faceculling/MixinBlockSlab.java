package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockSlab.class)
public abstract class MixinBlockSlab extends Block implements IFaceObstructionCheckHelper {

    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        //If the slab is not a full cube and this is up or down
        if(!field_150004_a && side <= 1) {
            int thisX = x + Facing.offsetsXForSide[Facing.oppositeSide[side]];
            int thisY = y + Facing.offsetsYForSide[Facing.oppositeSide[side]];
            int thisZ = z + Facing.offsetsZForSide[Facing.oppositeSide[side]];
            boolean isTop = (worldIn.getBlockMetadata(thisX, thisY, thisZ) & 8) != 0;
            if(isTop && side == 0 || !isTop && side == 1) {
                return true;
            }
        }
        //Check if other block is solid
        Block otherBlock = worldIn.getBlock(x, y, z);
        if(otherBlock.isOpaqueCube()) {
            return false;
        }
        //Check for IFaceObstructionCheckHelper
        if(otherBlock instanceof IFaceObstructionCheckHelper target) {
            return target.isFaceNonObstructing(worldIn, x, y, z, side, minX, minY, minZ, maxX, maxY, maxZ);
        }
        //Default
        return true;
    }

    /**
     * @author jss2a98aj
     * @reason Improved accuracy in the case a mod ever uses this.
     */
    @Overwrite()
    private static boolean func_150003_a(Block block) {
        return block.getRenderBlockPass() == 0 && block instanceof BlockSlab;
    }

    @Override()
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        if(getRenderBlockPass() == 1) {
            return true;
        }
        boolean isTop = (worldIn.getBlockMetadata(x, y, z) & 8) != 0;
        return switch (side) {
            // -y bottom
            case 0 -> !isTop;
            // +y top
            case 1 -> isTop;
            default -> isTop ? otherMinY < 0.5D : otherMaxY > 0.5D;
        };
    }

    MixinBlockSlab(Material material) {
        super(material);
    }

    @Shadow @Final protected boolean field_150004_a;
}
