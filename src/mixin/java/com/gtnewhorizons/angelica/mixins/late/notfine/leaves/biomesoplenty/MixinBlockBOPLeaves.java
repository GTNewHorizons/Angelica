package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.biomesoplenty;

import biomesoplenty.common.blocks.BlockBOPLeaves;
import biomesoplenty.common.blocks.BlockBOPLeaves.LeafCategory;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockBOPLeaves.class)
public abstract class MixinBlockBOPLeaves extends BlockLeavesBase {

    @Shadow(remap = false)
    private IIcon[][] textures;
    @Shadow(remap = false)
    @Final
    private static String[] leaves;

    @Shadow(remap = false)
    @Final
    private LeafCategory category;

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

    private MixinBlockBOPLeaves(Material material, boolean fancy) {
        super(material, fancy);
    }

}
