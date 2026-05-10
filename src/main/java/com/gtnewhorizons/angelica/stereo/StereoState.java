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

    @Getter private Eye currentEye = Eye.MONO;
    @Getter private boolean active = false;

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
        frameMode = StereoMode.OFF;
    }

    public void setEye(Eye eye) {
        this.currentEye = eye;
    }

    /**
     * Returns the horizontal eye-space offset to apply to the modelview matrix for the current eye.
     * Positive = camera moves right, world appears to shift left.
     *
     * <p>For LEFT eye: returns +ipd/2 (camera moves left, but in eye-space coordinates after the
     * view rotation, a camera shift left is a +X translation of the world relative to camera).
     * For RIGHT eye: returns -ipd/2. For MONO: returns 0.</p>
     *
     * <p>Sign convention here matches the vanilla anaglyph code in
     * {@code EntityRenderer.setupCameraTransform}, which translates the modelview by
     * {@code (anaglyphField * 2 - 1) * 0.1f} — i.e. left eye (field=0) gets {@code -0.1f}
     * which is a leftward camera shift expressed as a +X world shift. We follow the same convention.</p>
     */
    public float getEyeOffset() {
        if (!active) return 0f;
        float half = frameIpd * 0.5f;
        switch (currentEye) {
            case LEFT:  return  half; // world shifts right-of-camera → camera moved left
            case RIGHT: return -half;
            default:    return 0f;
        }
    }

    /** Convenience: matches the legacy 0.1f anaglyph value, scaled by configured IPD ratio. */
    public float getHandEyeOffset() {
        if (!active) return 0f;
        // Hand uses 0.1f modelview offset and 0.07f projection offset in vanilla anaglyph.
        // We scale proportionally to configured IPD, taking 0.064 as the "1.0x" reference.
        float scale = frameIpd / 0.064f;
        float base = 0.1f * scale;
        switch (currentEye) {
            case LEFT:  return  base;
            case RIGHT: return -base;
            default:    return 0f;
        }
    }

    public boolean isLeftEye()  { return active && currentEye == Eye.LEFT; }
    public boolean isRightEye() { return active && currentEye == Eye.RIGHT; }
}
