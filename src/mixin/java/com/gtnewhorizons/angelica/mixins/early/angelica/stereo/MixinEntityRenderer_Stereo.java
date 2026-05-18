package com.gtnewhorizons.angelica.mixins.early.angelica.stereo;

import com.gtnewhorizons.angelica.compat.chromatictooltips.ChromaticTooltipsCompat;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.stereo.CursorPresentThread;
import com.gtnewhorizons.angelica.stereo.StereoCursor;
import com.gtnewhorizons.angelica.stereo.StereoHudMode;
import com.gtnewhorizons.angelica.stereo.StereoMode;
import com.gtnewhorizons.angelica.stereo.StereoState;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderHelper;
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
        // Note: cursor thread's publishFrame() is intentionally NOT called here. It is called
        // later in the frame at framebufferRender HEAD (see MixinFramebuffer_AsyncCursor), so
        // we capture framebufferMc after onRenderTickEnd has had a chance to add its content
        // (WAILA HUD, achievement popups, etc.). Capturing here would miss those.
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

        // Scissor protects each eye's region of the main framebuffer from vanilla MC's glClear
        // at the start of renderWorld — without it, the right eye's clear would wipe the left
        // eye's already-rendered content from the main FB. With shaders, Iris binds its own
        // half-width intermediate FBOs partway through renderWorld; an active scissor in
        // main-FB pixel coords would clip every fragment written into those FBOs to nothing.
        // DeferredWorldRenderingPipeline.beginLevelRendering disables scissor at that handoff
        // point, so scissor is only "live" during the vanilla pre-Iris portion of renderWorld.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        StereoState.INSTANCE.setEye(StereoState.Eye.LEFT);
        GL11.glViewport(leftX, leftY, eyeW, eyeH);
        GL11.glScissor(leftX, leftY, eyeW, eyeH);
        // enterWorldPass: any subsequent glViewport call that asks for the main FB's full size
        // (Iris's Pass.use, CompositeRenderer, ClearPass) gets remapped to this eye's viewport.
        StereoState.INSTANCE.enterWorldPass(leftX, leftY, eyeW, eyeH);
        // Tell Iris (if a shaderpack is loaded) to swap its render targets to this eye's set,
        // so all subsequent FBO binds + samplers resolve to the LEFT eye's textures.
        angelica$setIrisActiveEye(0);
        self.renderWorld(partialTicks, finishTimeNano);
        StereoState.INSTANCE.exitWorldPass();

        StereoState.INSTANCE.setEye(StereoState.Eye.RIGHT);
        GL11.glViewport(rightX, rightY, eyeW, eyeH);
        // Re-enable scissor here — beginLevelRendering may have disabled it for the left eye.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rightX, rightY, eyeW, eyeH);
        StereoState.INSTANCE.enterWorldPass(rightX, rightY, eyeW, eyeH);
        angelica$setIrisActiveEye(1);
        self.renderWorld(partialTicks, finishTimeNano);
        StereoState.INSTANCE.exitWorldPass();
        // Restore eye 0 as the "active" eye for any post-world Iris state lookups.
        angelica$setIrisActiveEye(0);

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

        // Before each eye's drawScreen, reset color/lighting state. GuiContainer.drawScreen leaves
        // GUI item lighting enabled at exit (RenderHelper.enableGUIStandardItemLighting on line 99 of
        // vanilla GuiContainer.drawScreen), which is fine when drawScreen is called once per frame —
        // the next frame's renderGameOverlay resets it. But in stereo we call drawScreen TWICE
        // back-to-back, so the RIGHT eye enters with lighting still on from LEFT eye's exit. That
        // causes the chest panel's drawGuiContainerBackgroundLayer (which sets color white but
        // doesn't disable lighting) to be lit, dimming the panel relative to the LEFT eye.

        // LEFT
        final int leftY = sbs ? 0 : fullH - eyeH;
        GL11.glViewport(0, leftY, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(0, leftY, eyeW, eyeH);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        screen.drawScreen(mouseX, mouseY, partialTicks);
        StereoState.INSTANCE.exitGuiPass();

        // RIGHT
        final int rightX = sbs ? eyeW : 0;
        GL11.glViewport(rightX, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(rightX, 0, eyeW, eyeH);
        RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
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

        // Re-arm ChromaticTooltips' deferred-render flag so the RIGHT eye Post draws too.
        ChromaticTooltipsCompat.rearm();

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

        if (!CursorPresentThread.isRunning()) {
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
        }

        GL11.glViewport(0, 0, fullW, fullH);
        return result;
    }

    // When stereo is active each eye has its own set of color and depth+stencil textures in
    // Iris's RenderTargets; this rebinds the owned framebuffer attachments to the requested
    // eye's textures so subsequent Iris passes write into and sample from the right bubble.
    // No-op if no pipeline (no shaderpack loaded).
    private static void angelica$setIrisActiveEye(int eyeIndex) {
        try {
            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.setActiveEye(eyeIndex);
            }
        } catch (Throwable ignored) {
            // Iris not present or pipeline not yet constructed — best-effort fall-through keeps
            // the stereo path working when no shaderpack is loaded.
        }
    }

    /** White arrow with 1px black outline; tip at (x, y). Item icons drawn earlier in the frame
     *  leave depth values that would occlude this rect, so depth-test is toggled around it. */
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
