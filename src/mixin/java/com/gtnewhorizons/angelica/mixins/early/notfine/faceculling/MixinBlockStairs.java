package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockStairs.class)
public abstract class MixinBlockStairs extends Block implements IFaceObstructionCheckHelper {

    @Override()
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        if(getRenderBlockPass() == 1) {
            return true;
        }
        boolean isTop = (worldIn.getBlockMetadata(x, y, z) & 7) > 3;
        return switch (side) {
            // -y bottom
            case 0 -> !isTop;
            // +y top
            case 1 -> isTop;
            default -> isTop ? otherMinY < 0.5D : otherMaxY > 0.5D;
        };
    }

    MixinBlockStairs(Material materialIn) {
        super(materialIn);
    }

}
