package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.utils.WorkaroundUtils;
import com.hbm.dim.laythe.SkyProviderLaytheSunset;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SkyProviderLaytheSunset.class, priority = 100)
public class MixinSkyProviderLaytheSunset {
	
	/**
	 * Avoid program rebinding due to pipeline.setInputs
	 */
	@WrapWithCondition(method = "renderSunset", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"), remap = false)
	private boolean iris$sunset$redirectTex2D(int cap) {
		if(cap == GL11.GL_TEXTURE_2D) {
			Minecraft.getMinecraft().renderEngine.bindTexture(WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND);
			return false;
		}
		return true;
	}

}
