package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;

/** Drains deferred geometry before changing state that is not stored with each batch. */
public final class BatchStateGuard {
    public static Runnable flush;
    public static Runnable drawFlush;
    private static int suspended;

    private BatchStateGuard() {}

    public static void suspend() { suspended++; }
    public static void resume() { suspended--; }
    public static boolean isSuspended() { return suspended != 0; }

    public static void beforeChange() {
        run(flush);
    }

    public static void beforeDraw() {
        run(drawFlush);
    }

    private static void run(Runnable action) {
        if (action == null || suspended != 0 || !GLStateManager.isMainThread()
            || DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.COMPILE) return;
        final CommandRecorder recorder = DisplayListManager.isRecording() ? DisplayListManager.pauseRecording() : null;
        suspend();
        try {
            action.run();
        } finally {
            resume();
            if (recorder != null) DisplayListManager.resumeRecording(recorder);
        }
    }
}
