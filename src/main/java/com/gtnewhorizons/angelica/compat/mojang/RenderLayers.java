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

        // Translucent blocks
        if (((MixinMaterial) block.getMaterial()).getIsTranslucent())
            return layer == RenderLayer.translucent();

        // Make all full, opaque blocks SOLID
        // This misses things like fences and walls, but it doesn't matter too much
        if (block.isOpaqueCube())
            return layer == RenderLayer.solid();

        // TODO: CUTOUT is more performant, but this is fine for testing
        return layer == RenderLayer.cutoutMipped();
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
