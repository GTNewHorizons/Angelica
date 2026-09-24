package com.gtnewhorizons.angelica.glsm.hooks;

public interface DeferredAlphaHandler {
    boolean isAlphaTestLocked();
    boolean deferAlphaTestToggle(boolean enabled);
    boolean deferAlphaFunc(int function, float reference);
}
