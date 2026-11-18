package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.recording.CommandRecorder;

/** Drains deferred geometry before changing state that is not stored with each batch. */
public final class BatchStateGuard {
    public static Runnable flush;
    private static int suspended;

    private BatchStateGuard() {}

    public static void suspend() { suspended++; }
    public static void resume() { suspended--; }
    public static boolean isSuspended() { return suspended != 0; }

    public static void beforeChange() {
        if (flush == null || suspended != 0 || !GLStateManager.isMainThread()
            || DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.COMPILE) return;
        final CommandRecorder recorder = DisplayListManager.isRecording() ? DisplayListManager.pauseRecording() : null;
        suspend();
        try {
            flush.run();
        } finally {
            resume();
            if (recorder != null) DisplayListManager.resumeRecording(recorder);
        }
    }
}
