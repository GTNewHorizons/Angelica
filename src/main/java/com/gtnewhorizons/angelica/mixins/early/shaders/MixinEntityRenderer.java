package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer implements IResourceManagerReloadListener {
    @Inject(at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V", shift = At.Shift.AFTER, ordinal = 0), method = "renderWorld(FJ)V")
    private void iris$beginRender(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        CapturedRenderingState.INSTANCE.setTickDelta(partialTicks);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(startTime);

        Program.unbind();

        pipeline.set(Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension()));

        pipeline.get().beginLevelRendering();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private void iris$endLevelRender(float partialTicks, long limitTime, CallbackInfo callback, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // TODO: Iris
//        HandRenderer.INSTANCE.renderTranslucent(poseStack, tickDelta, camera, gameRenderer, pipeline);
        Minecraft.getMinecraft().mcProfiler.endStartSection("iris_final");
        pipeline.get().finalizeLevelRendering();
        pipeline.set(null);
        Program.unbind();
    }

    @Inject(at = @At(value= "INVOKE", target="Lnet/minecraft/client/renderer/RenderGlobal;clipRenderersByFrustum(Lnet/minecraft/client/renderer/culling/ICamera;F)V", shift=At.Shift.AFTER), method = "renderWorld(FJ)V")
    private void iris$beginEntities(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        final Minecraft mc = Minecraft.getMinecraft();
        Camera camera = new Camera(mc.renderViewEntity, partialTicks);
        pipeline.get().renderShadows((EntityRenderer) (Object) this, camera);
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
            GL11.glDepthMask(true);
        }
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V", shift = At.Shift.AFTER))
    private void iris$endWeather(float partialTicks, long startTime, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.NONE);
    }

}
