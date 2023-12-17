package com.gtnewhorizons.angelica.compat.toremove;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

@Deprecated
public class RenderLayers {
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
