package com.gtnewhorizons.angelica.mixins.late.notfine.faceculling.thaumcraft;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.common.blocks.BlockTable;

@Mixin(value = BlockTable.class)
public abstract class MixinBlockTable implements IFaceObstructionCheckHelper {

    @Override
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        switch(side) {
            case 0: return false;
            case 1: {
                return worldIn.getBlockMetadata(x, y, z) < 14;
            }
            default: {
                float lowerBound = 0.25f;
                if(worldIn.getBlockMetadata(x, y, z) >= 14) {
                    lowerBound = 0.5f;
                    if(otherMaxY <= 0.25f) {
                        return false;
                    }
                }
                return otherMinY < lowerBound;
            }
        }
    }

}
