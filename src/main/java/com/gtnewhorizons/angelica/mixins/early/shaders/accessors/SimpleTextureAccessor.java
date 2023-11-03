package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleTexture.class)
public interface SimpleTextureAccessor {
	@Accessor("textureLocation")
	ResourceLocation getLocation();
}
