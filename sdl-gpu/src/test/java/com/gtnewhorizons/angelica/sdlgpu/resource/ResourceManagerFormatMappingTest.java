package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

class ResourceManagerFormatMappingTest {

    private static final int D24 = SDL_GPU_TEXTUREFORMAT_D24_UNORM;
    private static final int D24S8 = SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;

    private static int map(int glFormat) {
        return ResourceManager.mapTextureFormat(glFormat, D24, D24S8);
    }

    @Test
    void testRGBA8MapsCorrectly() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(GL11.GL_RGBA8));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(GL11.GL_RGBA));
    }

    @Test
    void testRGB8PromotesToRGBA8() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(GL11.GL_RGB8));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(GL11.GL_RGB));
    }

    @Test
    void testSRGBAlpha() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, map(GL21.GL_SRGB8_ALPHA8));
    }

    @Test
    void testFloatFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, map(GL30.GL_RGBA16F));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, map(GL30.GL_RGBA32F));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R16_FLOAT, map(GL30.GL_R16F));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, map(GL30.GL_RG16F));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R32_FLOAT, map(GL30.GL_R32F));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, map(GL30.GL_RG32F));
    }

    @Test
    void testSingleChannelFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8_UNORM, map(GL30.GL_R8));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, map(GL30.GL_RG8));
    }

    @Test
    void testDepthFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_D16_UNORM, map(GL14.GL_DEPTH_COMPONENT16));
        assertEquals(SDL_GPU_TEXTUREFORMAT_D24_UNORM, map(GL14.GL_DEPTH_COMPONENT24));
        assertEquals(SDL_GPU_TEXTUREFORMAT_D32_FLOAT, map(GL30.GL_DEPTH_COMPONENT32F));
    }

    @Test
    void testRGBA16MapsToR16G16B16A16Unorm() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM, map(GL11.GL_RGBA16));
    }

    @Test
    void testDepthComponent32MapsToD32Float() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_D32_FLOAT, map(GL14.GL_DEPTH_COMPONENT32));
        assertNotEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(GL14.GL_DEPTH_COMPONENT32), "must not fall through to the colour-format default on a depth attachment");
    }

    @Test
    void testDepthStencilFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT, map(GL30.GL_DEPTH24_STENCIL8));
        assertEquals(SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT, map(GL30.GL_DEPTH32F_STENCIL8));
    }

    @Test
    void testIntegerFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT, map(GL30.GL_RGBA8UI));
        assertEquals(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT, map(GL30.GL_RGBA32UI));
    }

    @Test
    void testPackedFormats() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, map(GL30.GL_R11F_G11F_B10F));
    }

    @Test
    void testUnknownFormatFallsBackToRGBA8() {
        assertEquals(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, map(0xDEAD));
    }
}
