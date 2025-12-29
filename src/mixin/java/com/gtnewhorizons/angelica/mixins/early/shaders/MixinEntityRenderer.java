package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.HandRenderer;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements IResourceManagerReloadListener {
    @Shadow public Minecraft mc;

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/ClippingHelperImpl;getInstance()Lnet/minecraft/client/renderer/culling/ClippingHelper;", shift = At.Shift.AFTER, ordinal = 0), method = "renderWorld(FJ)V")
    private void iris$beginRender(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());

        Program.unbind();

        pipeline.set(Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension()));

        pipeline.get().beginLevelRendering();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false))
    private void iris$endLevelRender(float partialTicks, long limitTime, CallbackInfo callback, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // TODO: Iris
        HandRenderer.INSTANCE.renderTranslucent(partialTicks, Camera.INSTANCE, mc.renderGlobal, pipeline.get());
        Minecraft.getMinecraft().mcProfiler.endStartSection("iris_final");
        pipeline.get().finalizeLevelRendering();
        pipeline.set(null);
        Program.unbind();
        GLStateManager.glDepthMask(true);
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    private void iris$disableVanillaRenderHand(ItemRenderer instance, float partialTicks, Operation<Void> original) {
        if (!IrisApi.getInstance().isShaderPackInUse()) {
            original.call(instance, partialTicks);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;clipRenderersByFrustum(Lnet/minecraft/client/renderer/culling/ICamera;F)V"), method = "renderWorld(FJ)V")
    private void iris$renderShadows(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().renderShadows((EntityRenderer) (Object) this, Camera.INSTANCE);
    }


    @Redirect(method = "renderWorld(FJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;renderDistanceChunks:I")    )
    /*slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))*/
    private int iris$alwaysRenderSky(GameSettings instance) {
        return Math.max(instance.renderDistanceChunks, 4);
    }
    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(F)V"))
    private void iris$beginSky(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // Use CUSTOM_SKY until levelFogColor is called as a heuristic to catch FabricSkyboxes.
        pipeline.get().setPhase(WorldRenderingPhase.CUSTOM_SKY);
    }


    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(F)V", shift = At.Shift.AFTER))
    private void iris$endSky(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.NONE);
    }

    @WrapOperation(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V"))
    private void iris$clouds(EntityRenderer instance, RenderGlobal rg, float partialTicks, Operation<Void> original, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.CLOUDS);
        original.call(instance, rg, partialTicks);
        pipeline.get().setPhase(WorldRenderingPhase.NONE);
    }


    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V"))
    private void iris$beginWeatherAndwriteRainAndSnowToDepthBuffer(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.RAIN_SNOW);
        if (pipeline.get().shouldWriteRainAndSnowToDepthBuffer()) {
            GLStateManager.glDepthMask(true);
        }
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V", shift = At.Shift.AFTER))
    private void iris$endWeather(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.NONE);
    }

    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/entity/EntityLivingBase;F)V", remap = false))
    private void iris$blockDamageTexture(RenderGlobal instance, Tessellator tessellator, EntityLivingBase entity, float partialTicks, Operation<Void> original, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.DESTROY);
        original.call(instance, tessellator, entity, partialTicks);
        pipeline.get().setPhase(WorldRenderingPhase.NONE);
    }
}
