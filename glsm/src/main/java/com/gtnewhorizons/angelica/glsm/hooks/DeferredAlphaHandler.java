package com.gtnewhorizons.angelica.glsm.hooks;

public interface DeferredAlphaHandler {
    boolean isAlphaTestLocked();
    void deferAlphaTestToggle(boolean enabled);
    void deferAlphaFunc(int function, float reference);
}
