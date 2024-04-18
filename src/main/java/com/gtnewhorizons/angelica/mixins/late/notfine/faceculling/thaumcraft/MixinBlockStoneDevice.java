package com.gtnewhorizons.angelica.mixins.late.notfine.faceculling.thaumcraft;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.common.blocks.BlockStoneDevice;

@Mixin(value = BlockStoneDevice.class)
public abstract class MixinBlockStoneDevice implements IFaceObstructionCheckHelper {

    @Override
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        return switch (worldIn.getBlockMetadata(x, y, z)) {
            case 0, 12 -> false;
            case 1, 5, 9, 10, 14 -> side != 1;
            case 11 -> side != 0;
            case 13 -> side > 1;
            default -> true;
        };
    }

}
