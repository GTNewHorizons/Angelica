package com.gtnewhorizons.angelica.sdlgpu.splash;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.frame.OffscreenTarget;

public final class SplashDispatcher {

    private static volatile boolean frameReady;
    private static volatile long drawnSize;
    private static long lastBlitNanos;
    private static final long MIN_INTERVAL_NS = 16_000_000L;

    private SplashDispatcher() {}

    public static void seedDrawnSize(int w, int h) {
        drawnSize = pack(w, h);
    }

    public static void signalFrameReady(int w, int h) {
        drawnSize = pack(w, h);
        frameReady = true;
    }

    public static void signalFrameReady() {
        frameReady = true;
    }

    public static long getDrawnSize() {
        return drawnSize;
    }

    private static long pack(int w, int h) {
        return (long) w << 32 | (h & 0xffffffffL);
    }

    public static void tryDispatch(SDLGPURenderBackend backend, OffscreenTarget target, boolean force) {
        if (!frameReady || target == null) return;
        final long now = System.nanoTime();
        if (!force && now - lastBlitNanos < MIN_INTERVAL_NS) return;
        lastBlitNanos = now;
        frameReady = false;
        backend.dispatchSplashBlit(target);
    }

    public static void reset() {
        frameReady = false;
        drawnSize = 0;
        lastBlitNanos = 0;
    }
}
