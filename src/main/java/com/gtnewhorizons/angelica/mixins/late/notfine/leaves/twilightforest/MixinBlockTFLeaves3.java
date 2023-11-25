package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.twilightforest;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.ILeafBlock;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import twilightforest.block.BlockTFLeaves3;

@Mixin(value = BlockTFLeaves3.class)
public abstract class MixinBlockTFLeaves3 extends BlockLeaves {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int renderMode = (int)Settings.MODE_LEAVES.getValue();
        renderMode = switch (renderMode) {
            case -1 -> SettingsManager.leavesOpaque ? 1 : 0;
            case 4 -> world.getBlock(
                x + Facing.offsetsXForSide[side],
                y + Facing.offsetsYForSide[side],
                z + Facing.offsetsZForSide[side]
            ) instanceof ILeafBlock ? 1 : 0;
            default -> renderMode > 1 ? 0 : renderMode;
        };
        return Blocks.leaves.field_150129_M[renderMode][0];
    }

}
