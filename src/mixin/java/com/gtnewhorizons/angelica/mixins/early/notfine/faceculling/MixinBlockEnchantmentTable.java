package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEnchantmentTable;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockEnchantmentTable.class)
public abstract class MixinBlockEnchantmentTable extends Block implements IFaceObstructionCheckHelper {

    /**
     * @author jss2a98aj
     * @reason More accurate face culling.
     */
    @Override
    public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        //If this is the top
        if(side == 1) {
            return true;
        }
        //Check if other block is solid
        Block otherBlock = worldIn.getBlock(x, y, z);
        if(otherBlock.isOpaqueCube()) {
            return false;
        }
        //Check for IFaceObstructionCheckHelper
        if(otherBlock instanceof IFaceObstructionCheckHelper target) {
            return target.isFaceNonObstructing(worldIn, x, y, z, side, 0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 1.0F);
        }
        //Default
        return true;
    }

    @Override
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        if(getRenderBlockPass() == 1) {
            return true;
        }
        if(side <= 1) {
            return side == 0;
        }
        return otherMaxY > 0.75F;
    }

    private MixinBlockEnchantmentTable(Material material) {
        super(material);
    }

}
