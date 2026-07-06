package com.gtnewhorizons.angelica.mixins.early.notfine.leaves;

import jss.notfine.util.IFaceObstructionCheckHelper;
import jss.notfine.util.ILeafBlock;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BlockLeavesBase.class)
public abstract class MixinBlockLeavesBase extends Block implements IFaceObstructionCheckHelper, ILeafBlock {

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return LeafRenderUtil.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override()
    public boolean isFaceNonObstructing(IBlockAccess world, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        return LeafRenderUtil.isFaceNonObstructing(world, x, y, z);
    }

    private MixinBlockLeavesBase(Material material) {
        super(material);
    }

}
