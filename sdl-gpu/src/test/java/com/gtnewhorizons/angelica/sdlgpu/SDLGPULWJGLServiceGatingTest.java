package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.config.SystemProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SDLGPULWJGLServiceGatingTest {

    @Test
    void anUnengagedSdlDeclinesAndNeverArmsTheFallbackTripwire() {
        final String key = SystemProperties.KEY_CELERITAS_LWJGL_SERVICE;
        assertFalse(SDLGPUGate.isEngaged(), "test JVM must not have an engaged SDL GPU device");
        assertNull(System.getProperty(key), "an unengaged SDL must not bind the celeritas service");

        try {
            SDLGPULWJGLService.bind();
            assertEquals(SDLGPULWJGLService.class.getName(), System.getProperty(key));

            assertThrows(
                IllegalStateException.class,
                SDLGPULWJGLService::create,
                "constructing the service while SDL GPU is unengaged must fail loudly");
        } finally {
            SDLGPULWJGLService.unbind();
        }

        assertNull(System.getProperty(key));
        assertFalse(SDLGPULWJGLService.isConstructed(), "declining must leave fallBackToGL() usable");
    }
}
