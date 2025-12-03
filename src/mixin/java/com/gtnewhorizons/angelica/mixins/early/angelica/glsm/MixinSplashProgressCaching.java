package com.gtnewhorizons.angelica.mixins.early.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.Drawable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 * is SharedDrawable or DrawableGL. This mixin just needs to:
 * 1. Register the SharedDrawable reference when it's created
 * 2. Mark splash complete when finish() is called
 */
@SuppressWarnings("deprecation")
@Mixin(value = cpw.mods.fml.client.SplashProgress.class, remap = false)
public class MixinSplashProgressCaching {

    @Shadow private static Drawable d;

    /**
     * Before start() calls d.makeCurrent(), register the SharedDrawable reference.
     * This allows GLStateManager.makeCurrent() to distinguish SharedDrawable from DrawableGL.
     */
    @Inject(method = "start",
            at = @At(value = "INVOKE",
                     target = "Lorg/lwjgl/opengl/Drawable;makeCurrent()V"))
    private static void angelica$captureSharedDrawable(CallbackInfo ci) {
        GLStateManager.setSharedDrawable(d);
    }

    /**
     * After finish() calls Display.getDrawable().makeCurrent() (DrawableGL), mark splash complete.
     * This is the final switch to DrawableGL that persists into the main game loop.
     * markSplashComplete() enables the fast path that bypasses holder tracking.
     */
    @Inject(method = "finish",
            at = @At(value = "INVOKE",
                     target = "Lorg/lwjgl/opengl/Drawable;makeCurrent()V",
                     shift = At.Shift.AFTER))
    private static void angelica$enableCachingOnFinish(CallbackInfo ci) {
        GLStateManager.markSplashComplete();
    }
}
