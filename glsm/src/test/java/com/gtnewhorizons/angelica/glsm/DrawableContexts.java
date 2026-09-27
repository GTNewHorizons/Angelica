package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.testutil.TestThreads;
import org.lwjgl.opengl.SharedDrawable;

import java.util.Arrays;
import java.util.Map;

final class DrawableContexts {

    private DrawableContexts() {}

    static TestThreads.Worker start(SharedDrawable drawable, String name, TestThreads.Body body) {
        return TestThreads.start(name, () -> {
            GLStateManager.makeCurrent(drawable);
            try {
                body.run();
            } finally {
                GLStateManager.releaseContext(drawable);
            }
        });
    }

    static void run(SharedDrawable drawable, String name, TestThreads.Body body) throws InterruptedException {
        start(drawable, name, body).join();
    }

    static void destroy(SharedDrawable drawable) {
        final Map<Object, GLContextState> contexts = Reflect.getStatic(GLStateManager.class, "drawableContexts");
        final GLContextState ctx = contexts.remove(drawable);
        if (ctx != null) {
            final GLContextState[] states = Reflect.getStatic(GLStateManager.class, "drawableContextStates");
            Reflect.setStatic(GLStateManager.class, "drawableContextStates", Arrays.stream(states).filter(s -> s != ctx).toArray(GLContextState[]::new));
        }
        drawable.destroy();
    }
}
