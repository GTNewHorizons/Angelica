package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Virtual cursor for stereo SBS_HALF + DUPLICATE rendering. While stereo+GUI is active, the OS
 * cursor is grabbed (hidden + position-locked), and we track our own (vX, vY) by accumulating
 * {@link Mouse#getDX()} / {@link Mouse#getDY()} deltas, clamped to the left half of the screen.
 *
 * <p>{@link com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector} rewrites
 * {@code Mouse.getX/getY/getEventX/getEventY} call sites to the helpers in {@code GLStateManager}
 * which delegate here, so all GUI/mod hit-test code transparently sees the virtual cursor's
 * position scaled into the full GUI-coord range.</p>
 *
 * <p>Y-axis convention follows LWJGL: {@code Mouse.getY() == 0} is the bottom of the window;
 * {@code Mouse.getDY() > 0} when the user moves the mouse up.</p>
 */
public final class StereoCursor {

    private static double vX = 0;
    private static double vY = 0;
    private static boolean weAreGrabbing = false;

    private StereoCursor() {}

    /**
     * Called once per frame from {@code MixinEntityRenderer_Stereo}'s begin-frame inject.
     * Decides whether to grab the OS cursor and updates the virtual cursor from deltas.
     */
    public static void update() {
        final boolean shouldOverride = shouldRemap() && Display.isActive();

        if (shouldOverride && !weAreGrabbing) {
            // Entering stereo+GUI: hide the OS cursor and seed the virtual cursor at the LEFT
            // eye's center. We use HIDDEN (not DISABLED via Mouse.setGrabbed) so the OS cursor
            // tracks mouse motion normally — the cursor present thread polls GetCursorPos every
            // iteration to update vX/vY at its own rate, decoupling cursor responsiveness from
            // main's framerate. With DISABLED, GetCursorPos would return the locked center and
            // the cursor thread couldn't observe motion at all.
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            vX = halfW / 2;
            vY = mc.displayHeight / 2;
            CursorPresentThread.setCursorHidden(true);
            CursorPresentThread.resetCursorPolling();
            weAreGrabbing = true;
        } else if (!shouldOverride && weAreGrabbing) {
            // Leaving stereo+GUI: release ClipCursor and restore the appropriate cursor mode.
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
        // No per-frame vX/vY update on main — the cursor present thread owns position now.
    }

    /**
     * Set the virtual cursor's absolute position. Called from the cursor present thread each
     * iteration after computing the OS cursor's position relative to MC's client area. We use
     * absolute (not accumulated-delta) positioning specifically to avoid asymmetric clamping
     * drift: when the OS cursor sits at a clip-rect edge, accumulated deltas would clamp at the
     * vX/vY edge but later opposite-direction motion would start from that clamp, leaving the
     * virtual cursor and OS cursor out of sync. Absolute positioning guarantees that wherever
     * the OS cursor is in the client area, the virtual cursor is at the deterministic mapped
     * position — no drift possible.
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

    // --- Mouse.getX/getY/getEventX/getEventY redirect targets ---
    // The AngelicaRedirector class transformer rewrites Mouse.getX() etc. call sites to the
    // delegators in GLStateManager, which call these. When stereo+GUI isn't active, fall through
    // to the real LWJGL Mouse methods so non-stereo behavior is unchanged.

    public static int getX() {
        if (!weAreGrabbing) return Mouse.getX();
        // Double so the resulting GUI coord (Mouse.getX() * scaledWidth / displayWidth) covers
        // the full left-eye view's GUI range.
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
