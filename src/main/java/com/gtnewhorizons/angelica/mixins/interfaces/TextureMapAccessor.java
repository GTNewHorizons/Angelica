package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.util.ResourceLocation;

import java.util.Map;

public interface TextureMapAccessor {
    Map getMapUploadedSprites();

    int getAnisotropicFiltering();

    int getMipmapLevels();

    ResourceLocation getLocationBlocksTexture();
}
