package com.gtnewhorizons.angelica.mixins.early.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import gkappa.modernsplash.CustomSplash;
import org.lwjgl.opengl.Drawable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin is functionally identical to MixinSplashProgressCaching,
 * but targets ModernSplash's CustomSplash class instead of FML's SplashProgress.
 */
@Mixin(value = CustomSplash.class, remap = false)
public class MixinCustomSplashMS {
    @Shadow
    private static Drawable d;

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
