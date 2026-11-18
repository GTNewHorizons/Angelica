package com.gtnewhorizons.angelica.mixins.early.angelica.rendering;

import com.gtnewhorizons.angelica.render.WeatherRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer_Weather {

    @Inject(method = "renderRainSnow(F)V", at = @At("HEAD"), cancellable = true)
    private void angelica$weather(float partialTicks, CallbackInfo ci) {
        if (WeatherRenderer.render(partialTicks)) ci.cancel();
    }

    @Inject(method = "addRainParticles", at = @At("HEAD"), cancellable = true)
    private void angelica$rainParticles(CallbackInfo ci) {
        if (WeatherRenderer.spawnRainParticles()) ci.cancel();
    }
}
