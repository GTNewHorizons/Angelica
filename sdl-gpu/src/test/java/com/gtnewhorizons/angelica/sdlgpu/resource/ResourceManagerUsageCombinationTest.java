package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

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

    /** Plain compute storage survives because SDL transitions it symmetrically to and from the sampler default. */
    static Stream<Arguments> unchangedUsages() {
        return Stream.of(
            Arguments.of("sampler + plain compute storage",
                SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE),
            Arguments.of("every storage bit without sampler",
                SDL_GPU_TEXTUREUSAGE_GRAPHICS_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE | SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_SIMULTANEOUS_READ_WRITE),
            Arguments.of("plain sampled colour target", SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_COLOR_TARGET),
            Arguments.of("sampled depth target", SDL_GPU_TEXTUREUSAGE_SAMPLER | SDL_GPU_TEXTUREUSAGE_DEPTH_STENCIL_TARGET));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unchangedUsages")
    void usageIsUnchanged(String name, int merged) {
        assertEquals(merged, ResourceManager.reconcileUsage(merged));
    }
}
