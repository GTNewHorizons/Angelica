package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.entity.Render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(Render.class)
public class MixinRender {
    // TODO: Rendering
    @Inject(method = "renderShadow(Lnet/minecraft/entity/Entity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void angelica$checkShouldSkipDefaultShadow(CallbackInfo ci) {
        if (Shaders.shouldSkipDefaultShadow) ci.cancel();
    }
}
