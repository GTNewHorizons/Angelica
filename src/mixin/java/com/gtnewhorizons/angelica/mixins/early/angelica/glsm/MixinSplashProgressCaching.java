package com.gtnewhorizons.angelica.mixins.early.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {

    // VAOs aren't shared across GL contexts; the SharedDrawable the client thread just swapped
    // onto has none. Core profile (macOS) rejects glValidateProgram without one.
    @Inject(method = "start", at = @At("RETURN"))
    private static void angelica$bindSharedDrawableVAO(CallbackInfo ci) {
        GLStateManager.glBindVertexArray(GLStateManager.glGenVertexArrays());
    }
}
