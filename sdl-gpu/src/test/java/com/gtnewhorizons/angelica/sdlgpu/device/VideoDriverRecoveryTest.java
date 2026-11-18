package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.sdlgpu.device.VideoDriverRecovery.shouldRetryOnX11;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoDriverRecoveryTest {

    static Stream<Arguments> sessions() {
        return Stream.of(
            Arguments.of("wayland without a Vulkan loader, XWayland present", "wayland", false, ":0", true),
            Arguments.of("wayland with a Vulkan loader", "wayland", true, ":0", false),
            Arguments.of("wayland, DISPLAY unset", "wayland", false, null, false),
            Arguments.of("wayland, DISPLAY empty", "wayland", false, "", false),
            Arguments.of("x11", "x11", false, ":0", false),
            Arguments.of("cocoa", "cocoa", false, ":0", false),
            Arguments.of("windows", "windows", false, ":0", false),
            Arguments.of("no video driver", null, false, ":0", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void retriesOnX11OnlyForWaylandWithoutVulkan(String name, String videoDriver, boolean vulkanLoaderAvailable, String display, boolean retry) {
        assertEquals(retry, shouldRetryOnX11(videoDriver, vulkanLoaderAvailable, display));
    }
}
