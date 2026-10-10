package com.gtnewhorizons.angelica.mixins.late.client.thaumcraft;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import thaumcraft.common.blocks.BlockHole;

/**
 * A placed hole renders as a cube textured with blank.png, which is fully transparent...why mesh??
 */
@Mixin(BlockHole.class)
public abstract class MixinBlockHole extends BlockContainer {

    private MixinBlockHole(Material material) {
        super(material);
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world, int x, int y, int z, int side) {
        return false;
    }
}
