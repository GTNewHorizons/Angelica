package com.gtnewhorizons.angelica.mixins.early.notfine.leaves;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.ILeafBlock;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockLeaves.class)
public abstract class MixinBlockLeaves extends BlockLeavesBase {

    /**
     * @author jss2a98aj
     * @reason Control leaf opacity.
     */
    @Overwrite
    public boolean isOpaqueCube() {
        return SettingsManager.leavesOpaque;
    }

    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if(field_150129_M[0] == null) {
            //A mod dev had no idea what they were doing.
            return getIcon(side, world.getBlockMetadata(x, y, z));
        }
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
        maskedMeta = maskedMeta >= field_150129_M[renderMode].length ? 0 : maskedMeta;
        return field_150129_M[renderMode][maskedMeta];
    }

    @Shadow protected IIcon[][] field_150129_M;

    private MixinBlockLeaves(Material material, boolean overridden) {
        super(material, overridden);
    }

}
