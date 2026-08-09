package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

final class SplashWindow {

    private SplashWindow() {}

    static boolean isSplashComplete() {
        return Reflect.getStatic(GLStateManager.class, "splashComplete");
    }

    static void setSplashComplete(boolean value) {
        Reflect.setStatic(GLStateManager.class, "splashComplete", value);
    }

    static void enter() {
        setSplashComplete(false);
        GLStateManager.setDrawableGLHolder(new Thread("fake-splash-renderer"));
    }

    static void leave() {
        GLStateManager.setDrawableGLHolder(Thread.currentThread());
        setSplashComplete(true);
    }
}
