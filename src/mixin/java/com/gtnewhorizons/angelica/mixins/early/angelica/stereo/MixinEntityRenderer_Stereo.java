package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
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
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class MixinEntityRenderer_Stereo {

    @Shadow public abstract void renderWorld(float partialTicks, long finishTimeNano);

    /**
     * Snapshot stereo config at frame start. Idempotent if stereo is OFF.
     */
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void angelica$stereoBeginFrame(float partialTicks, CallbackInfo ci) {
        StereoState.INSTANCE.beginFrame();
        angelica$constrainCursorToLeftEye();
    }

    /**
     * For SBS_HALF + DUPLICATE, snap the OS cursor back into the left half whenever the user
     * drags it past the seam. Hover/click hit-tests are calibrated to the left eye's view.
     */
    private static void angelica$constrainCursorToLeftEye() {
        if (!org.lwjgl.opengl.Display.isActive()) return;
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        if (mode == null || !mode.isActive()) return;
        if (StereoState.INSTANCE.getFrameHudMode() != StereoHudMode.DUPLICATE) return;
        if (!mode.isSideBySide() || !mode.isHalf()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) return;
        final int halfW = mc.displayWidth / 2;
        if (org.lwjgl.input.Mouse.getX() > halfW - 1) {
            org.lwjgl.input.Mouse.setCursorPosition(halfW - 1, org.lwjgl.input.Mouse.getY());
        }
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

    /**
     * Replace the {@code currentScreen.drawScreen} call with the chosen HUD strategy.
     * Open menus (chest, inventory, NEI, etc.) render via this path; without duplicating, they
     * appear stretched across the full screen and don't line up with either eye's viewport.
     */
    @Redirect(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"
        )
    )
    private void angelica$stereoDrawScreen(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        if (mode == null || !mode.isActive()) {
            screen.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        final StereoHudMode hudMode = StereoState.INSTANCE.getFrameHudMode();
        if (hudMode == StereoHudMode.HIDE) {
            return;
        }
        if (hudMode == StereoHudMode.STRETCH) {
            screen.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();
        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        // Mouse remap: the GUI is rendered into a half-width viewport with a full-screen
        // ScaledResolution, so a slot at GUI-x N appears at screen pixel N/2. We want the cursor
        // (clamped to the left half, screen-x in [0, fullW/2]) to map to the full GUI-x range.
        // Since the vanilla code already passed mouseX = Mouse.getX() * scaledWidth / fullW,
        // doubling that gives us the GUI-x within the left eye's view.
        final int adjMouseX = (sbs && half) ? (mouseX * 2) : mouseX;
        final int adjMouseY = (!sbs && half) ? (mouseY * 2 - 0) : mouseY; // OU stub; we focus on SBS

        // LEFT
        GL11.glViewport(0, sbs ? 0 : fullH - eyeH, eyeW, eyeH);
        screen.drawScreen(adjMouseX, adjMouseY, partialTicks);

        // RIGHT
        GL11.glViewport(sbs ? eyeW : 0, 0, eyeW, eyeH);
        screen.drawScreen(adjMouseX, adjMouseY, partialTicks);

        // Restore.
        GL11.glViewport(0, 0, fullW, fullH);
    }
}
