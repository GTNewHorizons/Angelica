package com.gtnewhorizons.angelica.mixins.late.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;

import mrtjp.projectred.core.RenderHalo;

@Mixin(value = RenderHalo.class)
public class MixinRenderHalo {

    @Inject(
            at = @At(remap = false, target = "Lcodechicken/lib/render/CCRenderState;reset()V", value = "INVOKE"),
            method = "prepareRenderState()V",
            remap = false)
    private void angelica$beginProjectRedHalo(CallbackInfo ci) {
        Shaders.beginProjectRedHalo();
    }

    @Inject(at = @At("TAIL"), method = "restoreRenderState()V", remap = false)
    private void angelica$endProjectRedHalo(CallbackInfo ci) {
        Shaders.endProjectRedHalo();
    }

}
