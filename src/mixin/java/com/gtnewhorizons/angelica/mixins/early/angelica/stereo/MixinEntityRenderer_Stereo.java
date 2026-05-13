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
        com.gtnewhorizons.angelica.stereo.StereoCursor.update();
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
        // Note: cursor thread's publishFrame() is intentionally NOT called here. It is called
        // later in the frame at framebufferRender HEAD (see MixinFramebuffer_AsyncCursor), so
        // we capture framebufferMc after onRenderTickEnd has had a chance to add its content
        // (WAILA HUD, achievement popups, etc.). Capturing here would miss those.
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

        // Scissor protects each eye's region of the main framebuffer from vanilla MC's glClear
        // at the start of renderWorld — without it, the right eye's clear would wipe the left
        // eye's already-rendered content from the main FB. With shaders, Iris binds its own
        // half-width intermediate FBOs partway through renderWorld; an active scissor in
        // main-FB pixel coords would clip every fragment written into those FBOs to nothing.
        // DeferredWorldRenderingPipeline.beginLevelRendering disables scissor at that handoff
        // point, so scissor is only "live" during the vanilla pre-Iris portion of renderWorld.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        // ===== LEFT EYE =====
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

        // ===== RIGHT EYE =====
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
            // Stereo off — fall back to HUDCaching so non-stereo users keep their cached-HUD perf
            // (we won the @Redirect priority fight; this delegation restores caching behavior).
            com.gtnewhorizons.angelica.hudcaching.HUDCaching.renderCachedHud(
                Minecraft.getMinecraft().entityRenderer, ingame, partialTicks, hasScreen, mouseX, mouseY);
            return;
        }

        final StereoHudMode hudMode = StereoState.INSTANCE.getFrameHudMode();
        if (hudMode == StereoHudMode.HIDE) {
            return;
        }
        if (hudMode == StereoHudMode.STRETCH) {
            com.gtnewhorizons.angelica.hudcaching.HUDCaching.renderCachedHud(
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

        com.gtnewhorizons.angelica.hudcaching.HUDCaching.renderCachedHudStereo(
            mc.entityRenderer, ingame, partialTicks, hasScreen, mouseX, mouseY,
            leftX, leftY, rightX, rightY, eyeW, eyeH);
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

        // mouseX/mouseY already arrive correctly remapped: the call site upstream computed them
        // from Mouse.getX() * scaledWidth / displayWidth, and AngelicaRedirector has rewritten
        // Mouse.getX/getEventX to StereoCursor's virtual coords (already in left-eye GUI space).

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
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        screen.drawScreen(mouseX, mouseY, partialTicks);
        StereoState.INSTANCE.exitGuiPass();

        // RIGHT
        final int rightX = sbs ? eyeW : 0;
        GL11.glViewport(rightX, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(rightX, 0, eyeW, eyeH);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        screen.drawScreen(mouseX, mouseY, partialTicks);
        StereoState.INSTANCE.exitGuiPass();

        // Restore. Cursor drawing happens in angelica$drawCursorAfterPostEvent so it lands
        // on top of NEI's overlay (which draws during the DrawScreenEvent.Post that fires
        // after the drawScreen call site we just intercepted).
        GL11.glViewport(0, 0, fullW, fullH);
    }

    /**
     * Inject after the second {@code EVENT_BUS.post} call in {@code updateCameraAndRender}
     * (the {@code DrawScreenEvent.Post} dispatch). NEI/other mods draw their GUI overlays
     * during that event, so the synthetic cursor must be drawn after it to land on top.
     */
    /**
     * Wrap the {@code DrawScreenEvent.Post} dispatch — NEI/other mods render their tooltips,
     * overlay buttons, and the GTNHLib recipe-tooltip widget's item icons during that event.
     * Post the event twice (once per eye viewport) so the items appear in both eyes, then draw
     * the synthetic cursor in both halves on top.
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
    private boolean angelica$stereoPostEvent(cpw.mods.fml.common.eventhandler.EventBus bus,
                                              cpw.mods.fml.common.eventhandler.Event event) {
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

        // RIGHT eye: Forge events track phase (HIGHEST → NORMAL → ...) and refuse to be
        // re-posted, so construct a fresh DrawScreenEvent.Post with the same params.
        GL11.glViewport(eyeW, 0, eyeW, eyeH);
        StereoState.INSTANCE.enterGuiPass(eyeW, 0, eyeW, eyeH);
        if (event instanceof net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post) {
            final net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post original =
                (net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post) event;
            final net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post copy =
                new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post(
                    original.gui, original.mouseX, original.mouseY, original.renderPartialTicks);
            bus.post(copy);
        }
        StereoState.INSTANCE.exitGuiPass();

        // Draw the synthetic cursor in both halves on top of everything — UNLESS the async
        // cursor present thread is running, in which case it owns cursor presentation at its
        // own (compositor) rate. Drawing here too would cause a double-cursor + the main-frame
        // copy would capture the stale main-thread cursor into the cursor thread's present
        // texture.
        if (!com.gtnewhorizons.angelica.stereo.CursorPresentThread.isRunning()) {
            final net.minecraft.client.gui.ScaledResolution sr =
                new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            final int scaledWidth = sr.getScaledWidth();
            final int scaledHeight = sr.getScaledHeight();
            final int cursorX = org.lwjgl.input.Mouse.getX() * scaledWidth / mc.displayWidth;
            final int cursorY = scaledHeight - org.lwjgl.input.Mouse.getY() * scaledHeight / mc.displayHeight - 1;

            // RIGHT half cursor (viewport still right).
            angelica$drawSyntheticCursor(cursorX, cursorY);
            // LEFT half cursor.
            GL11.glViewport(0, 0, eyeW, eyeH);
            angelica$drawSyntheticCursor(cursorX, cursorY);
        }
        // Restore.
        GL11.glViewport(0, 0, fullW, fullH);
        return result;
    }

    /**
     * Switch Iris's active eye (if a shaderpack is loaded). When stereo is active each eye has
     * its own set of color and depth+stencil textures in {@code RenderTargets}; calling this
     * rebinds the owned framebuffer attachments to the requested eye's textures so subsequent
     * Iris passes write into and sample from the right bubble. No-op if no pipeline.
     */
    private static void angelica$setIrisActiveEye(int eyeIndex) {
        try {
            final net.coderbot.iris.pipeline.WorldRenderingPipeline pipeline =
                net.coderbot.iris.Iris.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.setActiveEye(eyeIndex);
            }
        } catch (Throwable ignored) {
            // Iris not present or pipeline not yet constructed — fall through. Treating this as
            // best-effort keeps the stereo path working when no shaderpack is loaded.
        }
    }

    /**
     * Draw an arrow cursor in GUI coords at (x, y). Right-angle triangle tip at (x, y), filled
     * white with a 1px black outline. The OS cursor is hidden by {@code Mouse.setGrabbed} when
     * stereo+GUI is active, so this is the only visible cursor.
     */
    private static void angelica$drawSyntheticCursor(int x, int y) {
        final int B = 0xFF000000;
        final int F = 0xFFFFFFFF;
        // Item icons drawn earlier in the frame leave depth values that would occlude this rect.
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < 9; i++) {
            net.minecraft.client.gui.Gui.drawRect(x - 1, y + i - 1, x + i + 2, y + i, B);
        }
        net.minecraft.client.gui.Gui.drawRect(x - 1, y + 8, x + 9, y + 9, B);
        for (int i = 0; i < 8; i++) {
            net.minecraft.client.gui.Gui.drawRect(x, y + i, x + i + 1, y + i + 1, F);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
