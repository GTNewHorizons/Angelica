package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureGateTest {

    @Test
    void gateOffWhenNothingAttached() {
        assertFalse(CaptureGate.gateValue(false, false, false));
    }

    @Test
    void gateOnWhenForced() {
        assertTrue(CaptureGate.gateValue(true, false, false));
    }

    @Test
    void gateOnWhenToolAttached() {
        assertTrue(CaptureGate.gateValue(false, true, false));
    }

    @Test
    void gateOnWhenTracyConnected() {
        assertTrue(CaptureGate.gateValue(false, false, true));
    }

    @Test
    void envDetectionMatchesRenderDocVariables() {
        assertTrue(CaptureGate.detectFromEnv(k -> "RENDERDOC_CAPTURE_KEYPRESS".equals(k) ? "1" : null));
        assertTrue(CaptureGate.detectFromEnv(k -> "ENABLE_VULKAN_RENDERDOC_CAPTURE".equals(k) ? "1" : null));
        assertTrue(CaptureGate.detectFromEnv(k -> "RENDERDOC_HOOK_EGL".equals(k) ? "1" : null));
    }

    @Test
    void envDetectionIgnoresUnrelatedVariables() {
        assertFalse(CaptureGate.detectFromEnv(k -> "PATH".equals(k) ? "/usr/bin" : null));
    }

    @Test
    void mapsDetectionFindsCaptureLibraries() {
        assertTrue(CaptureGate.detectFromMaps(mapsWith("/usr/lib/librenderdoc.so")));
        assertTrue(CaptureGate.detectFromMaps(mapsWith("/usr/lib/x86_64-linux-gnu/libVkLayer_GLES_RenderDoc.so")));
        assertTrue(CaptureGate.detectFromMaps(mapsWith("/usr/lib/apitrace/wrappers/glxtrace.so")));
        assertTrue(CaptureGate.detectFromMaps(mapsWith("/usr/lib/apitrace/wrappers/egltrace.so")));
    }

    @Test
    void mapsDetectionIgnoresOrdinaryLibraries() {
        assertFalse(CaptureGate.detectFromMaps(mapsWith("/usr/lib/libvulkan.so.1")));
        assertFalse(CaptureGate.detectFromMaps(Collections.emptyList()));
    }

    private static List<String> mapsWith(String path) {
        return Arrays.asList(
            "55a1b2c00000-55a1b2c21000 r--p 00000000 fd:01 262401                     /usr/lib/jvm/java-21/bin/java",
            "7f2c1a000000-7f2c1a021000 r-xp 00000000 fd:01 262402                     " + path,
            "7ffd1a000000-7ffd1a021000 rw-p 00000000 00:00 0                          [stack]");
    }
}
