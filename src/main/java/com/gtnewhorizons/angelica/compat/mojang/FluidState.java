package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.block.Block;
import net.minecraftforge.fluids.Fluid;
import org.joml.Vector3d;

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

    public float getHeight(BlockRenderView world, BlockPos pos) {
        return 0.0f;
    }
}
