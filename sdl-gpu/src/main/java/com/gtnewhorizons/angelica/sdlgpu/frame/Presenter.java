package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.backend.MainThreadPump;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public final class Presenter {

    private final FrameManager frameManager;
    private final Executor windowThreadExecutor;
    private final MainThreadPump pump;
    private final Semaphore pending = new Semaphore(1);
    private final Runnable task;

    private long srcTexture;
    private int srcW;
    private int srcH;
    private int flipMode;

    public Presenter(FrameManager frameManager, Executor windowThreadExecutor, MainThreadPump pump) {
        this.frameManager = frameManager;
        this.windowThreadExecutor = windowThreadExecutor;
        this.pump = pump;
        this.task = () -> {
            try {
                pump.runPresentPump();
                frameManager.presentOnWindowThread(srcTexture, srcW, srcH, flipMode);
            } finally {
                pump.endPresent();
                pending.release();
            }
        };
    }

    public void requestPresent(long srcTexture, int srcW, int srcH, int flipMode) {
        pending.acquireUninterruptibly();
        submit(srcTexture, srcW, srcH, flipMode);
    }

    public boolean tryRequestPresent(long srcTexture, int srcW, int srcH, int flipMode) {
        if (!pending.tryAcquire()) return false;
        submit(srcTexture, srcW, srcH, flipMode);
        return true;
    }

    private void submit(long srcTexture, int srcW, int srcH, int flipMode) {
        this.srcTexture = srcTexture;
        this.srcW = srcW;
        this.srcH = srcH;
        this.flipMode = flipMode;
        pump.beginPresent();
        boolean submitted = false;
        try {
            windowThreadExecutor.execute(task);
            submitted = true;
        } finally {
            if (!submitted) {
                pump.endPresent();
                pending.release();
            }
        }
    }

    public void drain() {
        pending.acquireUninterruptibly();
        pending.release();
    }
}
