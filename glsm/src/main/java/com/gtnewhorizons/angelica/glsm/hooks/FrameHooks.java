package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class FrameHooks {

    private FrameHooks() {}

    private static boolean gameLoopStarted;
    private static long frameCounter;
    private static boolean frameGroupActive;

    public static void bootstrapFirstFrame() {
        DisplayListManager.abortIfLeaked();
        if (!gameLoopStarted) {
            gameLoopStarted = true;
            RENDER_BACKEND.onFrameBegin();
            pushFrameGroup();
        }
    }

    public static void frameEnd() {
        popFrameGroup();
        RENDER_BACKEND.onFrameEnd();
    }

    public static void frameBegin() {
        if (gameLoopStarted) {
            RENDER_BACKEND.onFrameBegin();
            pushFrameGroup();
        }
    }

    public static void shutdown() {
        BackendManager.shutdown();
    }

    public static void bindSplashVao() {
        GLStateManager.glBindVertexArray(GLStateManager.glGenVertexArrays());
    }

    public static void splashFinished() {
        GLStateManager.markSplashComplete("SplashProgress.finish");
    }

    private static void pushFrameGroup() {
        CaptureGate.refresh();
        GLDebug.pushGroup("frame:", frameCounter);
        frameGroupActive = true;
    }

    private static void popFrameGroup() {
        if (frameGroupActive) {
            GLDebug.popGroup();
            frameGroupActive = false;
            frameCounter++;
        }
    }
}
