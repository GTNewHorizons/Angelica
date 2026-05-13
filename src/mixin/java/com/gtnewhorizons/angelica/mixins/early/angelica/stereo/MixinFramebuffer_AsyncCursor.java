package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.CursorPresentThread;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the async cursor present thread is running, it owns presentation of MC's main framebuffer
 * to the default backbuffer — it does its own blit + cursor + swap at compositor rate. MC's main
 * thread also calls {@code framebufferRender} to blit framebufferMc into the default FB once per
 * iteration; with the cursor thread also writing the default FB, the two writes race and tear.
 *
 * <p>This injection point is also the right place to <b>capture</b> framebufferMc into the cursor
 * thread's present texture. MC's frame layout (runGameLoop) is:
 * {@code updateCameraAndRender → onRenderTickEnd → guiAchievement → framebufferRender → Display.update}.
 * By {@code framebufferRender}'s HEAD, every render path that targets framebufferMc has run
 * (world, HUD, WAILA via onRenderTickEnd, achievement popups, etc.). Publishing here gives the
 * cursor thread a fully-composed frame; publishing earlier — e.g., at the RETURN of
 * {@code updateCameraAndRender} — misses everything that draws between then and now.</p>
 *
 * <p>Short-circuit only when the framebuffer being rendered is MC's main framebuffer (so
 * HUDCaching's cache framebuffer, Iris's intermediate FBOs, etc. still render normally).</p>
 *
 * <p>Main's {@code Display.update()} is also redirected (see
 * {@link MixinMinecraft_AsyncCursor}) so the cursor thread is the sole window-swap caller.</p>
 */
@Mixin(value = Framebuffer.class, priority = 1100)
public class MixinFramebuffer_AsyncCursor {

    @Inject(method = "framebufferRender", at = @At("HEAD"), cancellable = true)
    public void angelica$capturePublishAndSkip(int width, int height, CallbackInfo ci) {
        if (!CursorPresentThread.isRunning()) return;
        final Framebuffer self = (Framebuffer) (Object) this;
        if (self == Minecraft.getMinecraft().getFramebuffer()) {
            CursorPresentThread.publishFrame();
            ci.cancel();
        }
    }
}
