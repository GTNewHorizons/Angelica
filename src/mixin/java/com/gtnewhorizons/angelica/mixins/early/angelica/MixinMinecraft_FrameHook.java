package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.hooks.FrameHooks;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks frame boundaries and shutdown in Minecraft to notify the active render backend.
 *
 * Frame end: at the start of func_147120_f[resetSize] (before Display.update) -- universal, covers
 *            all callers including LoadingScreenRenderer, drawSplashScreen, toggleFullscreen.
 * Frame begin: at the end of func_147120_f (after Display.update), but only once the game loop has started.
 * Bootstrap: one-shot at the start of the first runGameLoop call to start the first frame.
 * Shutdown: at the start of shutdownMinecraftApplet (cleanup GPU resources).
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_FrameHook {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void angelica$bootstrapFirstFrame(CallbackInfo ci) {
        FrameHooks.bootstrapFirstFrame();
    }

    @Inject(method = "func_147120_f"/*resetSize*/, at = @At("HEAD"))
    private void angelica$onFrameEnd(CallbackInfo ci) {
        FrameHooks.frameEnd();
    }

    @Inject(method = "func_147120_f"/*resetSize*/, at = @At("RETURN"))
    private void angelica$onFrameBegin(CallbackInfo ci) {
        FrameHooks.frameBegin();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void angelica$onShutdown(CallbackInfo ci) {
        GpuCulling.shutdown();
        FrameHooks.shutdown();
    }
}
