package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.twilightforest;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.ILeafBlock;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import twilightforest.block.BlockTFLeaves;

@Mixin(value = BlockTFLeaves.class)
public abstract class MixinBlockTFLeaves extends BlockLeaves {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int maskedMeta = world.getBlockMetadata(x, y, z) & 3;
        int renderMode;
        if(Settings.MODE_LEAVES.option.getStore() == LeavesQuality.SHELLED_FAST) {
            renderMode = world.getBlock(
                x + Facing.offsetsXForSide[side],
                y + Facing.offsetsYForSide[side],
                z + Facing.offsetsZForSide[side]
            ) instanceof ILeafBlock ? 1 : 0;
        } else {
            renderMode = SettingsManager.leavesOpaque ? 1 : 0;
        }
        maskedMeta = maskedMeta > 1 ? 0 : maskedMeta;
        return Blocks.leaves.field_150129_M[renderMode][maskedMeta];
    }

}
