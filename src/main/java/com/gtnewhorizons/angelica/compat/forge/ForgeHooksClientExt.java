package com.gtnewhorizons.angelica.compat.forge;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.FluidState;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class ForgeHooksClientExt {

    static final ThreadLocal<RenderLayer> renderLayer = new ThreadLocal<>();

    public static void setRenderLayer(RenderLayer layer) {
        renderLayer.set(layer);
    }

    public static TextureAtlasSprite[] getFluidSprites(BlockRenderView world, BlockPos pos, FluidState fluidState) {
        return new TextureAtlasSprite[0];
    }
}
