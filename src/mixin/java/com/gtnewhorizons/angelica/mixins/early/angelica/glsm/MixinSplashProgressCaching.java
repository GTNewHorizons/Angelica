package com.gtnewhorizons.angelica.mixins.early.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.FrameHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {

    @Inject(method = "start", at = @At("TAIL"))
    private static void angelica$bindSharedDrawableVAO(CallbackInfo ci) {
        FrameHooks.bindSplashVao();
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private static void angelica$enableCachingOnFinish(CallbackInfo ci) {
        FrameHooks.splashFinished();
    }
}
