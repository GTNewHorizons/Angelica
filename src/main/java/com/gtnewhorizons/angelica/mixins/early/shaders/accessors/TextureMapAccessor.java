package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureMap.class)
public interface TextureMapAccessor {

    @Accessor("mapUploadedSprites")
    Map getMapUploadedSprites();

    @Accessor("anisotropicFiltering")
    int getAnisotropicFiltering();

    @Accessor("mipmapLevels")
    int getMipmapLevels();

    @Accessor("locationBlocksTexture")
    ResourceLocation getLocationBlocksTexture();

}
