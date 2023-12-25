package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.thaumcraft;

import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.ILeafBlock;
import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import thaumcraft.common.blocks.BlockMagicalLeaves;

@Mixin(value = BlockMagicalLeaves.class)
public abstract class MixinBlockMagicalLeaves extends Block implements ILeafBlock {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int renderMode = ((LeavesQuality)Settings.MODE_LEAVES.option.getStore()).ordinal() - 1;
        int maskedMeta = world.getBlockMetadata(x, y, z) & 3;
        renderMode = switch (renderMode) {
            case -1 -> SettingsManager.leavesOpaque ? 1 : 0;
            case 4 -> world.getBlock(
                x + Facing.offsetsXForSide[side],
                y + Facing.offsetsYForSide[side],
                z + Facing.offsetsZForSide[side]
            ) instanceof ILeafBlock ? 1 : 0;
            default -> renderMode > 1 ? 0 : renderMode;
        };
        maskedMeta = maskedMeta > 1 ? 0 : maskedMeta;
        return icon[renderMode + maskedMeta * 2];
    }

    /**
     * @author jss2a98aj
     * @reason Support new leaf rendering modes on Thaumcraft leaves.
     */
    @Overwrite
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return LeafRenderUtil.shouldSideBeRendered(world, x, y, z, side);
    }

    private MixinBlockMagicalLeaves(Material material) {
        super(material);
    }

    @Shadow(remap = false)
    public IIcon[] icon;

}
