package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.OpenGlHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.Shaders;

@Mixin(OpenGlHelper.class)
public class MixinOpenGlHelper {

    @Inject(method = "setActiveTexture(I)V", at = @At("HEAD"))
    private static void angelica$setActiveTexUnit(int activeTexture, CallbackInfo ci) {
        Shaders.activeTexUnit = activeTexture;
    }
}
