package com.gtnewhorizons.angelica.sdlgpu;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL15;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE;

class ImageAccessUsageMappingTest {

    /** Never READ|WRITE together: that leaves SDL's DefaultTextureUsageMode ambiguous on a sampled texture. */
    static Stream<Arguments> accessModes() {
        return Stream.of(
            Arguments.of("GL_READ_ONLY", GL15.GL_READ_ONLY, SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ),
            Arguments.of("GL_WRITE_ONLY", GL15.GL_WRITE_ONLY, SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE),
            Arguments.of("GL_READ_WRITE", GL15.GL_READ_WRITE, SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_WRITE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accessModes")
    void accessModeRequestsExactlyOneStorageBit(String name, int access, int expectedUsage) {
        assertEquals(expectedUsage, SDLGPURenderBackend.imageUsageForAccess(access));
    }
}
