package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderGlobal;
import org.lwjglx.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {
    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private void iris$renderSky$beginNormalSky(float partialTicks, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        // None of the vanilla sky is rendered until after this call, so if anything is rendered before, it's CUSTOM_SKY.
        pipeline.set(Iris.getPipelineManager().getPipelineNullable());
        pipeline.get().setPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationSunPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setSunRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.SUN);
    }

    @Inject(method = "renderSky", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/RenderGlobal;locationMoonPhasesPng:Lnet/minecraft/util/ResourceLocation;"))
    private void iris$setMoonRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.MOON);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldProvider;calcSunriseSunsetColors(FF)[F"))
    private void iris$setSunsetRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private void iris$setStarRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.STARS);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityClientPlayerMP;getPosition(F)Lnet/minecraft/util/Vec3;"))
    private void iris$setVoidRenderStage(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        pipeline.get().setPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getCelestialAngle(F)F"),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getRainStrength(F)F")))
    private void iris$renderSky$tiltSun(float p_72714_1_, CallbackInfo ci, @Share("pipeline") LocalRef<WorldRenderingPipeline> pipeline) {
        GLStateManager.glRotatef(pipeline.get().getSunPathRotation(), 0.0F, 0.0F, 1.0F);
    }

}
