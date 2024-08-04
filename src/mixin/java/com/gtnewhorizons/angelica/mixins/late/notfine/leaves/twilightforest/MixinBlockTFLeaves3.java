package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.twilightforest;

import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import twilightforest.block.BlockTFLeaves3;

@Mixin(value = BlockTFLeaves3.class)
public abstract class MixinBlockTFLeaves3 extends BlockLeaves {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        final int renderMode = LeafRenderUtil.selectRenderMode(world, x, y, z, side) ? 1 : 0;
        return Blocks.leaves.field_150129_M[renderMode][0];
    }

}
