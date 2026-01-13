package com.gtnewhorizons.angelica.mixins.early.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controls GLSM cache tracking based on which GL context is active during splash screen lifecycle.
 *
 * Two GL contexts exist during splash:
 * - SharedDrawable: Isolated context used by Client thread during splash, discarded after
 * - DrawableGL: Main Display context that survives into the game (splash thread renders here)
 *
 * We only want to cache state changes made on DrawableGL, since SharedDrawable's state is irrelevant
 * to the main game loop.
 *
 * GLStateManager.makeCurrent() automatically handles caching based on whether the drawable
 * is DrawableGL or not. DrawableGL is captured early in OpenGlHelper.initializeTextures().
 */
@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    /**
     *  On return from finish() - mark splash complete
     */
    @Inject(method = "finish", at = @At("RETURN"))
    private static void angelica$enableCachingOnFinish(CallbackInfo ci) {
        GLStateManager.markSplashComplete();
        LOGGER.info("Splash Complete");
    }
}
