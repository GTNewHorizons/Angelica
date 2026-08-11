package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;

class DepthStencilBitsTest {

    @Test
    void depthBitsPerFormat() {
        assertEquals(16, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_D16_UNORM));
        assertEquals(24, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_D24_UNORM));
        assertEquals(24, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertEquals(32, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_D32_FLOAT));
        assertEquals(32, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT));
        assertEquals(0, PixelOps.depthBits(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM));
    }

    @Test
    void onlyPackedFormatsCarryStencil() {
        assertEquals(8, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertEquals(8, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT));
        assertEquals(0, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_D24_UNORM));
        assertEquals(0, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_D32_FLOAT));
        assertEquals(0, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_D16_UNORM));
        assertEquals(0, PixelOps.stencilBits(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM));
    }

    @Test
    void stencilBitsAgreesWithIsDepthStencilFormat() {
        final int[] formats = {
            SDL_GPU_TEXTUREFORMAT_D16_UNORM, SDL_GPU_TEXTUREFORMAT_D24_UNORM, SDL_GPU_TEXTUREFORMAT_D32_FLOAT,
            SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT, SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT,
            SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM,
        };
        for (int f : formats) {
            assertEquals(PixelOps.isDepthStencilFormat(f), PixelOps.stencilBits(f) != 0, "format " + f);
        }
    }

    @Test
    void colorChannelBitsForCommonFormats() {
        for (int fmt : new int[]{ SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM }) {
            for (int c = 0; c < 4; c++) assertEquals(8, PixelOps.colorChannelBits(fmt, c), "channel " + c);
        }
        assertEquals(16, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, 3));
        assertEquals(8, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8_UNORM, 0));
        assertEquals(0, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8_UNORM, 1));
        assertEquals(8, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, 1));
        assertEquals(0, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, 2));
        assertEquals(10, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, 0));
        assertEquals(2, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, 3));
        assertEquals(0, PixelOps.colorChannelBits(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT, 0));
    }

    @Test
    void depthFormatsAreNotColorFormats() {
        assertTrue(PixelOps.isDepthFormat(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT));
        assertFalse(PixelOps.isDepthFormat(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM));
    }
}
