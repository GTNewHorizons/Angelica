package com.gtnewhorizons.angelica.mixins.late.notfine.leaves.witchery;

import com.emoniph.witchery.blocks.BlockWitchLeaves;
import jss.notfine.core.SettingsManager;
import jss.notfine.util.LeafRenderUtil;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.material.Material;
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
    @Override
    @Overwrite
    public boolean isOpaqueCube() {
        return SettingsManager.leavesOpaque;
    }

    @Override
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int maskedMeta = world.getBlockMetadata(x, y, z) & 3;
        final int renderMode = LeafRenderUtil.selectRenderMode(world, x, y, z, side) ? 1 : 0;
        maskedMeta = maskedMeta > 1 ? 0 : maskedMeta;
        return iconsForModes[renderMode][maskedMeta];
    }

    @Shadow(remap = false)
    private IIcon[][] iconsForModes;

    private MixinBlockWitchLeaves(Material material, boolean unused) {
        super(material, unused);
    }

}
