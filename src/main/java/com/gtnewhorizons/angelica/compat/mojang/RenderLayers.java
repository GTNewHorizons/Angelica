package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.mixins.early.sodium.MixinMaterial;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraftforge.fluids.Fluid;

public class RenderLayers {
    public static boolean canRenderInLayer(BlockState state, RenderLayer layer) {

        return canRenderInLayer(state.getBlock(), layer);
    }

    public static boolean canRenderInLayer(Block block, RenderLayer layer) {
        if(layer == RenderLayer.translucent())
            return block.canRenderInPass(1);
        else if(layer == RenderLayer.cutoutMipped())
            return block.canRenderInPass(0);

        return false;
    }

    public static boolean canRenderInLayer(FluidState fluidState, RenderLayer layer) {
        return canRenderFluidInLayer(fluidState.getFluid().getBlock(), layer);
    }

    public static boolean canRenderFluidInLayer(Block block, RenderLayer layer) {
        // Make all water-type fluids translucent, and all others solid
        // This may be revisited later, but it *should* be fine for now
        // Translucent fluids
        if (block.getMaterial() == Material.water) {
            return layer == RenderLayer.solid();
        }

        return layer == RenderLayer.solid();
    }
}
