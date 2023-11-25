package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockLiquid.class)
public abstract class MixinBlockLiquid implements IFluidBlock {

    @Shadow
    public abstract int getBlockColor();

    @Override
    public Fluid getFluid() {
        return ((Block) (Object) this).getMaterial() == Material.water ? FluidRegistry.WATER : FluidRegistry.LAVA;
    }

    @Override
    public float getFilledPercentage(World world, int x, int y, int z) {
        return getFluid() == null ? 0 : 1 - BlockLiquid.getLiquidHeightPercent(world.getBlockMetadata(x, y, z));
    }
}
