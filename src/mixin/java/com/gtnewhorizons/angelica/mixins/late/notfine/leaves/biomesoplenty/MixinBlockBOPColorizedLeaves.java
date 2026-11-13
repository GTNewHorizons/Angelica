package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.biomesoplenty;

import biomesoplenty.common.blocks.BlockBOPColorizedLeaves;
import biomesoplenty.common.blocks.BlockBOPColorizedLeaves.ColourizedLeafCategory;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockBOPColorizedLeaves.class)
public abstract class MixinBlockBOPColorizedLeaves extends BlockLeavesBase {

    @Shadow(remap = false)
    private IIcon[][] textures;
    @Shadow(remap = false)
    @Final
    private static String[] leaves;

    @Shadow(remap = false)
    @Final
    private ColourizedLeafCategory category;

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        final int meta = world.getBlockMetadata(x, y, z);
        int type = (meta & 3) + category.ordinal() * 4;
        if (type >= leaves.length) {
            type = 0;
        }
        return textures[LeafRenderUtil.selectRenderMode(world, x, y, z, side) ? 1 : 0][type];
    }

    /**
     * @author NotFine
     * @reason Support new leaf rendering modes on Biomes O' Plenty leaves.
     */
    @Overwrite
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return LeafRenderUtil.shouldSideBeRendered(world, x, y, z, side);
    }

    private MixinBlockBOPColorizedLeaves(Material material, boolean fancy) {
        super(material, fancy);
    }

}
