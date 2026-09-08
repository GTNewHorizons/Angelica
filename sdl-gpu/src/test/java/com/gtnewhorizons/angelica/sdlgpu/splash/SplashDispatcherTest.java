package com.gtnewhorizons.angelica.sdlgpu.splash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplashDispatcherTest {

    private static final long INTERVAL = 16_000_000L;

    @Test
    void theIntervalGateSwallowsFramesArrivingTooSoon() {
        assertFalse(SplashDispatcher.intervalElapsed(INTERVAL - 1, 0L, false));
        assertTrue(SplashDispatcher.intervalElapsed(INTERVAL, 0L, false));
    }

    @Test
    void theFinalFrameIsNotSwallowedByTheIntervalGate() {
        assertTrue(SplashDispatcher.intervalElapsed(1L, 0L, true), "teardown must present the last splash frame whenever it arrives");
    }
}
