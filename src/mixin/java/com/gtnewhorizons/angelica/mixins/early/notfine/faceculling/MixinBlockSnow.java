package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import jss.notfine.util.IFaceObstructionCheckHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;

@Mixin(value = BlockSnow.class)
public abstract class MixinBlockSnow extends Block implements IFaceObstructionCheckHelper {

	/**
	 * @author jss2a98aj
	 * @reason More accurate face culling.
	 */
	@Override
    @Overwrite()
	@SideOnly(Side.CLIENT)
	public boolean shouldSideBeRendered(IBlockAccess worldIn, int x, int y, int z, int side) {
        //If this is the top and this is not full height
        if(side == 1 && maxY < 1.0D) {
            return true;
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

    @Override()
    public boolean isFaceNonObstructing(IBlockAccess worldIn, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        if(getRenderBlockPass() == 1) {
            return true;
        }
        int meta = worldIn.getBlockMetadata(x, y, z) & 7;
        return switch (side) {
            // +y top
            case 0 -> meta != 7;
            // -y bottom
            case 1 -> false;
            default -> otherMaxY > (float) (2 * (1 + meta)) * 0.0625f;
        };
    }

    MixinBlockSnow(Material material) {
        super(material);
    }

}

