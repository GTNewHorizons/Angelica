package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.WorkaroundUtils;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.SkyProviderCelestial;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import jss.notfine.core.Settings;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported with additional fixes from the ShaderFixer mod
 * @author kotmatross28729
 */
@Mixin(value = SkyProviderCelestial.class, priority = 100)
public class MixinSkyProviderCelestial {
	
	/**
	 * Avoid program rebinding due to pipeline.setInputs
	 */
	@WrapWithCondition(method = {
			"renderSunset",
			"renderCelestials",
			"renderAtmosphereGlow",
	}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"), remap = false)
	private boolean iris$common$redirectTex2D(int cap) {
		return this.iris$common$redirectTexture(cap);
	}
	
	/**
	 * Avoid program rebinding due to pipeline.setInputs | Doesn't affect texture disabling before glSkyList
	 */
	@WrapWithCondition(method = "render",
			slice = @Slice(from = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V", ordinal = 1)),
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"), remap = false)
	private boolean iris$common$redirectTex2DException(int cap) {
		return this.iris$common$redirectTexture(cap);
	}
	
	@Unique
	private boolean iris$common$redirectTexture(int cap) {
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
			at = @At(value = "INVOKE", target = "Lcom/hbm/dim/SkyProviderCelestial;renderSun(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;Lcom/hbm/dim/CelestialBody;DDFF)V"), remap = false)
	private void iris$main$renderSunInShaderProgram(SkyProviderCelestial instance, float partialTicks, WorldClient world, Minecraft mc, CelestialBody sun, double sunSize, double coronaSize, float visibility, float pressure, Operation<Void> original, @Share("main$previousProgram") LocalIntRef main$previousProgram){
		GLStateManager.glUseProgram(main$previousProgram.get());
		original.call(instance, partialTicks, world, mc, sun, sunSize, coronaSize, visibility, pressure);
		GLStateManager.glUseProgram(0);
	}
	
	@Inject(method = "render", at = @At(value = "TAIL"), remap = false)
	public void iris$main$restorePreviousProgram(CallbackInfo ci, @Share("main$previousProgram") LocalIntRef main$previousProgram) {
		GLStateManager.glUseProgram(main$previousProgram.get());
	}
	
	@Inject(method = "renderSun", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"),
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0),
					to = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 1)
			)
	)
	private void iris$sun$disableBlanking(CallbackInfo ci) {
		Tessellator.instance.vertexCount = 0;
	}
	
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I"),
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 1),
					to = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 2)
			)
	)
	private void iris$main$disableVoid(CallbackInfo ci) {
		Tessellator.instance.vertexCount = 0;
	}
	
	@WrapWithCondition(method = "render", at = {
				@At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 1),
				@At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", ordinal = 2)
			}, remap = false)
	private boolean angelica$renderHorizon(int i) {
		return (boolean) Settings.HORIZON.option.getStore();
	}
	
	@Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;", opcode = Opcodes.GETSTATIC))
	private void iris$renderSky$beginNormalSky(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.SKY));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2), remap = false)
	private void iris$renderSky$tiltSun(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(
				p -> GLStateManager.glRotatef(p.getSunPathRotation(), 0.0F, 0.0F, 1.0F)
		);
	}
	
	@Inject(method = "renderSun", at = @At(value = "HEAD"), remap = false)
	private void iris$setSunRenderStage(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.SUN));
	}
	
}
