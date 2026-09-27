package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

import java.util.Arrays;
import java.util.Map;

final class DrawableContexts {

    private DrawableContexts() {}

    static void forget(Object drawable) {
        final Map<Object, GLContextState> contexts = Reflect.getStatic(GLStateManager.class, "drawableContexts");
        final GLContextState ctx = contexts.remove(drawable);
        if (ctx == null) return;
        final GLContextState[] states = Reflect.getStatic(GLStateManager.class, "drawableContextStates");
        Reflect.setStatic(GLStateManager.class, "drawableContextStates", Arrays.stream(states).filter(s -> s != ctx).toArray(GLContextState[]::new));
    }
}
