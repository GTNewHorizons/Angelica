package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

public interface TextureMapAccessor {
    Map getMapUploadedSprites();

    int getAnisotropicFiltering();

    int getMipmapLevels();

    ResourceLocation getLocationBlocksTexture();
}
