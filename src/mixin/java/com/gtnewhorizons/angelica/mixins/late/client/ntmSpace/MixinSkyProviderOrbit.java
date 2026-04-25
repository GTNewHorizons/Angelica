package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.WorkaroundUtils;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.orbit.SkyProviderOrbit;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyProviderOrbit.class, priority = 100)
public class MixinSkyProviderOrbit {
	
	/**
	 * Avoid program rebinding due to pipeline.setInputs
	 */
	@WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"), remap = false)
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
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;disableStandardItemLighting()V"), remap = false)
	public void iris$main$renderInDefaultProgram(CallbackInfo ci, @Share("main$previousProgram") LocalIntRef main$previousProgram) {
		main$previousProgram.set(GLStateManager.getActiveProgram());
		GLStateManager.glUseProgram(0);
	}
	
	@WrapOperation(method = "render",
			at = @At(value = "INVOKE", target = "Lcom/hbm/dim/orbit/SkyProviderOrbit;renderSun(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;Lcom/hbm/dim/CelestialBody;DDFF)V"), remap = false)
	private void iris$main$renderSunInShaderProgram(SkyProviderOrbit instance, float partialTicks, WorldClient world, Minecraft mc, CelestialBody sun, double sunSize, double coronaSize, float visibility, float pressure, Operation<Void> original, @Share("main$previousProgram") LocalIntRef main$previousProgram){
		GLStateManager.glUseProgram(main$previousProgram.get());
		original.call(instance, partialTicks, world, mc, sun, sunSize, coronaSize, visibility, pressure);
		GLStateManager.glUseProgram(0);
	}
	
	/**
	 * Sets the program back before {@link #iris$main$renderInDefaultProgram(CallbackInfo, LocalIntRef)} call
	 */
	@Inject(method = "render", at = @At(value = "TAIL"), remap = false)
	public void iris$main$restorePreviousProgram(CallbackInfo ci, @Share("main$previousProgram") LocalIntRef main$previousProgram) {
		GLStateManager.glUseProgram(main$previousProgram.get());
	}

}
