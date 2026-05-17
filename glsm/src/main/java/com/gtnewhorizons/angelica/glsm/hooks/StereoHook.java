package com.gtnewhorizons.angelica.glsm.hooks;

import org.lwjgl.input.Mouse;

/**
 * Cross-module bridge for stereo SBS rendering. The main-mod side registers an impl via
 * {@link GLSMHooks#stereoHook}; {@code GLStateManager} consults it from inside {@code glScissor}
 * and the {@code stereoMouseGet*} entry points that {@code AngelicaRedirector} rewrites call
 * sites to.
 *
 * <p>The {@code glsm} subproject does not depend on the main mod by design — this interface is
 * the only contract.</p>
 */
public interface StereoHook {

    /**
     * If a GUI eye pass is active, remap incoming scissor coords (assumed "full-screen FB pixels")
     * into the current eye viewport, writing the remapped {@code (x, y, w, h)} into {@code outXYWH}
     * and returning {@code true}. Returns {@code false} when no remap is needed — caller passes
     * the original args through unchanged.
     */
    boolean remapScissor(int x, int y, int width, int height, int[] outXYWH);

    /**
     * If a world-pass eye render is active and the caller is asking for the full main-FB viewport
     * (the pattern Iris's Pass.use() and CompositeRenderer fall into at every shader phase change),
     * write the eye-sized viewport into {@code outXYWH} and return {@code true}. Otherwise return
     * {@code false} and the caller forwards the original args.
     */
    boolean remapWorldPassViewport(int x, int y, int width, int height, int[] outXYWH);

    int stereoMouseGetX();
    int stereoMouseGetY();
    int stereoMouseGetEventX();
    int stereoMouseGetEventY();

    /** Pass-through impl used when no stereo hook is registered. */
    StereoHook NONE = new StereoHook() {
        @Override public boolean remapScissor(int x, int y, int w, int h, int[] out) { return false; }
        @Override public boolean remapWorldPassViewport(int x, int y, int w, int h, int[] out) { return false; }
        @Override public int stereoMouseGetX() { return Mouse.getX(); }
        @Override public int stereoMouseGetY() { return Mouse.getY(); }
        @Override public int stereoMouseGetEventX() { return Mouse.getEventX(); }
        @Override public int stereoMouseGetEventY() { return Mouse.getEventY(); }
    };
}
