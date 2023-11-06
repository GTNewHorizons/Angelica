package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraftforge.fluids.Fluid;
import org.joml.Vector3d;

public class FluidState {

    public boolean isEmpty() {
        return false;
    }

    public Fluid getFluid() {
        return null;
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
