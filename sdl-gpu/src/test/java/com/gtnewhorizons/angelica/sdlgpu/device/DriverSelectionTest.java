package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.device.Device.metalNeedsNewerSdl;
import static com.gtnewhorizons.angelica.sdlgpu.device.Device.resolveDriverName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLVersion.SDL_VERSIONNUM;

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

    @Test
    void metalBelow346FallsBack() {
        assertTrue(metalNeedsNewerSdl("metal", SDL_VERSIONNUM(3, 4, 5)));
        assertFalse(metalNeedsNewerSdl("metal", SDL_VERSIONNUM(3, 4, 6)));
        assertFalse(metalNeedsNewerSdl("metal", SDL_VERSIONNUM(3, 5, 0)));
    }

    @Test
    void otherDriversIgnoreTheMetalFloor() {
        assertFalse(metalNeedsNewerSdl("vulkan", SDL_VERSIONNUM(3, 4, 5)));
        assertFalse(metalNeedsNewerSdl("direct3d12", SDL_VERSIONNUM(3, 0, 0)));
    }
}
