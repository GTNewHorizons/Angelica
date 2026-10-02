package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.WorkaroundUtils;
import com.hbm.dim.orbit.SkyProviderOrbit;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyProviderOrbit.class, priority = 100, remap = false)
public class MixinSkyProviderOrbit {
	
	@Unique
	private int angelica$previousProgram;
	
	/**
	 * Avoid program rebinding due to pipeline.setInputs
	 */
	@WrapWithCondition(method = "render"
			, at = @At(value = "INVOKE"
				, target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"))
	private boolean iris$common$redirectTex2D(int cap) {
		if (cap == GL11.GL_TEXTURE_2D) {
			Minecraft.getMinecraft().renderEngine.bindTexture(WorkaroundUtils.GL_TEXTURE_2D_WORKAROUND);
			return false;
		}
		return true;
	}
	
	/**
	 * Force skybox to render with the default program | Fix various issues with shaders enabled
	 */
	@Inject(method = "render"
			, at = @At(value = "INVOKE"
				, target = "Lnet/minecraft/client/renderer/RenderHelper;disableStandardItemLighting()V"
				, remap = true))
	public void iris$main$renderInDefaultProgram(CallbackInfo ci) {
		angelica$previousProgram = GLStateManager.getActiveProgram();
		GLStateManager.glUseProgram(0);
	}
	
	@Surround(method = "render"
			, at = @At(value = "INVOKE"
				, target = "Lcom/hbm/dim/orbit/SkyProviderOrbit;renderSun(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;Lcom/hbm/dim/CelestialBody;DDFF)V"))
	private void iris$main$renderSunInShaderProgram(){
		GLStateManager.glUseProgram(angelica$previousProgram);
	}

	@Surround.Finally
	private void iris$main$renderSunInShaderProgramEnd() {
		GLStateManager.glUseProgram(0);
	}

	@Inject(method = "render"
			, at = @At(value = "TAIL"))
	public void iris$main$restorePreviousProgram(CallbackInfo ci) {
		GLStateManager.glUseProgram(angelica$previousProgram);
	}

}
