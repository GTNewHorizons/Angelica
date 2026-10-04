package com.gtnewhorizons.angelica.sdlgpu.device;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.Platform;

import static com.gtnewhorizons.angelica.sdlgpu.device.Device.driverCandidates;
import static com.gtnewhorizons.angelica.sdlgpu.device.Device.metalNeedsNewerSdl;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLVersion.SDL_VERSIONNUM;

class DriverSelectionTest {

    @Test
    void autoUsesThePlatformOrder() {
        assertArrayEquals(new String[] { "metal", "vulkan" }, driverCandidates("", Platform.MACOSX));
        assertArrayEquals(new String[] { "vulkan", "direct3d12" }, driverCandidates("", Platform.WINDOWS));
        assertArrayEquals(new String[] { "vulkan" }, driverCandidates("", Platform.LINUX));
    }

    @Test
    void requestedDriverGoesFirstWithoutDuplicates() {
        assertArrayEquals(new String[] { "vulkan", "metal" }, driverCandidates("vulkan", Platform.MACOSX));
        assertArrayEquals(new String[] { "direct3d12", "vulkan" }, driverCandidates("direct3d12", Platform.WINDOWS));
        assertArrayEquals(new String[] { "vulkan" }, driverCandidates("vulkan", Platform.LINUX));
        assertArrayEquals(new String[] { "Vulkan", "direct3d12" }, driverCandidates("Vulkan", Platform.WINDOWS));
    }

    @Test
    void unsupportedDriverStillFallsBack() {
        assertArrayEquals(new String[] { "metal", "vulkan", "direct3d12" }, driverCandidates("metal", Platform.WINDOWS));
        assertArrayEquals(new String[] { "bogus", "vulkan" }, driverCandidates("bogus", Platform.LINUX));
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
