package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Virtual cursor for stereo SBS_HALF + DUPLICATE rendering. While stereo+GUI is active, the OS
 * cursor is grabbed and we track our own (vX, vY) by accumulating {@link Mouse#getDX}/{@link Mouse#getDY}
 * deltas, clamped to the left half of the screen. {@code GLSMRedirector} rewrites
 * {@code Mouse.getX/getY/getEventX/getEventY} call sites to {@code GLStateManager.stereoMouseGet*},
 * which delegate to this class via {@code StereoGLSMBridge}'s {@code StereoHook} impl — so all
 * GUI/mod hit-test code transparently sees the virtual cursor in the left-eye GUI-coord range.
 * Y-axis follows LWJGL: {@code Mouse.getY() == 0} is window-bottom.
 */
public final class StereoCursor {

    private static double vX = 0;
    private static double vY = 0;
    private static boolean weAreGrabbing = false;

    private StereoCursor() {}

    /** Called once per frame from MixinEntityRenderer_Stereo's begin-frame inject. */
    public static void update() {
        final boolean shouldOverride = shouldRemap() && Display.isActive();

        if (shouldOverride && !weAreGrabbing) {
            // Entering stereo+GUI: grab the mouse and seed the virtual cursor from the OS cursor
            // position (clamped into left-half coords).
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            int seedX = Mouse.getX();
            int seedY = Mouse.getY();
            if (seedX > halfW - 1) seedX = halfW - 1;
            if (seedX < 0) seedX = 0;
            if (seedY < 0) seedY = 0;
            if (seedY > mc.displayHeight - 1) seedY = mc.displayHeight - 1;
            vX = seedX;
            vY = seedY;
            Mouse.setGrabbed(true);
            weAreGrabbing = true;
        } else if (!shouldOverride && weAreGrabbing) {
            // Leaving stereo+GUI: restore OS cursor state — visible if a screen is open so the
            // user keeps clicking after disabling stereo, hidden in-game so MC camera resumes.
            Mouse.setGrabbed(Minecraft.getMinecraft().currentScreen == null);
            weAreGrabbing = false;
        }

        if (weAreGrabbing) {
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            final int fullH = mc.displayHeight;
            vX += Mouse.getDX();
            vY += Mouse.getDY();
            if (vX < 0)              vX = 0;
            if (vX > halfW - 1)      vX = halfW - 1;
            if (vY < 0)              vY = 0;
            if (vY > fullH - 1)      vY = fullH - 1;
        }
    }

    public static int virtualX() { return (int) vX; }
    public static int virtualY() { return (int) vY; }
    public static boolean isActive() { return weAreGrabbing; }

    // SBS_HALF only compresses horizontally, so the GUI scaled-coord X must be doubled to cover
    // the full left-eye GUI range; Y is unchanged.
    // (Note: GLSMRedirector rewrites Mouse.getX/Y/EventX/EventY call sites to GLStateManager
    // helpers that call into these via StereoGLSMBridge's StereoHook impl.)

    public static int getX() {
        if (!weAreGrabbing) return Mouse.getX();
        return (int) (vX * 2);
    }

    public static int getY() {
        if (!weAreGrabbing) return Mouse.getY();
        return (int) vY;
    }

    public static int getEventX() {
        if (!weAreGrabbing) return Mouse.getEventX();
        return (int) (vX * 2);
    }

    public static int getEventY() {
        if (!weAreGrabbing) return Mouse.getEventY();
        return (int) vY;
    }

    private static boolean shouldRemap() {
        final StereoMode mode = AngelicaConfig.stereoscopicMode;
        if (mode == null || !mode.isActive()) return false;
        if (AngelicaConfig.stereoHudMode != StereoHudMode.DUPLICATE) return false;
        if (!mode.isSideBySide() || !mode.isHalf()) return false;
        final Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.currentScreen != null;
    }
}
