package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.EntityRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Priority 900 (lower than HUD_CACHING's default) so our renderGameOverlay redirect wins over
 * the HUD-caching one when stereo is active. HUD caching is also force-disabled at config load
 * when stereo is enabled (see step 7 in the rebase plan).
 */
@Mixin(value = EntityRenderer.class, priority = 900)
public abstract class MixinEntityRenderer_Stereo {

    @Shadow public abstract void renderWorld(float partialTicks, long finishTimeNano);

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void angelica$stereoBeginFrame(float partialTicks, CallbackInfo ci) {
        StereoState.INSTANCE.beginFrame();
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void angelica$stereoEndFrame(float partialTicks, CallbackInfo ci) {
        if (StereoState.INSTANCE.isActive()) {
            // Restore the full viewport for anything that runs after this frame's end
            // (screenshots, post-frame overlays) and assumes the default state.
            Minecraft mc = Minecraft.getMinecraft();
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        StereoState.INSTANCE.endFrame();
    }

    @Redirect(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V"
        )
    )
    private void angelica$stereoRenderWorld(EntityRenderer self, float partialTicks, long finishTimeNano) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        if (mode == null || !mode.isActive()) {
            self.renderWorld(partialTicks, finishTimeNano);
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;

        // SBS splits horizontally; OU splits vertically. HALF gets half the split-axis dimension;
        // FULL keeps the full split-axis dimension (so the image is squished in the output).
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();

        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        final int leftX  = 0;
        final int leftY  = sbs ? 0 : fullH - eyeH; // OU: left eye is on top (GL is bottom-origin).
        final int rightX = sbs ? eyeW : 0;
        final int rightY = 0;

        // Scissor confines the second pass's glClear so it doesn't erase the first eye.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        StereoState.INSTANCE.setEye(StereoState.Eye.LEFT);
        GL11.glViewport(leftX, leftY, eyeW, eyeH);
        GL11.glScissor(leftX, leftY, eyeW, eyeH);
        self.renderWorld(partialTicks, finishTimeNano);

        StereoState.INSTANCE.setEye(StereoState.Eye.RIGHT);
        GL11.glViewport(rightX, rightY, eyeW, eyeH);
        GL11.glScissor(rightX, rightY, eyeW, eyeH);
        self.renderWorld(partialTicks, finishTimeNano);

        // Restore full viewport and disable scissor so the HUD redirect (and vanilla overlay
        // code) see consistent default state.
        StereoState.INSTANCE.setEye(StereoState.Eye.MONO);
        GL11.glViewport(0, 0, fullW, fullH);
        GL11.glScissor(0, 0, fullW, fullH);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Redirect(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(FZII)V"
        )
    )
    private void angelica$stereoRenderHud(GuiIngame ingame, float partialTicks, boolean hasScreen, int mouseX, int mouseY) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        if (mode == null || !mode.isActive()) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        final StereoHudMode hudMode = StereoState.INSTANCE.getFrameHudMode();
        if (hudMode == StereoHudMode.HIDE) {
            return;
        }
        if (hudMode == StereoHudMode.STRETCH) {
            ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        // DUPLICATE: render HUD into each eye's viewport.
        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();

        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        GL11.glViewport(0, sbs ? 0 : fullH - eyeH, eyeW, eyeH);
        ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);

        GL11.glViewport(sbs ? eyeW : 0, 0, eyeW, eyeH);
        ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);

        GL11.glViewport(0, 0, fullW, fullH);
    }
}
