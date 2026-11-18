package com.gtnewhorizons.angelica.sdlgpu.device;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.sdl.SDLGPU.SDL_AcquireGPUCommandBuffer;
import static org.lwjgl.sdl.SDLGPU.SDL_QueryGPUFence;
import static org.lwjgl.sdl.SDLGPU.SDL_ReleaseGPUFence;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUFences;

final class FenceProbe {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private FenceProbe() {}

    static boolean queryInverted(long dev, String driverName, boolean onFailure) {
        final long cb = SDL_AcquireGPUCommandBuffer(dev);
        if (cb == 0) {
            LOG.warn("Fence probe: SDL_AcquireGPUCommandBuffer failed: {}; using the SDL version table ({})", SDLError.SDL_GetError(), onFailure);
            return onFailure;
        }
        final long fence = Submits.submitAndAcquireFence(cb);
        if (fence == 0) {
            LOG.warn("Fence probe: submit and acquire fence failed: {}; using the SDL version table ({})", SDLError.SDL_GetError(), onFailure);
            return onFailure;
        }
        final boolean waitOk;
        final boolean rawSignaled;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            waitOk = SDL_WaitForGPUFences(dev, true, stack.pointers(fence));
            rawSignaled = SDL_QueryGPUFence(dev, fence);
        } finally {
            SDL_ReleaseGPUFence(dev, fence);
        }
        final boolean metal = Device.isMetal(driverName);
        if (waitOk && !rawSignaled && !metal) {
            LOG.error("SDL_QueryGPUFence reports a waited fence as busy on driver {}; not inverting the query outside Metal", driverName);
        }
        return metal && waitOk && !rawSignaled;
    }
}
