package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.tconstruct;

import jss.notfine.core.Settings;
import jss.notfine.core.SettingsManager;
import jss.notfine.gui.options.named.LeavesQuality;
import jss.notfine.util.IFaceObstructionCheckHelper;
import jss.notfine.util.ILeafBlock;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import tconstruct.world.blocks.OreberryBush;

@Mixin(value = OreberryBush.class)
public abstract class MixinOreberryBush extends Block implements IFaceObstructionCheckHelper, ILeafBlock {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        final int metadata = world.getBlockMetadata(x, y, z);
        int maskedMeta = metadata % 4 + (metadata < 12 ? 0 : 4);
        boolean renderMode;
        if(Settings.MODE_LEAVES.option.getStore() == LeavesQuality.SHELLED_FAST) {
            if(metadata < 8) {
                renderMode = false;
            } else {
                x += Facing.offsetsXForSide[side];
                y += Facing.offsetsYForSide[side];
                z += Facing.offsetsZForSide[side];
                final Block otherBlock = world.getBlock(x, y, z);
                renderMode = otherBlock instanceof ILeafBlock leafBlock && leafBlock.isFullLeaf(world, x, y, z);
            }
        } else {
            renderMode = SettingsManager.leavesOpaque;
        }
        maskedMeta = maskedMeta >= (renderMode ? fastIcons : fancyIcons).length ? 0 : maskedMeta;
        return (renderMode ? fastIcons : fancyIcons)[maskedMeta];
    }

    /**
     * @author jss2a98aj
     * @reason  Support new leaf rendering modes on Tinker's Construct leaves.
     */
    @Override
    @Overwrite
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        if (side != 0 && maxY < 1f) {
            return true;
        }
        return LeafRenderUtil.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override
    public boolean isFaceNonObstructing(IBlockAccess world, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        if(world.getBlockMetadata(x ,y, z) < 8) {
            return true;
        }
        return LeafRenderUtil.isFaceNonObstructing(world, x, y, z);
    }

    @Override
    public boolean isFullLeaf(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x ,y, z) >= 8;
    }

    @Shadow(remap = false)
    public IIcon[] fastIcons;
    @Shadow(remap = false)
    public IIcon[] fancyIcons;

    private MixinOreberryBush(Material material) {
        super(material);
    }

}
