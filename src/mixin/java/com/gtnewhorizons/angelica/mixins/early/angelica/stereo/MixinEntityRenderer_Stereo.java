package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.stereo.StereoCursor;
import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Priority 1100 (above HUD_CACHING's default) so our renderGameOverlay redirect wins. The
 * non-stereo / STRETCH paths delegate to {@link HUDCaching#renderCachedHud} to preserve
 * HUD-caching perf for users who didn't enable stereo.
 */
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class MixinEntityRenderer_Stereo {

    @Shadow public abstract void renderWorld(float partialTicks, long finishTimeNano);

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void angelica$stereoBeginFrame(float partialTicks, CallbackInfo ci) {
        StereoState.INSTANCE.beginFrame();
        StereoCursor.update();
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
            // Stereo off — defer to HUDCaching since we won the @Redirect priority fight.
            HUDCaching.renderCachedHud(
                Minecraft.getMinecraft().entityRenderer, ingame, partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        final StereoHudMode hudMode = StereoState.INSTANCE.getFrameHudMode();
        if (hudMode == StereoHudMode.HIDE) {
            return;
        }
        if (hudMode == StereoHudMode.STRETCH) {
            HUDCaching.renderCachedHud(
                Minecraft.getMinecraft().entityRenderer, ingame, partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        // DUPLICATE: keep HUD caching alive — render once into the cache FBO at full resolution,
        // then blit the cache into each eye's viewport. HUDCaching.renderCachedHudStereo handles
        // viewport setup, enterGuiPass/exitGuiPass, and the per-eye blit.
        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final boolean sbs = mode.isSideBySide();
        final boolean half = mode.isHalf();

        final int eyeW = sbs ? (half ? fullW / 2 : fullW) : fullW;
        final int eyeH = sbs ? fullH               : (half ? fullH / 2 : fullH);

        final int leftX = 0;
        final int leftY = sbs ? 0 : fullH - eyeH;
        final int rightX = sbs ? eyeW : 0;
        final int rightY = 0;

        HUDCaching.renderCachedHudStereo(
            mc.entityRenderer, ingame, partialTicks, hasScreen, mouseX, mouseY,
            leftX, leftY, rightX, rightY, eyeW, eyeH);
    }

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

        // mouseX/mouseY are already remapped: the upstream call site computed them from
        // Mouse.getX() * scaledWidth / displayWidth, and GLSMRedirector has rewritten
        // Mouse.getX/getEventX to StereoCursor's virtual coords (already left-eye GUI space).

        // LEFT
        final int leftY = sbs ? 0 : fullH - eyeH;
        GL11.glViewport(0, leftY, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(0, leftY, eyeW, eyeH);
        screen.drawScreen(mouseX, mouseY, partialTicks);
        StereoState.INSTANCE.exitGuiPass();

        // RIGHT
        final int rightX = sbs ? eyeW : 0;
        GL11.glViewport(rightX, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(rightX, 0, eyeW, eyeH);
        screen.drawScreen(mouseX, mouseY, partialTicks);
        StereoState.INSTANCE.exitGuiPass();

        // Cursor drawing happens in angelica$stereoPostEvent after NEI's overlay (drawn during
        // the DrawScreenEvent.Post that fires after this redirect's call site).
        GL11.glViewport(0, 0, fullW, fullH);
    }

    /**
     * Wrap the {@code DrawScreenEvent.Post} dispatch. NEI and other mods render tooltips,
     * overlay buttons, and item icons during that event; we post it twice (once per eye
     * viewport) so the items appear in both eyes, then draw the synthetic cursor on top.
     * The {@code ordinal = 1} targets the second {@code EVENT_BUS.post} in
     * {@code updateCameraAndRender} — Pre is ordinal 0, Post is ordinal 1.
     */
    @Redirect(
        method = "updateCameraAndRender",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/eventhandler/EventBus;post(Lcpw/mods/fml/common/eventhandler/Event;)Z",
            ordinal = 1,
            remap = false
        )
    )
    private boolean angelica$stereoPostEvent(EventBus bus, Event event) {
        final StereoMode mode = StereoState.INSTANCE.getFrameMode();
        final boolean stereoActive = mode != null && mode.isActive()
            && StereoState.INSTANCE.getFrameHudMode() == StereoHudMode.DUPLICATE
            && mode.isSideBySide() && mode.isHalf();
        if (!stereoActive) {
            return bus.post(event);
        }

        final Minecraft mc = Minecraft.getMinecraft();
        final int fullW = mc.displayWidth;
        final int fullH = mc.displayHeight;
        final int eyeW = fullW / 2;
        final int eyeH = fullH;

        // LEFT eye: post the event so NEI's overlay + tooltip items render here.
        GL11.glViewport(0, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(0, 0, eyeW, eyeH);
        final boolean result = bus.post(event);
        StereoState.INSTANCE.exitGuiPass();

        // RIGHT eye: Forge phase-tracking refuses a re-post of the same event instance, so build
        // a fresh DrawScreenEvent.Post with the same params.
        GL11.glViewport(eyeW, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(eyeW, 0, eyeW, eyeH);
        if (event instanceof GuiScreenEvent.DrawScreenEvent.Post) {
            final GuiScreenEvent.DrawScreenEvent.Post original = (GuiScreenEvent.DrawScreenEvent.Post) event;
            final GuiScreenEvent.DrawScreenEvent.Post copy =
                new GuiScreenEvent.DrawScreenEvent.Post(
                    original.gui, original.mouseX, original.mouseY, original.renderPartialTicks);
            bus.post(copy);
        }
        StereoState.INSTANCE.exitGuiPass();

        // Synthetic cursor on top of everything, in both halves. OS cursor is hidden by
        // Mouse.setGrabbed when stereo+GUI is active, so this is the only visible cursor.
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int scaledWidth = sr.getScaledWidth();
        final int scaledHeight = sr.getScaledHeight();
        final int cursorX = Mouse.getX() * scaledWidth / mc.displayWidth;
        final int cursorY = scaledHeight - Mouse.getY() * scaledHeight / mc.displayHeight - 1;

        // RIGHT half cursor (viewport still right).
        angelica$drawSyntheticCursor(cursorX, cursorY);
        // LEFT half cursor.
        GL11.glViewport(0, 0, eyeW, eyeH);
        angelica$drawSyntheticCursor(cursorX, cursorY);

        GL11.glViewport(0, 0, fullW, fullH);
        return result;
    }

    /**
     * White arrow with 1px black outline; tip at (x, y). Item icons drawn earlier in the frame
     * leave depth values that would occlude this rect, so depth-test is toggled around it.
     */
    private static void angelica$drawSyntheticCursor(int x, int y) {
        final int B = 0xFF000000;
        final int F = 0xFFFFFFFF;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < 9; i++) {
            Gui.drawRect(x - 1, y + i - 1, x + i + 2, y + i, B);
        }
        Gui.drawRect(x - 1, y + 8, x + 9, y + 9, B);
        for (int i = 0; i < 8; i++) {
            Gui.drawRect(x, y + i, x + i + 1, y + i + 1, F);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
