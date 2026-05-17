package com.gtnewhorizons.angelica.stereo;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import lombok.Getter;

public class StereoState {

    public enum Eye { LEFT, RIGHT, MONO }

    public static final StereoState INSTANCE = new StereoState();

    private Eye currentEye = Eye.MONO;
    private boolean active = false;

    // While inGuiPass is true, GLStateManager.glScissor remaps caller scissor coords (assumed to
    // be "framebuffer pixels with GUI filling the whole screen") into the current eye viewport.
    // Set by MixinEntityRenderer_Stereo around each drawScreen / renderGameOverlay / Post-event
    // eye pass.
    private boolean inGuiPass = false;

    // While true, GLStateManager.glViewport intercepts attempts to set the viewport to the
    // full main framebuffer dimensions (which is what Iris's Pass.use() and CompositeRenderer
    // do at every shader phase change) and remaps them to the current eye viewport instead.
    // Set by MixinEntityRenderer_Stereo around each renderWorld eye pass.
    private boolean inWorldPass = false;

    private int eyeVpX = 0;
    private int eyeVpY = 0;
    private int eyeVpW = 0;
    private int eyeVpH = 0;

    public void enterGuiPass(int x, int y, int w, int h) {
        inGuiPass = true;
        eyeVpX = x;
        eyeVpY = y;
        eyeVpW = w;
        eyeVpH = h;
    }

    public void exitGuiPass() { inGuiPass = false; }
    public boolean isInGuiPass() { return inGuiPass; }

    public void enterWorldPass(int x, int y, int w, int h) {
        inWorldPass = true;
        eyeVpX = x;
        eyeVpY = y;
        eyeVpW = w;
        eyeVpH = h;
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
        // Intentionally do NOT reset frameMode/frameIpd/frameHudMode here. RenderTickEvent.END
        // fires from FMLCommonHandler.onRenderTickEnd *after* updateCameraAndRender returns, and
        // MixinFMLCommonHandler_Stereo needs the frame's stereo config still readable so it can
        // duplicate the event per-eye. beginFrame() overwrites these on the next frame.
    }

    public void setEye(Eye eye) {
        this.currentEye = eye;
    }

    // Signs are flipped relative to vanilla's anaglyph convention (EntityRenderer.setupCameraTransform
    // uses -0.1 for LEFT, +0.1 for RIGHT). Vanilla renders each eye from the OPPOSITE camera
    // position, which works for red/cyan anaglyph because the brain doesn't see real per-eye
    // images, but is backwards for SBS VR where each eye directly sees its half of the screen.
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

    /** Hand-specific offset, currently not applied (HandRenderer uses getEyeOffset instead for VR comfort). */
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

    /** Stable integer index for the current eye. LEFT/MONO map to 0, RIGHT maps to 1. */
    public int currentEyeIndex() {
        return getCurrentEye() == Eye.RIGHT ? 1 : 0;
    }

    /** 2 when stereo active for current frame, otherwise 1. Used by Iris RenderTargets to decide
     *  whether to duplicate per-eye resources. */
    public int stereoEyeCount() {
        return isActive() ? 2 : 1;
    }

    // Iris-facing helpers: return the FB dimensions Iris should use for sizing render targets,
    // compute dispatch, and the viewWidth/viewHeight uniforms. ALWAYS return the full display
    // size even in SBS stereo — each eye renders at native aspect into its own per-eye FBO
    // chain, and the final per-eye blit squishes horizontally into that eye's main-FB region.
    // Halving width for SBS_HALF causes Complementary Reimagined's bloom/SSAO tile layout
    // (max(vec2(viewWidth,viewHeight)/vec2(1920,1080), 1.0)) to clamp past texture edges and
    // produce dark patchwork artifacts. Cost: each eye's FBO is full display size.
    public int irisFbWidth(int actualWidth) {
        return actualWidth;
    }

    public int irisFbHeight(int actualHeight) {
        return actualHeight;
    }
}
