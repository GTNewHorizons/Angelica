package com.gtnewhorizons.angelica.sdlgpu.frame;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public final class Presenter {

    private final FrameManager frameManager;
    private final Executor windowThreadExecutor;
    private final Semaphore pending = new Semaphore(1);
    private final Runnable task;

    private long srcTexture;
    private int srcW;
    private int srcH;
    private int flipMode;

    public Presenter(FrameManager frameManager, Executor windowThreadExecutor) {
        this.frameManager = frameManager;
        this.windowThreadExecutor = windowThreadExecutor;
        this.task = () -> {
            try {
                frameManager.presentOnWindowThread(srcTexture, srcW, srcH, flipMode);
            } finally {
                pending.release();
            }
        };
    }

    public void requestPresent(long srcTexture, int srcW, int srcH, int flipMode) {
        pending.acquireUninterruptibly();
        this.srcTexture = srcTexture;
        this.srcW = srcW;
        this.srcH = srcH;
        this.flipMode = flipMode;
        boolean submitted = false;
        try {
            windowThreadExecutor.execute(task);
            submitted = true;
        } finally {
            if (!submitted) pending.release();
        }
    }

    public void drain() {
        pending.acquireUninterruptibly();
        pending.release();
    }
}
