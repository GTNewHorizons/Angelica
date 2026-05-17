package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import lombok.Getter;

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
        currentEye = Eye.MONO;
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
     * Horizontal eye-space modelview offset for the current eye. Sign convention matches the
     * vanilla anaglyph path in {@code EntityRenderer.setupCameraTransform}: LEFT eye returns
     * {@code +ipd/2} (camera shifted left, expressed as a +X world translation relative to camera).
     */
    public float getEyeOffset() {
        if (!active) return 0f;
        float half = frameIpd * 0.5f;
        switch (currentEye) {
            case LEFT:  return  half;
            case RIGHT: return -half;
            default:    return 0f;
        }
    }

    /** Hand offset scaled proportionally to configured IPD, taking 0.064 as the "1.0x" reference (vanilla anaglyph uses 0.1f at that IPD). */
    public float getHandEyeOffset() {
        if (!active) return 0f;
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
