package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLSMTextureLifetime;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class FrameHooks {

    private FrameHooks() {}

    private static boolean gameLoopStarted;
    private static long frameCounter;
    private static boolean frameGroupActive;
    private static String framePrefix = "frame:";
    private static String buildLabel = "";

    public static void bootstrapFirstFrame() {
        DisplayListManager.abortIfLeaked();
        if (!gameLoopStarted) {
            gameLoopStarted = true;
            final GLSMInitConfig config = GLStateManager.getInitConfig();
            if (config != null && !config.getBuildLabel().isEmpty()) {
                buildLabel = config.getBuildLabel();
                framePrefix = buildLabel + " frame:";
            }
            RENDER_BACKEND.onFrameBegin();
            pushFrameGroup();
        }
    }

    public static void frameEnd() {
        GLSMTextureLifetime.onFrameEnd();
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

    public static void splashFinished() {
        GLStateManager.markSplashComplete("SplashProgress.finish");
    }

    private static void pushFrameGroup() {
        final boolean wasConnected = CaptureGate.tracyConnected;
        CaptureGate.refresh();
        if (CaptureGate.tracyConnected && !wasConnected && !buildLabel.isEmpty()) Tracy.message(buildLabel);
        GLDebug.pushGroup(framePrefix, frameCounter);
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
