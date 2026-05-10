package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When stereo rendering is mid-frame and an eye pass is active, the framebuffer's own
 * {@code glViewport(0, 0, framebufferWidth, framebufferHeight)} call (triggered by
 * {@code bindFramebuffer(true)}) wipes out the per-eye viewport set by
 * {@link MixinEntityRenderer_Stereo}. Restore it here.
 */
@Mixin(Framebuffer.class)
public class MixinFramebuffer_Stereo {

    @Inject(method = "bindFramebuffer", at = @At("RETURN"))
    private void angelica$restoreStereoViewport(boolean updateViewport, CallbackInfo ci) {
        if (!updateViewport) return;
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        if (mode == null || !mode.isActive()) return;
        final StereoState.Eye eye = StereoState.INSTANCE.getCurrentEye();
        if (eye != StereoState.Eye.LEFT && eye != StereoState.Eye.RIGHT) return;

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();
        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        final int x, y;
        if (eye == StereoState.Eye.LEFT) {
            x = 0;
            y = sbs ? 0 : fullH - eyeH;
        } else {
            x = sbs ? eyeW : 0;
            y = 0;
        }
        GL11.glViewport(x, y, eyeW, eyeH);
    }
}
