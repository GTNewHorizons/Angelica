package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.twilightforest;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.ILeafBlock;
import net.minecraft.block.BlockLeaves;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import twilightforest.block.BlockTFMagicLeaves;

@Mixin(value = BlockTFMagicLeaves.class)
public abstract class MixinBlockTFMagicLeaves extends BlockLeaves {
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
        return switch (maskedMeta) {
            case 1 -> renderMode == 1 ? SPR_TRANSLEAVES_OPAQUE : SPR_TRANSLEAVES;
            case 3 -> renderMode == 1 ? SPR_SORTLEAVES_OPAQUE : SPR_SORTLEAVES;
            default -> renderMode == 1 ? SPR_TIMELEAVES_OPAQUE : SPR_TIMELEAVES;
        };
    }

    @Shadow(remap = false)
    public static IIcon SPR_TIMELEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_TIMELEAVES_OPAQUE;
    @Shadow(remap = false)
    public static IIcon SPR_TRANSLEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_TRANSLEAVES_OPAQUE;
    @Shadow(remap = false)
    public static IIcon SPR_SORTLEAVES;
    @Shadow(remap = false)
    public static IIcon SPR_SORTLEAVES_OPAQUE;

}
