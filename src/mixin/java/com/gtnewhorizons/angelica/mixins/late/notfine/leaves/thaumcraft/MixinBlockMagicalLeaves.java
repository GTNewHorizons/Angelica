package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.thaumcraft;

import jss.notfine.util.IFaceObstructionCheckHelper;
import jss.notfine.util.ILeafBlock;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import thaumcraft.common.blocks.BlockMagicalLeaves;

@Mixin(value = BlockMagicalLeaves.class)
public abstract class MixinBlockMagicalLeaves extends Block implements IFaceObstructionCheckHelper, ILeafBlock {

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int maskedMeta = world.getBlockMetadata(x, y, z) & 3;
        final int renderMode = LeafRenderUtil.selectRenderMode(world, x, y, z, side) ? 1 : 0;
        maskedMeta = maskedMeta > 1 ? 0 : maskedMeta;
        return icon[renderMode + maskedMeta * 2];
    }

    /**
     * @author jss2a98aj
     * @reason Support new leaf rendering modes on Thaumcraft leaves.
     */
    @Override
    @Overwrite
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return LeafRenderUtil.shouldSideBeRendered(world, x, y, z, side);
    }

    @Override()
    public boolean isFaceNonObstructing(IBlockAccess world, int x, int y, int z, int side, double otherMinX, double otherMinY, double otherMinZ, double otherMaxX, double otherMaxY, double otherMaxZ) {
        return LeafRenderUtil.isFaceNonObstructing(world, x, y, z);
    }

    private MixinBlockMagicalLeaves(Material material) {
        super(material);
    }

    @Shadow(remap = false)
    public IIcon[] icon;

}
