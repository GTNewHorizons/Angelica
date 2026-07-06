package com.gtnewhorizons.angelica.glsm.hooks;

public interface DeferredDepthColorHandler {
    boolean isDepthColorLocked();
    void deferDepthEnable(boolean enabled);
    void deferColorMask(boolean r, boolean g, boolean b, boolean a);
}
