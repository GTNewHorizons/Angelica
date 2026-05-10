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
 * Drives stereoscopic rendering by:
 * <ol>
 *   <li>Initializing {@link StereoState} at the start of {@code updateCameraAndRender}.</li>
 *   <li>Redirecting the inner {@code renderWorld} call to a two-pass loop with per-eye
 *       viewports and {@code StereoState.currentEye} flipping between passes.</li>
 *   <li>Redirecting the inner {@code renderGameOverlay} call to either skip, stretch,
 *       or duplicate the HUD per {@link StereoHudMode}.</li>
 *   <li>Tearing down state at the end.</li>
 * </ol>
 *
 * <p>Priority is set higher (lower number) than the HUD-caching mixin so that when stereo is
 * active our redirect on {@code renderGameOverlay} replaces the HUD-caching one. We also
 * defensively force HUD caching off when stereo activates (see {@link StereoState#beginFrame()}
 * — actually handled at config-load time; see step 7 in the implementation plan).</p>
 */
@Mixin(value = EntityRenderer.class, priority = 900)
public abstract class MixinEntityRenderer_Stereo {

    @Shadow public abstract void renderWorld(float partialTicks, long finishTimeNano);

    /**
     * Snapshot stereo config at frame start. Idempotent if stereo is OFF.
     */
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void angelica$stereoBeginFrame(float partialTicks, CallbackInfo ci) {
        StereoState.INSTANCE.beginFrame();
    }

    /**
     * Tear down stereo state at frame end and restore the full viewport for any subsequent
     * rendering (e.g. screenshots, frame-end overlays).
     */
    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void angelica$stereoEndFrame(float partialTicks, CallbackInfo ci) {
        if (StereoState.INSTANCE.isActive()) {
            Minecraft mc = Minecraft.getMinecraft();
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        StereoState.INSTANCE.endFrame();
    }

    /**
     * Replace the single {@code renderWorld} call with two calls, one per eye, with
     * appropriate viewports.
     */
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
            // Debug-only camera offset and stereo OFF both land here — no viewport split.
            self.renderWorld(partialTicks, finishTimeNano);
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;

        // Compute per-eye viewport dimensions and offsets.
        // SBS: split horizontally. OU: split vertically.
        // HALF: each eye gets half the split-axis dimension at native aspect on the other axis.
        // FULL: each eye gets full screen on the split axis (squished).
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();

        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        final int leftX  = 0;
        final int leftY  = sbs ? 0 : fullH - eyeH; // OU: left eye is on top (Y is bottom-origin in GL)
        final int rightX = sbs ? eyeW : 0;
        final int rightY = sbs ? 0    : 0;

        // Scissor confines glClear and writes to the eye's viewport region — without it,
        // the second pass's clear erases the first pass's output.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        // ===== LEFT EYE =====
        StereoState.INSTANCE.setEye(StereoState.Eye.LEFT);
        GL11.glViewport(leftX, leftY, eyeW, eyeH);
        GL11.glScissor(leftX, leftY, eyeW, eyeH);
        self.renderWorld(partialTicks, finishTimeNano);

        // ===== RIGHT EYE =====
        StereoState.INSTANCE.setEye(StereoState.Eye.RIGHT);
        GL11.glViewport(rightX, rightY, eyeW, eyeH);
        GL11.glScissor(rightX, rightY, eyeW, eyeH);
        self.renderWorld(partialTicks, finishTimeNano);

        // Restore full viewport + disable scissor so vanilla HUD code and our HUD redirect see consistent state.
        StereoState.INSTANCE.setEye(StereoState.Eye.MONO);
        GL11.glViewport(0, 0, fullW, fullH);
        GL11.glScissor(0, 0, fullW, fullH);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Replace the single {@code renderGameOverlay} call with the chosen HUD strategy.
     */
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

        // LEFT
        GL11.glViewport(0, sbs ? 0 : fullH - eyeH, eyeW, eyeH);
        ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);

        // RIGHT
        GL11.glViewport(sbs ? eyeW : 0, 0, eyeW, eyeH);
        ingame.renderGameOverlay(partialTicks, hasScreen, mouseX, mouseY);

        // Restore.
        GL11.glViewport(0, 0, fullW, fullH);
    }
}
