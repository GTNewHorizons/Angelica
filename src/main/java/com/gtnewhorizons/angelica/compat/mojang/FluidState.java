package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.block.BlockLiquid;
import net.minecraftforge.fluids.Fluid;
import org.joml.Vector3d;

@Deprecated
public class FluidState {

    private final Fluid fluid;
    private final int meta;

    public FluidState(Fluid fluid, int meta) {
        this.fluid = fluid;
        this.meta = meta;
    }

    public boolean isEmpty() {
        return fluid == null;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public Vector3d getVelocity(BlockRenderView world, BlockPos pos) {
        return new Vector3d();
    }

    public boolean method_15756(BlockRenderView world, BlockPos set) {
        return true;
    }

    /**
     * Returns how much of the block is fluid, from 0 to 1.
     */
    public float getHeight(BlockRenderView world, BlockPos pos) {
        return fluid == null ? 0 : 1 - BlockLiquid.getLiquidHeightPercent(meta);
    }
}
