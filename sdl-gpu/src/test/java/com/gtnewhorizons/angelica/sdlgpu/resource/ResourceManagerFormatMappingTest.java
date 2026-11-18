package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class ResourceManagerFormatMappingTest {

    private static final int D24 = SDL_GPU_TEXTUREFORMAT_D24_UNORM;
    private static final int D24S8 = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;

    private static int map(int glFormat) {
        return ResourceManager.mapTextureFormat(glFormat, D24, D24S8);
    }

    /** Unknown formats fall back to RGBA8; GL_DEPTH_COMPONENT32 must not take that fallback. */
    static Stream<Arguments> formats() {
        return Stream.of(
            Arguments.of("GL_RGBA8", GL11.GL_RGBA8, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM),
            Arguments.of("GL_RGBA", GL11.GL_RGBA, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM),
            Arguments.of("GL_RGB8", GL11.GL_RGB8, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM),
            Arguments.of("GL_RGB", GL11.GL_RGB, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM),
            Arguments.of("GL_SRGB8_ALPHA8", GL21.GL_SRGB8_ALPHA8, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB),
            Arguments.of("GL_RGBA16F", GL30.GL_RGBA16F, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT),
            Arguments.of("GL_RGBA32F", GL30.GL_RGBA32F, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT),
            Arguments.of("GL_R16F", GL30.GL_R16F, SDL_GPU_TEXTUREFORMAT_R16_FLOAT),
            Arguments.of("GL_RG16F", GL30.GL_RG16F, SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT),
            Arguments.of("GL_R32F", GL30.GL_R32F, SDL_GPU_TEXTUREFORMAT_R32_FLOAT),
            Arguments.of("GL_RG32F", GL30.GL_RG32F, SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT),
            Arguments.of("GL_R8", GL30.GL_R8, SDL_GPU_TEXTUREFORMAT_R8_UNORM),
            Arguments.of("GL_RG8", GL30.GL_RG8, SDL_GPU_TEXTUREFORMAT_R8G8_UNORM),
            Arguments.of("GL_DEPTH_COMPONENT16", GL14.GL_DEPTH_COMPONENT16, SDL_GPU_TEXTUREFORMAT_D16_UNORM),
            Arguments.of("GL_DEPTH_COMPONENT24", GL14.GL_DEPTH_COMPONENT24, SDL_GPU_TEXTUREFORMAT_D24_UNORM),
            Arguments.of("GL_DEPTH_COMPONENT32F", GL30.GL_DEPTH_COMPONENT32F, SDL_GPU_TEXTUREFORMAT_D32_FLOAT),
            Arguments.of("GL_RGBA16", GL11.GL_RGBA16, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM),
            Arguments.of("GL_DEPTH_COMPONENT32", GL14.GL_DEPTH_COMPONENT32, SDL_GPU_TEXTUREFORMAT_D32_FLOAT),
            Arguments.of("GL_DEPTH24_STENCIL8", GL30.GL_DEPTH24_STENCIL8, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT),
            Arguments.of("GL_DEPTH32F_STENCIL8", GL30.GL_DEPTH32F_STENCIL8, SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT),
            Arguments.of("GL_RGBA8UI", GL30.GL_RGBA8UI, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT),
            Arguments.of("GL_RGBA32UI", GL30.GL_RGBA32UI, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT),
            Arguments.of("GL_R11F_G11F_B10F", GL30.GL_R11F_G11F_B10F, SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT),
            Arguments.of("GL_RGB10_A2", GL11.GL_RGB10_A2, SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM),
            Arguments.of("unknown format 0xDEAD", 0xDEAD, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formats")
    void mapsGlInternalFormat(String name, int glFormat, int expectedSdlFormat) {
        assertEquals(expectedSdlFormat, map(glFormat));
    }
}
