package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {
    private static final Logger LOGGER = LogManager.getLogger("Umbra");

    @Inject(method = "finish", at = @At("RETURN"))
    private static void umbra$enableCachingOnFinish(CallbackInfo ci) {
        GLStateManager.markSplashComplete("SplashProgress.finish");
        LOGGER.info("Splash Complete");
    }
}
