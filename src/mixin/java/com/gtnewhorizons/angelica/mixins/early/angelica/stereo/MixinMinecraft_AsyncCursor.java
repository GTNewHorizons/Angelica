package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.CursorPresentThread;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(
        method = "func_147120_f",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false)
    )
    private void angelica$skipSwapWhenCursorThreadOwnsIt() {
        if (CursorPresentThread.isRunning()) {
            // Pump window/input events without swapping. Cursor thread does the swap.
            Display.processMessages();
        } else {
            Display.update();
        }
    }
}
