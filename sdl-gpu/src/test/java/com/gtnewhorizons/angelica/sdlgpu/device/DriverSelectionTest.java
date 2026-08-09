package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.device.Device.resolveDriverName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DriverSelectionTest {

    @Test
    void windowsWithoutUserHintForcesVulkan() {
        assertEquals("vulkan", resolveDriverName("", true));
    }

    @Test
    void userHintWinsOnWindows() {
        assertNull(resolveDriverName("direct3d12", true));
        assertNull(resolveDriverName("vulkan", true));
    }

    @Test
    void nonWindowsIsUntouched() {
        assertNull(resolveDriverName("", false));
        assertNull(resolveDriverName("direct3d12", false));
    }
}
