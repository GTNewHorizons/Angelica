package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

import com.gtnewhorizons.angelica.mixins.interfaces.TextureMapAccessor;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap implements TextureMapAccessor {

    @Accessor("mapUploadedSprites")
    public abstract Map<String, TextureAtlasSprite> getMapUploadedSprites();

    @Accessor("anisotropicFiltering")
    public abstract int getAnisotropicFiltering();

    @Accessor("mipmapLevels")
    public abstract int getMipmapLevels();

    @Accessor("locationBlocksTexture")
    public abstract ResourceLocation getLocationBlocksTexture();

}
