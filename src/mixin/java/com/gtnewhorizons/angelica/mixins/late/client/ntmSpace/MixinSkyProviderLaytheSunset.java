package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.hbm.dim.laythe.SkyProviderLaytheSunset;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SkyProviderLaytheSunset.class, priority = 100)
public class MixinSkyProviderLaytheSunset {
	
	/**
	 * Disables {@link GL11#glDisable(int GL_TEXTURE_2D)} call
	 */
	@WrapWithCondition(method = "renderSunset", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", ordinal = 0), remap = false)
	private boolean iris$sunset$revertTextureDisable(int i) {
		return false;
	}
	
	/**
	 * Disables {@link GL11#glEnable(int GL_TEXTURE_2D)} call
	 */
	@WrapWithCondition(method = "renderSunset", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V", ordinal = 0), remap = false)
	private boolean iris$sunsetBlend$revertTextureEnable(int i) {
		return false;
	}
	
}
