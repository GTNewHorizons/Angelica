package com.gtnewhorizons.angelica.mixins.late.notfine.faceculling.thaumcraft;

import jss.notfine.util.IFaceObstructionCheckHelper;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.common.blocks.BlockWoodenDevice;

@Mixin(value = BlockWoodenDevice.class)
public abstract class MixinBlockWoodenDevice implements IFaceObstructionCheckHelper {

    @Override
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        return switch (worldIn.getBlockMetadata(x, y, z)) {
            case 1 -> side != 1;
            case 4 -> side > 1;
            case 6, 7 -> false;
            default -> true;
        };
    }

}
