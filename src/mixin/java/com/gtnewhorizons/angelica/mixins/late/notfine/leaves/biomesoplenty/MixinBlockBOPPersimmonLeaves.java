package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.biomesoplenty;

import biomesoplenty.common.blocks.BlockBOPPersimmonLeaves;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BlockBOPPersimmonLeaves.class)
public abstract class MixinBlockBOPPersimmonLeaves extends BlockLeavesBase {

    @Shadow(remap = false)
    private IIcon[][] textures;

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        final int meta = world.getBlockMetadata(x, y, z);
        final int type = meta & 3;
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

    private MixinBlockBOPPersimmonLeaves(Material material, boolean fancy) {
        super(material, fancy);
    }

}
