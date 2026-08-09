package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique private static boolean angelica$gameLoopStarted;
    @Unique private static long angelica$frameCounter;
    @Unique private static boolean angelica$frameGroupActive;

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void angelica$bootstrapFirstFrame(CallbackInfo ci) {
        DisplayListManager.abortIfLeaked();
        if (!angelica$gameLoopStarted) {
            angelica$gameLoopStarted = true;
            RENDER_BACKEND.onFrameBegin();
            angelica$pushFrameGroup();
        }
    }

    @Inject(method = "func_147120_f"/*resetSize*/, at = @At("HEAD"))
    private void angelica$onFrameEnd(CallbackInfo ci) {
        angelica$popFrameGroup();
        RENDER_BACKEND.onFrameEnd();
    }

    @Inject(method = "func_147120_f"/*resetSize*/, at = @At("RETURN"))
    private void angelica$onFrameBegin(CallbackInfo ci) {
        if (angelica$gameLoopStarted) {
            RENDER_BACKEND.onFrameBegin();
            angelica$pushFrameGroup();
        }
    }

    @Unique
    private static void angelica$pushFrameGroup() {
        CaptureGate.refresh();
        GLDebug.pushGroup("frame:" + angelica$frameCounter);
        angelica$frameGroupActive = true;
    }

    @Unique
    private static void angelica$popFrameGroup() {
        if (angelica$frameGroupActive) {
            GLDebug.popGroup();
            angelica$frameGroupActive = false;
            angelica$frameCounter++;
        }
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void angelica$onShutdown(CallbackInfo ci) {
        GpuCulling.shutdown();
        BackendManager.shutdown();
    }
}
