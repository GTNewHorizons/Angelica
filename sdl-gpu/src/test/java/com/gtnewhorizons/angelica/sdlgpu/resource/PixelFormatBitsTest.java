package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps.colorChannelBits;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_A8_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_BC1_RGBA_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R8_UNORM;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;

class PixelFormatBitsTest {

    private static int[] rgba(int sdlFormat) {
        return new int[]{
            colorChannelBits(sdlFormat, 0),
            colorChannelBits(sdlFormat, 1),
            colorChannelBits(sdlFormat, 2),
            colorChannelBits(sdlFormat, 3),
        };
    }

    private static void assertRgba(int sdlFormat, int r, int g, int b, int a) {
        assertEquals(r, colorChannelBits(sdlFormat, 0), "red");
        assertEquals(g, colorChannelBits(sdlFormat, 1), "green");
        assertEquals(b, colorChannelBits(sdlFormat, 2), "blue");
        assertEquals(a, colorChannelBits(sdlFormat, 3), "alpha");
    }

    @Test
    void uniformWidthFormats() {
        assertRgba(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, 8, 8, 8, 8);
        assertRgba(SDL_GPU_TEXTUREFORMAT_R8_UNORM, 8, 0, 0, 0);
        assertRgba(SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, 16, 16, 0, 0);
        assertRgba(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, 32, 32, 32, 32);
    }

    @Test
    void packedFormatsReportTheirRealChannelWidths() {
        assertRgba(SDL_GPU_TEXTUREFORMAT_A8_UNORM, 0, 0, 0, 8);
        assertRgba(SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, 10, 10, 10, 2);
        assertRgba(SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, 11, 11, 10, 0);
        assertRgba(SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM, 5, 6, 5, 0);
        assertRgba(SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM, 5, 5, 5, 1);
        assertRgba(SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM, 4, 4, 4, 4);
    }

    @Test
    void depthAndCompressedFormatsHaveNoColorChannels() {
        assertRgba(SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT, 0, 0, 0, 0);
        assertRgba(SDL_GPU_TEXTUREFORMAT_BC1_RGBA_UNORM, 0, 0, 0, 0);
    }

    @Test
    void outOfRangeChannelsAreZero() {
        assertEquals(0, colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, -1));
        assertEquals(0, colorChannelBits(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, 4));
        assertEquals(4, rgba(SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM).length);
    }
}
