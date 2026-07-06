package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;

public interface EntityRendererAccessor {
    float invokeGetNightVisionBrightness(EntityPlayer entityPlayer, float partialTicks);

    /**
     * Get the lightmap texture for shader binding.
     */
    DynamicTexture getLightmapTexture();
}
