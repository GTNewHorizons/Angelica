package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    @Accessor("mapUploadedSprites")
    public abstract Map getMapUploadedSprites();

    @Accessor("anisotropicFiltering")
    public abstract int getAnisotropicFiltering();

    @Accessor("mipmapLevels")
    public abstract int getMipmapLevels();

    @Accessor("locationBlocksTexture")
    public abstract ResourceLocation getLocationBlocksTexture();

}
