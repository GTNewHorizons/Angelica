package com.gtnewhorizons.angelica.glsm.hooks;

public interface DeferredBlendHandler {
    boolean isBlendLocked();
    boolean isOverrideHeld();
    void deferBlendModeToggle(boolean enabled);
    void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    void flushDeferredBlend();
}
