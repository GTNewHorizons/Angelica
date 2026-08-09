package com.gtnewhorizons.angelica.sdlgpu;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL15;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE;

class ImageAccessUsageMappingTest {

    @Test
    void readOnlyRequestsOnlyStorageRead() {
        assertEquals(SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ, SDLGPURenderBackend.imageUsageForAccess(GL15.GL_READ_ONLY));
    }

    @Test
    void writeOnlyRequestsOnlyStorageWrite() {
        assertEquals(SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE, SDLGPURenderBackend.imageUsageForAccess(GL15.GL_WRITE_ONLY));
    }

    @Test
    void readWriteRequestsOnlyStorageWrite() {
        assertEquals(SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE, SDLGPURenderBackend.imageUsageForAccess(GL15.GL_READ_WRITE));
    }

    @Test
    void noAccessModeRequestsBothStorageBits() {
        final int both = SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;
        for (int access : new int[] { GL15.GL_READ_ONLY, GL15.GL_WRITE_ONLY, GL15.GL_READ_WRITE }) {
            final int usage = SDLGPURenderBackend.imageUsageForAccess(access);
            assertEquals(0, usage & SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE);
            assertNotEquals(both, usage & both, "READ|WRITE together leaves SDL's DefaultTextureUsageMode ambiguous on a sampled texture");
        }
    }
}
