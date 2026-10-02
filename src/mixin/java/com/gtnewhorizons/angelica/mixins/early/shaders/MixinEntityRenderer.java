package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import jss.notfine.core.SettingsManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.CloudSetting;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.HandRenderer;
import com.gtnewhorizons.angelica.compat.thaumcraft.ThaumometerScreen;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements IResourceManagerReloadListener {
    @Shadow public Minecraft mc;

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER, ordinal = 0), method = "renderWorld(FJ)V")
    private void iris$beginRender(float partialTicks, long startTime, CallbackInfo ci) {
        mc.mcProfiler.endStartSection("iris_begin");
        DHCompat.checkFrame();
        Iris.tryLoadShaderpackWhenPossible();

        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        ThaumometerScreen.discard();
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        Program.unbind();

        mc.mcProfiler.startSection("iris_prepare_pipeline");
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimensionName());
        mc.mcProfiler.endSection();

        GLStateManager.setShaderColor(1f, 1f, 1f, 1f);

        pipeline.beginLevelRendering();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false))
    private void iris$endLevelRender(float partialTicks, long limitTime, CallbackInfo callback) {
        // TODO: Iris
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        HandRenderer.INSTANCE.renderTranslucent(partialTicks, Camera.INSTANCE, mc.renderGlobal, pipeline);
        ThaumometerScreen.render(pipeline);
        Minecraft.getMinecraft().mcProfiler.endStartSection("iris_final");
        pipeline.finalizeLevelRendering();
        Program.unbind();
        GLStateManager.glDepthMask(true);
        GLStateManager.disableBlend();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GLStateManager.enableAlphaTest();
    }

    @Surround(id = "disableVanillaRenderHand", method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    private void iris$disableVanillaRenderHand() {
        @Surround.Skip
        boolean skip = IrisApi.getInstance().isShaderPackInUse();
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;clipRenderersByFrustum(Lnet/minecraft/client/renderer/culling/ICamera;F)V"), method = "renderWorld(FJ)V")
    private void iris$renderShadows(float partialTicks, long startTime, CallbackInfo ci, @Local Frustrum playerFrustum) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer != null) {
            renderer.setShadowPassPlayerViewport(((ViewportProvider) playerFrustum).sodium$createViewport());
        }
        Iris.getPipelineManager().getPipelineNullable().renderShadows((EntityRenderer) (Object) this, Camera.INSTANCE);
    }


    @Redirect(method = "renderWorld(FJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I")    )
    /*slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))*/
    private int iris$alwaysRenderSky(GameSettings instance) {
        return Math.max(instance.renderDistanceChunks, 4);
    }

    @ModifyConstant(method = "renderWorld(FJ)V", constant = @Constant(doubleValue = 128.0D), expect = 2)
    private double iris$alwaysRenderCloudsLate(double cloudHeightCheck) {
        if (IrisApi.getInstance().isShaderPackInUse()) return Double.NEGATIVE_INFINITY;
        return SettingsManager.cloudRenderOrderHeight() - Camera.INSTANCE.getOffset().y;
    }

    @Surround(id = "renderSky", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(F)V"))
    private void iris$beginSky() {
        @Surround.Carry
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        // Use CUSTOM_SKY until levelFogColor is called as a heuristic to catch FabricSkyboxes.
        pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
    }

    @Surround.Finally("renderSky")
    private void iris$endSky(@Surround.Carry WorldRenderingPipeline pipeline) {
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    @Surround(id = "clouds", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V"))
    private void iris$clouds() {
        @Surround.Carry
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        pipeline.setPhase(WorldRenderingPhase.CLOUDS);
        @Surround.Skip
        boolean skip = pipeline.getCloudSetting() == CloudSetting.OFF;
    }

    @Surround.Finally("clouds")
    private void iris$cloudsEnd(@Surround.Carry WorldRenderingPipeline pipeline) {
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }


    @Surround(id = "wrapWeather", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V"))
    private void iris$wrapWeather() {
        @Surround.Carry
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
        if (pipeline.shouldWriteRainAndSnowToDepthBuffer()) {
            GLStateManager.glDepthMask(true);
        }
        @Surround.Skip
        boolean skip = !pipeline.shouldRenderWeather();
    }

    @Surround.Finally("wrapWeather")
    private void iris$wrapWeatherEnd(@Surround.Carry WorldRenderingPipeline pipeline) {
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    @Surround(id = "wrapRainParticles", method = "updateRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;addRainParticles()V"))
    private void iris$wrapRainParticles() {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        @Surround.Skip
        boolean skip = !(pipeline == null || pipeline.shouldRenderWeatherParticles());
    }

    @Surround(id = "blockDamageTexture", method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V", remap = false))
    private void iris$blockDamageTexture() {
        @Surround.Carry
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        pipeline.setPhase(WorldRenderingPhase.DESTROY);
    }

    @Surround.Finally("blockDamageTexture")
    private void iris$blockDamageTextureEnd(@Surround.Carry WorldRenderingPipeline pipeline) {
        pipeline.setPhase(WorldRenderingPhase.NONE);
    }

    @Surround(id = "litParticlePhase", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EffectRenderer;renderLitParticles(Lnet/minecraft/entity/Entity;F)V"))
    private void iris$litParticlePhase() {
        @Surround.Carry int depth = GbufferPrograms.beginParticles();
    }

    @Surround.Finally("litParticlePhase")
    private void iris$litParticlePhaseEnd(@Surround.Carry int depth) {
        GbufferPrograms.endParticles(depth);
    }

    @Surround(id = "particlePhase", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EffectRenderer;renderParticles(Lnet/minecraft/entity/Entity;F)V"))
    private void iris$particlePhase() {
        @Surround.Carry int depth = GbufferPrograms.beginParticles();
    }

    @Surround.Finally("particlePhase")
    private void iris$particlePhaseEnd(@Surround.Carry int depth) {
        GbufferPrograms.endParticles(depth);
    }
}
