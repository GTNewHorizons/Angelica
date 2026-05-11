package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import lombok.Getter;

/**
 * Per-frame stereoscopic rendering state. Mirrors the shape of
 * {@link com.gtnewhorizons.angelica.rendering.RenderingState} but tracks which eye
 * we are currently rendering and the active stereo configuration.
 *
 * <p>Lifecycle within a single frame, when {@link StereoMode#isActive()}:</p>
 * <ol>
 *   <li>{@link #beginFrame()} — at top of {@code updateCameraAndRender}</li>
 *   <li>{@link #setEye(Eye)} = LEFT, viewport set, {@code renderWorld} runs</li>
 *   <li>{@link #setEye(Eye)} = RIGHT, viewport set, {@code renderWorld} runs</li>
 *   <li>HUD render (possibly twice, depending on {@link StereoHudMode})</li>
 *   <li>{@link #endFrame()}</li>
 * </ol>
 *
 * <p>When {@link StereoMode#OFF}, the singleton stays in MONO and {@link #isActive()}
 * returns false; all hooks become no-ops.</p>
 */
public class StereoState {

    public enum Eye { LEFT, RIGHT, MONO }

    public static final StereoState INSTANCE = new StereoState();

    private Eye currentEye = Eye.MONO;
    private boolean active = false;

    // While true, GLStateManager.glScissor remaps caller scissor coords (assumed to be in
    // "framebuffer pixels with GUI filling the whole screen") into the current eye viewport.
    // Set by MixinEntityRenderer_Stereo around each drawScreen / renderGameOverlay / Post-event
    // eye pass. The four eyeVp* fields hold that pass's viewport bounds.
    private boolean inGuiPass = false;

    // While true, GLStateManager.glViewport intercepts attempts to set the viewport to the
    // full main framebuffer dimensions (which is what Iris's Pass.use() and CompositeRenderer
    // do at every shader phase change) and remaps them to the current eye viewport instead.
    // Set by MixinEntityRenderer_Stereo around each renderWorld eye pass.
    private boolean inWorldPass = false;

    private int eyeVpX = 0, eyeVpY = 0, eyeVpW = 0, eyeVpH = 0;

    public void enterGuiPass(int x, int y, int w, int h) {
        inGuiPass = true;
        eyeVpX = x; eyeVpY = y; eyeVpW = w; eyeVpH = h;
    }

    public void exitGuiPass() { inGuiPass = false; }
    public boolean isInGuiPass() { return inGuiPass; }

    public void enterWorldPass(int x, int y, int w, int h) {
        inWorldPass = true;
        eyeVpX = x; eyeVpY = y; eyeVpW = w; eyeVpH = h;
    }

    public void exitWorldPass() { inWorldPass = false; }
    public boolean isInWorldPass() { return inWorldPass; }

    public int getEyeVpX() { return eyeVpX; }
    public int getEyeVpY() { return eyeVpY; }
    public int getEyeVpW() { return eyeVpW; }
    public int getEyeVpH() { return eyeVpH; }

    public Eye getCurrentEye() {
        StereoDebugEye debug = AngelicaConfig.stereoDebugForceEye;
        if (debug != null && debug != StereoDebugEye.OFF) {
            return debug == StereoDebugEye.LEFT ? Eye.LEFT : Eye.RIGHT;
        }
        return currentEye;
    }

    public boolean isActive() {
        StereoDebugEye debug = AngelicaConfig.stereoDebugForceEye;
        if (debug != null && debug != StereoDebugEye.OFF) {
            return true;
        }
        return active;
    }

    /** Cached at frame start so config flips mid-frame don't cause inconsistency. */
    @Getter private StereoMode frameMode = StereoMode.OFF;
    @Getter private float frameIpd = 0.064f;
    @Getter private StereoHudMode frameHudMode = StereoHudMode.DUPLICATE;

    private StereoState() {}

    /**
     * Snapshot config and mark the frame as active. Called once per frame at the top of
     * {@code EntityRenderer.updateCameraAndRender}.
     *
     * @return true if stereo is active for this frame.
     */
    public boolean beginFrame() {
        StereoMode mode = AngelicaConfig.stereoscopicMode;
        if (mode == null || !mode.isActive()) {
            active = false;
            currentEye = Eye.MONO;
            frameMode = StereoMode.OFF;
            return false;
        }
        active = true;
        frameMode = mode;
        frameIpd = AngelicaConfig.stereoIpd;
        frameHudMode = AngelicaConfig.stereoHudMode != null
            ? AngelicaConfig.stereoHudMode
            : StereoHudMode.DUPLICATE;
        currentEye = Eye.MONO; // until setEye is called for the first pass
        return true;
    }

    public void endFrame() {
        active = false;
        currentEye = Eye.MONO;
        // Intentionally do NOT reset frameMode / frameIpd / frameHudMode here. RenderTickEvent.END
        // fires from FMLCommonHandler.onRenderTickEnd *after* updateCameraAndRender returns, and
        // our MixinFMLCommonHandler_Stereo redirect needs the frame's stereo config to still be
        // readable so it can duplicate the event per-eye. beginFrame() overwrites these values
        // on the next frame, so leaving them alive between frames is safe.
    }

    public void setEye(Eye eye) {
        this.currentEye = eye;
    }

    /**
     * Returns the horizontal eye-space offset to apply to the modelview matrix for the current eye.
     *
     * <p>For LEFT eye: returns +ipd/2. Geometrically: the LEFT eye sits to the LEFT of player
     * center in world space. After the modelview transforms world → eye coordinates, the world
     * appears shifted RIGHTWARD relative to the LEFT-shifted camera — so we translate the
     * modelview by +ipd/2 in X to put the world at the correct eye-space position.</p>
     *
     * <p>For RIGHT eye: returns -ipd/2 by symmetry.</p>
     *
     * <p>(The vanilla anaglyph code in {@code EntityRenderer.setupCameraTransform} uses the
     * OPPOSITE signs — left eye gets -0.1, right eye gets +0.1. That convention renders each
     * eye's view from the *opposite* camera position, which works for red/cyan anaglyph because
     * the brain doesn't see actual per-eye images, but is backwards for SBS VR where each eye
     * directly sees its half of the screen. Using the vanilla signs feels like eye-swapped
     * stereo to a VR viewer.)</p>
     */
    public float getEyeOffset() {
        if (!isActive()) return 0f;
        float ipd = AngelicaConfig.stereoIpd > 0f ? AngelicaConfig.stereoIpd : frameIpd;
        float half = ipd * 0.5f;
        switch (getCurrentEye()) {
            case LEFT:  return  half;
            case RIGHT: return -half;
            default:    return 0f;
        }
    }

    /**
     * Convenience: hand-specific offset, currently not applied (HandRenderer zeroes it out for
     * VR comfort — see HandRenderer.java). Signs follow {@link #getEyeOffset()} so re-enabling
     * this won't produce eye-swapped stereo.
     */
    public float getHandEyeOffset() {
        if (!isActive()) return 0f;
        float ipd = AngelicaConfig.stereoIpd > 0f ? AngelicaConfig.stereoIpd : frameIpd;
        float scale = ipd / 0.064f;
        float base = 0.1f * scale;
        switch (getCurrentEye()) {
            case LEFT:  return  base;
            case RIGHT: return -base;
            default:    return 0f;
        }
    }

    public boolean isLeftEye()  { return isActive() && getCurrentEye() == Eye.LEFT; }
    public boolean isRightEye() { return isActive() && getCurrentEye() == Eye.RIGHT; }

    /**
     * Returns a stable integer index for the current eye, suitable for indexing per-eye arrays
     * (e.g. {@code RenderTargets}'s per-eye color/depth state). LEFT/MONO map to 0, RIGHT maps to 1.
     */
    public int currentEyeIndex() {
        return getCurrentEye() == Eye.RIGHT ? 1 : 0;
    }

    /**
     * Returns the number of eyes the pipeline should allocate per-eye resources for: 2 when stereo
     * is active for the current frame, otherwise 1. Used by Iris's RenderTargets to decide whether
     * to duplicate its color and depth-stencil textures so each eye gets its own bubble (avoids
     * cross-eye contamination in kernel-based composite shaders like bloom).
     */
    public int stereoEyeCount() {
        return isActive() ? 2 : 1;
    }

    /**
     * Iris-facing helper: returns the framebuffer width that Iris should treat as the "main"
     * framebuffer width when sizing its render targets, dispatching compute, and reporting the
     * {@code viewWidth} uniform. <strong>Always returns the full display width</strong> even in
     * SBS stereo — each eye renders at native aspect into its own per-eye FBO chain, and the
     * final per-eye blit squishes the result horizontally into that eye's main-FB region.
     *
     * <p>Why not halve for SBS_HALF? Many composite shaders (Complementary Reimagined's bloom,
     * SSAO, etc.) lay tiles out in {@code colortexN} based on
     * {@code max(vec2(viewWidth, viewHeight) / vec2(1920, 1080), 1.0)}. A non-square ratio
     * (e.g. {@code (1.0, 2.0)} on 4K SBS_HALF) causes tile reads to clamp past the texture edge
     * and produce the dark patchwork artifacts we saw. Keeping the per-eye render at the
     * monoscopic aspect avoids the issue entirely. Cost: each eye's FBO is the full display
     * size — ~2× the fragment work per frame vs a half-width per-eye FBO.</p>
     */
    public int irisFbWidth(int actualWidth) {
        return actualWidth;
    }

    /**
     * Iris-facing helper for height. See {@link #irisFbWidth(int)} — returns the actual height
     * for monoscopic and stereo modes alike.
     */
    public int irisFbHeight(int actualHeight) {
        return actualHeight;
    }
}
