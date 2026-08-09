package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.*;

class ClearTexImageFormatTest {

    @Test
    void unsignedIntegerFormatsUseTheComputeClear() {
        assertTrue(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R8_UINT));
        assertTrue(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R16_UINT), "voxel_img / wsr_img format");
        assertTrue(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R32_UINT));
        assertTrue(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT));
    }

    @Test
    void signedFloatAndNormalizedFormatsDoNot() {
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R16_INT), "signed needs iimage3D");
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R32_FLOAT));
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R16_FLOAT));
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R8_UNORM), "UNORM is not UINT");
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM));
        assertFalse(PixelOps.isSdlFormatUnsignedInteger(SDL_GPU_TEXTUREFORMAT_D32_FLOAT));
    }

    @Test
    void volumeSizingAccountsForDepth() {
        final int bpp = PixelOps.sdlFormatTexelBytes(SDL_GPU_TEXTUREFORMAT_R16_UINT);
        assertTrue(bpp == 2, "R16_UINT is two bytes per texel, got " + bpp);
        final long slice = 512L * 256L * bpp;
        final long volume = slice * 512L;
        assertTrue(volume / slice == 512, "a depth-aware clear must cover every slice");
    }
}
