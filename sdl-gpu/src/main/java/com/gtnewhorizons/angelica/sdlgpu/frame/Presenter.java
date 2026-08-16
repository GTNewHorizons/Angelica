package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public final class Presenter {

    private final FrameManager frameManager;
    private final Executor windowThreadExecutor;
    private final Semaphore pending = new Semaphore(1);

    public Presenter(FrameManager frameManager, Executor windowThreadExecutor) {
        this.frameManager = frameManager;
        this.windowThreadExecutor = windowThreadExecutor;
    }

    public boolean isEngaged() {
        return windowThreadExecutor != null && GLStateManager.isSplashComplete();
    }

    public void requestPresent(long srcTexture, int srcW, int srcH) {
        pending.acquireUninterruptibly();
        boolean submitted = false;
        try {
            windowThreadExecutor.execute(() -> {
                try {
                    frameManager.presentOnWindowThread(srcTexture, srcW, srcH);
                } finally {
                    pending.release();
                }
            });
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
