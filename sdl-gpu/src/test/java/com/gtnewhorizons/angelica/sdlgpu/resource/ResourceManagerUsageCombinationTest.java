package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class ResourceManagerUsageCombinationTest {

    @Test
    void samplerDropsSimultaneousReadWrite() {
        final int merged = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE;
        final int out = ResourceManager.reconcileUsage(merged);
        assertEquals(0, out & SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE, "SDL never returns a simultaneous-RW texture to the sampler default");
        assertNotEquals(0, out & SDL_GPU_TEXTUREUSAGE_SAMPLER);
        assertNotEquals(0, out & SDL_GPU_TEXTUREUSAGE_COLOR_TARGET);
    }

    @Test
    void samplerDropsGraphicsStorageRead() {
        final int merged = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ;
        final int out = ResourceManager.reconcileUsage(merged);
        assertEquals(0, out & SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ);
        assertNotEquals(0, out & SDL_GPU_TEXTUREUSAGE_SAMPLER);
    }

    @Test
    void samplerKeepsPlainComputeStorage() {
        final int merged = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;
        assertEquals(merged, ResourceManager.reconcileUsage(merged), "SDL transitions plain compute storage symmetrically to and from the sampler default");
    }

    @Test
    void withoutSamplerEveryStorageBitSurvives() {
        final int merged = SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE;
        assertEquals(merged, ResourceManager.reconcileUsage(merged));
    }

    @Test
    void plainSampledColourTextureIsUnchanged() {
        final int merged = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET;
        assertEquals(merged, ResourceManager.reconcileUsage(merged));
    }

    @Test
    void depthTargetIsUnchanged() {
        final int merged = SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET;
        assertEquals(merged, ResourceManager.reconcileUsage(merged));
    }
}
