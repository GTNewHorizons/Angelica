package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.CursorPresentThread;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the async cursor present thread is running it owns presentation of the window's
 * backbuffer (it does the framebufferMc blit, draws the cursor sprite, and calls swapBuffers
 * at compositor rate on its own GL context). If MC's main thread also calls
 * {@link Display#update()} — which swaps + pumps window events — the two swaps race for the
 * same window backbuffer. Because we cancel MC's {@code framebufferRender} when the cursor
 * thread is active (see {@link MixinFramebuffer_AsyncCursor}), MC's swap presents a black
 * backbuffer, and it consistently beats the cursor thread's swap → user sees black.
 *
 * <p>Redirect MC's {@code Display.update()} to {@code Display.processMessages()} when the
 * cursor thread is running. Window/input events still get pumped on the main thread (which
 * MC needs for input handling), but the swap is skipped — leaving the cursor thread as the
 * sole presenter.</p>
 */
@Mixin(value = Minecraft.class, priority = 1100)
public class MixinMinecraft_AsyncCursor {

    /**
     * Stop the cursor present thread before MC tears the window down. Its worker is daemon, so
     * JVM exit should kill it, but it spends most of its time inside native SwapBuffers; once
     * {@code Display.destroy()} invalidates the HDC the JNI frame can't be preempted cleanly and
     * the JVM hangs on shutdown (visible as javaw lingering after the window closes). Calling
     * stop() at HEAD of shutdownMinecraftApplet joins the worker while the window is still valid.
     */
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void angelica$stopCursorThreadBeforeShutdown(CallbackInfo ci) {
        CursorPresentThread.stop();
    }

    @Redirect(
        method = "func_147120_f",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false)
    )
    private void angelica$skipSwapWhenCursorThreadOwnsIt() {
        if (CursorPresentThread.isRunning()) {
            // Pump window/input events without swapping. Cursor thread does the swap.
            Display.processMessages();
            // lwjglx's Display.update() transfers an internal "latestResized" flag onto the
            // "displayResized" flag that Display.wasResized() reads; processMessages() doesn't.
            // With the swap-bypass, MC's wasResized() check that follows this call always reads
            // false, mc.displayWidth/Height stays at the old size, and the cursor present
            // textures keep rendering into a tiny region of the resized window. Detect the size
            // change ourselves and drive MC's resize handler directly — that re-allocates the
            // main framebuffer, after which the cursor thread's allocatePresentTextures sees the
            // new size on its next iteration.
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && !mc.isFullScreen()) {
                final int w = Display.getWidth();
                final int h = Display.getHeight();
                if (w > 0 && h > 0 && (w != mc.displayWidth || h != mc.displayHeight)) {
                    mc.resize(w, h);
                }
            }
        } else {
            Display.update();
        }
    }
}
