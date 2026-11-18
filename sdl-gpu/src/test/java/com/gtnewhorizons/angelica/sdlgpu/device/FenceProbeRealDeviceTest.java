package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.sdlgpu.RealDeviceExtension;
import com.gtnewhorizons.angelica.sdlgpu.SdlTestRig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.system.MemoryStack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_AcquireGPUCommandBuffer;
import static org.lwjgl.sdl.SDLGPU.SDL_GetGPUDeviceDriver;
import static org.lwjgl.sdl.SDLGPU.SDL_ReleaseGPUFence;
import static org.lwjgl.sdl.SDLGPU.SDL_SubmitGPUCommandBufferAndAcquireFence;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUFences;
import static org.lwjgl.sdl.SDLVersion.SDL_GetVersion;
import static org.lwjgl.sdl.SDLVersion.SDL_VERSIONNUM;

@ExtendWith(RealDeviceExtension.class)
@Tag("sdl-gpu-device")
class FenceProbeRealDeviceTest {

    @Test
    void probeMatchesVersionTableAndCorrectedQueryReadsSignaled() {
        final SdlTestRig rig = RealDeviceExtension.rig();
        final String driver = SDL_GetGPUDeviceDriver(rig.sdlHandle);
        assertEquals("metal".equalsIgnoreCase(driver) && SDL_GetVersion() == SDL_VERSIONNUM(3, 4, 14), rig.device.isFenceQueryInverted(), "driver=" + driver + " sdl=" + SDL_GetVersion());

        final long cb = SDL_AcquireGPUCommandBuffer(rig.sdlHandle);
        assertNotEquals(0L, cb);
        final long fence = SDL_SubmitGPUCommandBufferAndAcquireFence(cb);
        assertNotEquals(0L, fence);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            assertTrue(SDL_WaitForGPUFences(rig.sdlHandle, true, stack.pointers(fence)));
            assertTrue(FenceWait.isSignaled(rig.device, fence));
        } finally {
            SDL_ReleaseGPUFence(rig.sdlHandle, fence);
        }
    }
}
