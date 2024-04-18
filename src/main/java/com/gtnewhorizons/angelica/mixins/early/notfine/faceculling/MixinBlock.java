package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = Block.class)
public abstract class MixinBlock {

    /**
     * @author jss2a98aj
     * @reason More accurate face culling.
     */
    @Overwrite()
    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        //Check if side is touching another block
        switch(side) {
            case 0 -> {
                if (minY > 0.0D) {
                    return true;
                }
            }
            case 1 -> {
                if (maxY < 1.0D) {
                    return true;
                }
            }
            case 2 -> {
                if (minZ > 0.0D) {
                    return true;
                }
            }
            case 3 -> {
                if (maxZ < 1.0D) {
                    return true;
                }
            }
            case 4 -> {
                if (minX > 0.0D) {
                    return true;
                }
            }
            case 5 -> {
                if (maxX < 1.0D) {
                    return true;
                }
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

    @Shadow protected double minX;
    @Shadow protected double minY;
    @Shadow protected double minZ;
    @Shadow protected double maxX;
    @Shadow protected double maxY;
    @Shadow protected double maxZ;

}
