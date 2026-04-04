package com.gtnewhorizons.umbra.mixins.early.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.ImmediateModeRecorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controls GLSM state during splash screen lifecycle.
 * Initializes splash tessellator on start, marks splash complete on finish.
 */
@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {
    private static final Logger LOGGER = LogManager.getLogger("Umbra");

    @Inject(method = "start", at = @At("HEAD"))
    private static void umbra$initSplashTessellator(CallbackInfo ci) {
        ImmediateModeRecorder.initSplashTessellator();
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private static void umbra$enableCachingOnFinish(CallbackInfo ci) {
        ImmediateModeRecorder.destroySplashTessellator();
        GLStateManager.markSplashComplete();
        LOGGER.info("Splash Complete");
    }
}
