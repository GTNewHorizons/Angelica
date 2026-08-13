package com.gtnewhorizons.angelica.sdlgpu;

import com.mitchej123.lwjgl.LWJGLService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SDLGPULWJGLServiceGatingTest {

    @Test
    void anUnengagedSdlDeclinesAndNeverArmsTheFallbackTripwire() {
        assertFalse(SDLGPUGate.isEngaged(), "test JVM must not have an engaged SDL GPU device");
        assertEquals(LWJGLService.PRIORITY_UNAVAILABLE, new SDLGPULWJGLService().getPriority());
        assertFalse(SDLGPULWJGLService.isOfferedAsAvailable(), "declining must leave fallBackToGL() usable");
    }
}
