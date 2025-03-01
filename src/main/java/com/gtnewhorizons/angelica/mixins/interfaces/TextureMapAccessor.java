package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

public interface TextureMapAccessor {
    Map<String, TextureAtlasSprite> getMapUploadedSprites();

    int getAnisotropicFiltering();

    int getMipmapLevels();

    ResourceLocation getLocationBlocksTexture();
}
