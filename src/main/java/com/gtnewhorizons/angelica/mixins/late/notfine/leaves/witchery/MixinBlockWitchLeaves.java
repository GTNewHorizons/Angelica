package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.witchery;

import com.emoniph.witchery.blocks.BlockWitchLeaves;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.ILeafBlock;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockWitchLeaves.class)
public abstract class MixinBlockWitchLeaves extends BlockLeavesBase {

    /**
     * @author jss2a98aj
     * @reason Support new leaf rendering modes on Witchery leaves.
     */
    @Overwrite
    public boolean isOpaqueCube() {
        return SettingsManager.leavesOpaque;
    }

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
        return iconsForModes[renderMode][maskedMeta];
    }

    @Shadow(remap = false)
    private IIcon[][] iconsForModes;

    private MixinBlockWitchLeaves(Material material, boolean unused) {
        super(material, unused);
    }

}
