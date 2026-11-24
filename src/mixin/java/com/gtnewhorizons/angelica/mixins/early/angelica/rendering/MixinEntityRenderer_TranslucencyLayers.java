package com.gtnewhorizons.angelica.mixins.early.angelica.rendering;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.rendering.translucency.TranslucencyLayers;
import com.gtnewhorizons.angelica.rendering.translucency.TranslucentLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_TranslucencyLayers {

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
    private void angelica$beginTranslucencyLayers(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        TranslucencyLayers.INSTANCE.beginFrame();
    }

    @Inject(method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderLast(Lnet/minecraft/client/renderer/RenderGlobal;F)V", remap = false))
    private void angelica$compositeTranslucencyLayers(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        TranslucencyLayers.INSTANCE.composite();
    }

    @Surround(id = "translucencyParticles", method = "renderWorld(FJ)V", expect = 2, at = {
        @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EffectRenderer;renderLitParticles(Lnet/minecraft/entity/Entity;F)V"),
        @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/EffectRenderer;renderParticles(Lnet/minecraft/entity/Entity;F)V") })
    private void angelica$particlesLayer() {
        @Surround.Carry boolean layered = TranslucencyLayers.INSTANCE.beginIfActive(TranslucentLayer.PARTICLES);
    }

    @Surround.Finally("translucencyParticles")
    private void angelica$particlesLayerEnd(@Surround.Carry boolean layered) {
        if (layered) TranslucencyLayers.INSTANCE.end();
    }

    @Surround(id = "translucencyWeather", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V"))
    private void angelica$weatherLayer(@Surround.Local(argsOnly = true) float partialTicks) {
        final WorldClient world = Minecraft.getMinecraft().theWorld;
        @Surround.Carry boolean layered = (world.getRainStrength(partialTicks) > 0.0F || world.provider.getWeatherRenderer() != null)
            && TranslucencyLayers.INSTANCE.beginIfActive(TranslucentLayer.WEATHER);
        // Rain writes no depth, which would leave the layer sorting at the opaque depth behind it.
        if (layered) GLSMDepthStencilAlpha.glDepthMask(true);
    }

    @Surround.Finally("translucencyWeather")
    private void angelica$weatherLayerEnd(@Surround.Carry boolean layered) {
        if (layered) TranslucencyLayers.INSTANCE.end();
    }

    @Surround(id = "translucencyClouds", method = "renderWorld(FJ)V", at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/client/renderer/EntityRenderer;renderCloudsCheck(Lnet/minecraft/client/renderer/RenderGlobal;F)V"))
    private void angelica$cloudsLayer() {
        @Surround.Carry boolean layered = Minecraft.getMinecraft().gameSettings.shouldRenderClouds() && TranslucencyLayers.INSTANCE.beginIfActive(TranslucentLayer.CLOUDS);
    }

    @Surround.Finally("translucencyClouds")
    private void angelica$cloudsLayerEnd(@Surround.Carry boolean layered) {
        if (layered) TranslucencyLayers.INSTANCE.end();
    }
}
