package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Virtual cursor for stereo SBS_HALF + DUPLICATE rendering. While stereo+GUI is active, the OS
 * cursor is hidden and the cursor present thread polls GetCursorPos to drive (vX, vY).
 * {@code GLSMRedirector} rewrites {@code Mouse.getX/getY/getEventX/getEventY} call sites to
 * {@code GLStateManager.stereoMouseGet*}, which delegate to this class via
 * {@code StereoGLSMBridge}'s {@code StereoHook} impl — so GUI/mod hit-test code sees the
 * virtual cursor in the left-eye GUI-coord range. Y-axis follows LWJGL:
 * {@code Mouse.getY() == 0} is window-bottom.
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
            // We use HIDDEN (not DISABLED via Mouse.setGrabbed) so the OS cursor tracks mouse
            // motion normally — the cursor present thread polls GetCursorPos every iteration to
            // update vX/vY at its own rate, decoupling cursor responsiveness from main's
            // framerate. With DISABLED, GetCursorPos would return the locked center and the
            // cursor thread couldn't observe motion at all.
            // Seed at left-eye center: MC re-centers Mouse to (displayW/2, displayH/2) on GUI
            // open, which lands on the seam between eyes (rightmost pixel of left eye).
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            vX = halfW / 2;
            vY = mc.displayHeight / 2;
            CursorPresentThread.setCursorHidden(true);
            CursorPresentThread.resetCursorPolling();
            weAreGrabbing = true;
        } else if (!shouldOverride && weAreGrabbing) {
            // We MUST force the GLFW mode directly via setCursorMode rather than relying on
            // Mouse.setGrabbed alone — MC caches its own grab-flag and skips the underlying
            // glfwSetInputMode call when the cache already matches. We changed GLFW to HIDDEN
            // behind MC's back on entry, so MC's cache (still "true" from the pre-stereo grab)
            // and GLFW's actual state (HIDDEN) are out of sync. Without this, GLFW would stay
            // in HIDDEN after exit, and the cursor would leak out of the window during in-game
            // mouse-look.
            CursorPresentThread.releaseClipCursor();
            final Minecraft mc = Minecraft.getMinecraft();
            final boolean wantGrab = mc.currentScreen == null;
            CursorPresentThread.setCursorMode(wantGrab
                ? CursorPresentThread.CURSOR_DISABLED
                : CursorPresentThread.CURSOR_NORMAL);
            Mouse.setGrabbed(wantGrab); // also resync MC's cache
            weAreGrabbing = false;
        }
        // Cursor present thread owns vX/vY now — no per-frame update here.
    }

    /**
     * Set the virtual cursor's absolute position. Called from the cursor present thread each
     * iteration after computing the OS cursor's position relative to MC's client area. Absolute
     * (not accumulated-delta) positioning avoids asymmetric clamping drift: with deltas, when
     * the OS cursor sits at a clip-rect edge, vX/vY clamp at the edge but later
     * opposite-direction motion would start from that clamp, leaving virtual and OS cursors
     * out of sync.
     */
    public static synchronized void setVirtualPos(double vx, double vy) {
        if (!weAreGrabbing) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        final int halfW = mc.displayWidth / 2;
        final int fullH = mc.displayHeight;
        if (vx < 0)              vx = 0;
        if (vx > halfW - 1)      vx = halfW - 1;
        if (vy < 0)              vy = 0;
        if (vy > fullH - 1)      vy = fullH - 1;
        vX = vx;
        vY = vy;
    }

    /** Cursor X in window pixels. Bottom-up Y is in {@link #virtualY()}. */
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
