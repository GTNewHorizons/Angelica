package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.device.VideoDriverRecovery.shouldRetryOnX11;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoDriverRecoveryTest {

    @Test
    void waylandWithoutVulkanSurfaceRetries() {
        assertTrue(shouldRetryOnX11("wayland", false, ":0"));
    }

    @Test
    void waylandWithVulkanStays() {
        assertFalse(shouldRetryOnX11("wayland", true, ":0"));
    }

    @Test
    void noXWaylandMeansNoRetry() {
        assertFalse(shouldRetryOnX11("wayland", false, null));
        assertFalse(shouldRetryOnX11("wayland", false, ""));
    }

    @Test
    void otherVideoDriversAreLeftAlone() {
        assertFalse(shouldRetryOnX11("x11", false, ":0"));
        assertFalse(shouldRetryOnX11("cocoa", false, ":0"));
        assertFalse(shouldRetryOnX11("windows", false, ":0"));
        assertFalse(shouldRetryOnX11(null, false, ":0"));
    }
}
