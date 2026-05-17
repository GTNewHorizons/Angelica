package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import lombok.Getter;

public class StereoState {

    public enum Eye { LEFT, RIGHT, MONO }

    public static final StereoState INSTANCE = new StereoState();

    private Eye currentEye = Eye.MONO;
    private boolean active = false;

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

    public float getEyeOffset() {
        if (!isActive()) return 0f;
        float ipd = AngelicaConfig.stereoIpd > 0f ? AngelicaConfig.stereoIpd : frameIpd;
        float half = ipd * 0.5f;
        switch (getCurrentEye()) {
            case LEFT:  return -half; // matches vanilla anaglyph: left eye = -0.1, right eye = +0.1
            case RIGHT: return  half;
            default:    return 0f;
        }
    }

    /** Hand offset scaled proportionally to configured IPD; vanilla anaglyph uses 0.1f at the 0.064 "1.0x" reference IPD. */
    public float getHandEyeOffset() {
        if (!isActive()) return 0f;
        float ipd = AngelicaConfig.stereoIpd > 0f ? AngelicaConfig.stereoIpd : frameIpd;
        float scale = ipd / 0.064f;
        float base = 0.1f * scale;
        switch (getCurrentEye()) {
            case LEFT:  return -base;
            case RIGHT: return  base;
            default:    return 0f;
        }
    }

    public boolean isLeftEye()  { return isActive() && getCurrentEye() == Eye.LEFT; }
    public boolean isRightEye() { return isActive() && getCurrentEye() == Eye.RIGHT; }
}
