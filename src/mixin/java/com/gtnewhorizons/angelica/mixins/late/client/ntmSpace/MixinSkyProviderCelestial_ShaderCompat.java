package com.gtnewhorizons.angelica.mixins.late.client.ntmSpace;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.hbm.dim.SkyProviderCelestial;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ported with additional fixes from the ShaderFixer mod
 * @author kotmatross28729
 */
@Mixin(value = SkyProviderCelestial.class, priority = 100)
public class MixinSkyProviderCelestial_ShaderCompat {
	
	/// Fixes
	
	/**
	 * Forces sunset to render with the default program
	 * <p> 
	 * Fixes sunset not rendering at all with shaders enabled
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/hbm/dim/SkyProviderCelestial;renderSunset(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V"), remap = false)
	public void iris$sunset$renderInDefaultProgram(CallbackInfo ci, @Share("sunset$previousProgram") LocalIntRef sunset$previousProgram) {
		sunset$previousProgram.set(GLStateManager.getActiveProgram());
		GLStateManager.glUseProgram(0);
	}
	
	/**
	 * Returns program before {@link #iris$sunset$renderInDefaultProgram(CallbackInfo, LocalIntRef)} call
	 */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/hbm/dim/SkyProviderCelestial;renderSunset(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V", shift = At.Shift.AFTER), remap = false)
	public void iris$sunset$restorePreviousProgram(CallbackInfo ci, @Share("sunset$previousProgram") LocalIntRef sunset$previousProgram) {
		GLStateManager.glUseProgram(sunset$previousProgram.get());
	}
	
	/**
	 * Disables sun blanking with shaders enabled (shaders ignore it anyway)
	 * <p> 
	 * Fixes the huge white square near the sun with shaders enabled
	 */
	@Inject(method = "renderSun", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 1))
	private void iris$sun$disableBlanking(CallbackInfo ci) {
		if(IrisApi.getInstance().isShaderPackInUse())
			Tessellator.instance.vertexCount = 0;
	}
	
	/**
	 * Moves the sun a little closer to the camera
	 * <p> 
	 * Fixes z-fighting between the sun and the shader skybox
	 */
	@ModifyArg(method = "renderSun",
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V", ordinal = 8),
					to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 3)),
					at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V"),
			index = 1)
	private double iris$sun$offsetY(double original) {
		return original - 0.3D;
	}
	
	/**
	 * In accordance with {@link #iris$sun$offsetY(double)} - moves the flare closer to the camera
	 */
	@ModifyArg(method = "renderSun",
			slice = @Slice(
					from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V", ordinal = 12),
					to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 4)),
					at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;addVertexWithUV(DDDDD)V"),
			index = 1)
	private double iris$sunFlare$offsetY(double original) {
		return original - 0.3D;
	}
	
	/**
	 * Disables mixing of celestial bodies with the atmosphere with shaders enabled
	 * <p>
	 * Fixes multiple bugs when used with a forced default program
	 */
	@Inject(method = "renderCelestials", at = @At(value="INVOKE", target="Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 10))
	private void iris$celestials$disableAtmosphereBlend(CallbackInfo ci) {
		if(IrisApi.getInstance().isShaderPackInUse())
			Tessellator.instance.vertexCount = 0;
	}
	
	/**
	 * Forces celestial bodies to render with the default program
	 * <p> 
	 * Requires {@link #iris$celestials$disableAtmosphereBlend(CallbackInfo)}
	 * <p> 
	 * Fixes many bugs, such as:
	 * <p> 
	 *  - [All Shaders] At certain time, celestial bodies are rendered as a just solid color squares
	 *  <p>
	 *  - [All Shaders] Some celestial bodies appear as heavily (70-90%) translucent
	 * <p> 
	 * - [Complementary 5/6.x] Stars render on top of celestial bodies; not behind them
	 * <p> 
	 * - [Complementary 5.x] Celestial bodies have incorrect colors near the sun
	 * <p> 
	 * - [Complementary 6.x] Celestial bodies don't render at all far from the sun
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V", ordinal = 5))
	private void iris$celestials$renderInDefaultProgram(CallbackInfo ci, @Share("celestials$previousProgram") LocalIntRef celestials$previousProgram) {
		celestials$previousProgram.set(GLStateManager.getActiveProgram());
		if(IrisApi.getInstance().isShaderPackInUse())
			GLStateManager.glUseProgram(0);
	}
	
	/**
	 * Returns program before {@link #iris$celestials$renderInDefaultProgram(CallbackInfo, LocalIntRef)} call
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 5, shift = At.Shift.AFTER))
	private void iris$celestials$restorePreviousProgram(CallbackInfo ci, @Share("celestials$previousProgram") LocalIntRef celestials$previousProgram) {
		GLStateManager.glUseProgram(celestials$previousProgram.get());
	}
	
	/**
	 * Forces celestial body ring (back) to render with the default program
	 * @see #iris$celestials$renderInDefaultProgram(CallbackInfo, LocalIntRef) 
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V", ordinal = 0))
	private void iris$ringsBack$renderInDefaultProgram(CallbackInfo ci, @Share("ringsBack$previousProgram") LocalIntRef ringsBack$previousProgram) {
		ringsBack$previousProgram.set(GLStateManager.getActiveProgram());
		if(IrisApi.getInstance().isShaderPackInUse())
			GLStateManager.glUseProgram(0);
	}
	
	/**
	 * Returns program before {@link #iris$ringsBack$renderInDefaultProgram(CallbackInfo, LocalIntRef)} call
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 0, shift = At.Shift.AFTER))
	private void iris$ringsBack$restorePreviousProgram(CallbackInfo ci, @Share("ringsBack$previousProgram") LocalIntRef ringsBack$previousProgram) {
		GLStateManager.glUseProgram(ringsBack$previousProgram.get());
	}
	
	/**
	 * Forces celestial body ring (front) to render with the default program
	 * @see #iris$celestials$renderInDefaultProgram(CallbackInfo, LocalIntRef)
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;startDrawingQuads()V", ordinal = 11))
	private void iris$ringsFront$renderInDefaultProgram(CallbackInfo ci, @Share("ringsFront$previousProgram") LocalIntRef ringsFront$previousProgram) {
		ringsFront$previousProgram.set(GLStateManager.getActiveProgram());
		if(IrisApi.getInstance().isShaderPackInUse())
			GLStateManager.glUseProgram(0);
	}
	
	/**
	 * Returns program before {@link #iris$ringsFront$renderInDefaultProgram(CallbackInfo, LocalIntRef)} call
	 */
	@Inject(method = "renderCelestials", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()I", ordinal = 11, shift = At.Shift.AFTER))
	private void iris$ringsFront$restorePreviousProgram(CallbackInfo ci, @Share("ringsFront$previousProgram") LocalIntRef ringsFront$previousProgram) {
		GLStateManager.glUseProgram(ringsFront$previousProgram.get());
	}
	
	/// Pipeline setup
	
	@Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
	private void iris$renderSky$beginNormalSky(CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
		pipeline.set(Iris.getPipelineManager().getPipelineNullable());
		if(pipeline.get() != null) 
			pipeline.get().setPhase(WorldRenderingPhase.SKY);
	}
	
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;getPosition(F)Lnet/minecraft/util/Vec3;"))
	private void iris$setVoidRenderStage(CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
		if(pipeline.get() != null) 
			pipeline.get().setPhase(WorldRenderingPhase.VOID);
	}
	
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2), remap = false)
	private void iris$renderSky$tiltSun(CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
		if(pipeline.get() != null) 
			GL11.glRotatef(pipeline.get().getSunPathRotation(), 0.0F, 0.0F, 1.0F);
	}
	
	@Inject(method = "renderSun", at = @At(value = "FIELD", target = "Lcom/hbm/dim/CelestialBody;texture:Lnet/minecraft/util/ResourceLocation;"), remap = false)
	private void iris$setSunRenderStage(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.SUN));
	}
	
	@Inject(method = "renderSunset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"))
	private void iris$setSunsetRenderStage(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.SUNSET));
	}
	
	@Inject(method = "renderStars", at = @At(value = "HEAD"), remap = false)
	private void iris$setStarRenderStage(CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.STARS));
	}

}
