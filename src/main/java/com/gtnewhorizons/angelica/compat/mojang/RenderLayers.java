package com.gtnewhorizons.angelica.compat.mojang;

public class RenderLayers {
    public static boolean canRenderInLayer(BlockState state, RenderLayer type) {
        return true;
    }

    public static boolean canRenderInLayer(FluidState fluidState, RenderLayer layer) {
        return true;
    }
}
