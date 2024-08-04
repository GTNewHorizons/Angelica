package com.gtnewhorizons.angelica.mixins.early.shaders.accessors;

import com.gtnewhorizons.angelica.mixins.interfaces.SimpleTextureAccessor;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleTexture.class)
public abstract class MixinSimpleTexture implements SimpleTextureAccessor {
	@Accessor("textureLocation")
	public abstract ResourceLocation getLocation();
}
