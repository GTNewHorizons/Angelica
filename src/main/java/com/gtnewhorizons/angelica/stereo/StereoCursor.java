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
            // Entering stereo+GUI: grab the mouse so the OS cursor is hidden, then seed the
            // virtual cursor at the LEFT eye's center. MC re-centers Mouse to (displayW/2,
            // displayH/2) when a GUI opens; that lands on the seam between eyes (rightmost
            // pixel of left eye), which made the cursor feel like it was pinned to the right
            // edge. Force it to the center of the left eye region instead.
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            vX = halfW / 2;
            vY = mc.displayHeight / 2;
            Mouse.setGrabbed(true);
            weAreGrabbing = true;
        } else if (!shouldOverride && weAreGrabbing) {
            // Leaving stereo+GUI: restore the cursor to the appropriate state — visible if a GUI
            // is open (so user can keep clicking after disabling stereo), hidden in-game so MC's
            // camera control resumes.
            Mouse.setGrabbed(Minecraft.getMinecraft().currentScreen == null);
            weAreGrabbing = false;
        }

        if (weAreGrabbing) {
            final Minecraft mc = Minecraft.getMinecraft();
            final int halfW = mc.displayWidth / 2;
            final int fullH = mc.displayHeight;
            // SBS_HALF compresses each eye's GUI horizontally into half the screen width, so a
            // button that's N pixels wide in mono is N/2 pixels wide on screen here. Without
            // the 0.5 scale, the cursor would cross the GUI in half the mouse motion compared
            // to mono — feeling 2× too sensitive horizontally. The 0.5 keeps the mouse-pixel
            // to on-screen-cursor-pixel feel consistent with mono.
            vX += Mouse.getDX() * 0.5;
            vY += Mouse.getDY();
            if (vX < 0)              vX = 0;
            if (vX > halfW - 1)      vX = halfW - 1;
            if (vY < 0)              vY = 0;
            if (vY > fullH - 1)      vY = fullH - 1;
        }
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
