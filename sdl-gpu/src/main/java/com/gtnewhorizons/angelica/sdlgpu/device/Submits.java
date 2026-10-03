package com.gtnewhorizons.angelica.sdlgpu.device;

import static org.lwjgl.sdl.SDLGPU.SDL_SubmitGPUCommandBuffer;
import static org.lwjgl.sdl.SDLGPU.SDL_SubmitGPUCommandBufferAndAcquireFence;

public final class Submits {
    private static final Object LOCK = new Object();

    private Submits() {}

    public static boolean submit(long commandBuffer) {
        synchronized (LOCK) {
            return SDL_SubmitGPUCommandBuffer(commandBuffer);
        }
    }

    public static long submitAndAcquireFence(long commandBuffer) {
        synchronized (LOCK) {
            return SDL_SubmitGPUCommandBufferAndAcquireFence(commandBuffer);
        }
    }
}
