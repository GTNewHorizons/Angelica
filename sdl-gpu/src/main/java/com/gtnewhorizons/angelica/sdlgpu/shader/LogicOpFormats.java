package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.lwjgl.sdl.SDLGPU.*;

public final class LogicOpFormats {

    public static final int OP_COPY = 12;

    private static final int CLASS_RGBA8 = 1;
    private static final int CLASS_R8 = 2;
    private static final int CLASS_RG8 = 3;
    private static final int CLASS_RGBA16 = 4;
    private static final int CLASS_R16 = 5;
    private static final int CLASS_RG16 = 6;
    private static final int CLASS_RGB10A2 = 7;
    private static final int CLASS_R5G6B5 = 8;
    private static final int CLASS_RGB5A1 = 9;
    private static final int CLASS_RGBA4 = 10;
    private static final int CLASS_A8 = 11;

    private static final int[] MESA_OP = { 0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15 };

    private static final byte[][] BITS = {
        { 0, 0, 0, 0 },
        { 8, 8, 8, 8 },
        { 8, 0, 0, 0 },
        { 8, 8, 0, 0 },
        { 16, 16, 16, 16 },
        { 16, 0, 0, 0 },
        { 16, 16, 0, 0 },
        { 10, 10, 10, 2 },
        { 5, 6, 5, 0 },
        { 5, 5, 5, 1 },
        { 4, 4, 4, 4 },
        { 0, 0, 0, 8 },
    };

    private LogicOpFormats() {}

    public static int mesaOp(int glOpcode) {
        return MESA_OP[glOpcode & 0xF];
    }

    public static int classOf(int sdlTextureFormat) {
        return switch (sdlTextureFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM -> CLASS_RGBA8;
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM -> CLASS_R8;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM -> CLASS_RG8;
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM -> CLASS_RGBA16;
            case SDL_GPU_TEXTUREFORMAT_R16_UNORM -> CLASS_R16;
            case SDL_GPU_TEXTUREFORMAT_R16G16_UNORM -> CLASS_RG16;
            case SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM -> CLASS_RGB10A2;
            case SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM -> CLASS_R5G6B5;
            case SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM -> CLASS_RGB5A1;
            case SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM -> CLASS_RGBA4;
            case SDL_GPU_TEXTUREFORMAT_A8_UNORM -> CLASS_A8;
            case SDL_GPU_TEXTUREFORMAT_R8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT,
                 SDL_GPU_TEXTUREFORMAT_R8_INT, SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT,
                 SDL_GPU_TEXTUREFORMAT_R32_INT, SDL_GPU_TEXTUREFORMAT_R32G32_INT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT ->
                throw new UnsupportedOperationException("glLogicOp on snorm/integer color attachment format " + sdlTextureFormat + " is not emulated");
            default -> 0;
        };
    }

    public static float channelMax(int cls, int channel) {
        final int bits = BITS[cls][channel];
        return bits == 0 ? 0.0f : (float) ((1 << bits) - 1);
    }

    public static long withClass(long key, int location, int cls) {
        return key | ((long) cls << (4 + 4 * location));
    }

    public static int classAt(long key, int location) {
        return (int) (key >>> (4 + 4 * location)) & 0xF;
    }

    public static int opOf(long key) {
        return (int) (key & 0xF);
    }
}
