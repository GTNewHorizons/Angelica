package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.sdl.SDLGPU.*;

class PixelPackTest {

    private static final int SENTINEL = 0xEE;

    @BeforeEach
    void littleEndianHost() {
        assumeTrue(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] b(int... v) {
        final byte[] out = new byte[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (byte) v[i];
        return out;
    }

    private static byte[] le16(int... v) {
        final byte[] out = new byte[v.length * 2];
        for (int i = 0; i < v.length; i++) {
            out[i * 2] = (byte) v[i];
            out[i * 2 + 1] = (byte) (v[i] >>> 8);
        }
        return out;
    }

    private static byte[] le32(int... v) {
        final byte[] out = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) {
            out[i * 4] = (byte) v[i];
            out[i * 4 + 1] = (byte) (v[i] >>> 8);
            out[i * 4 + 2] = (byte) (v[i] >>> 16);
            out[i * 4 + 3] = (byte) (v[i] >>> 24);
        }
        return out;
    }

    private static byte[] f32(float... v) {
        final int[] bits = new int[v.length];
        for (int i = 0; i < v.length; i++) bits[i] = Float.floatToRawIntBits(v[i]);
        return le32(bits);
    }

    private static ByteBuffer direct(byte[] content, int size) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        for (int i = 0; i < size; i++) buf.put(i, (byte) SENTINEL);
        for (int i = 0; i < content.length; i++) buf.put(i, content[i]);
        return buf;
    }

    private static byte[] read(ByteBuffer buf, int len) {
        final byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = buf.get(i);
        return out;
    }

    private static PackState ps(int alignment) {
        final PackState ps = new PackState();
        ps.alignment = alignment;
        return ps;
    }

    private static byte[] pack1(int sdlFormat, byte[] texel, boolean readPixels, int format, int type) {
        final ByteBuffer src = direct(texel, texel.length);
        final int outSize = PixelOps.glPixelSize(format, type);
        final ByteBuffer dst = direct(new byte[0], outSize + 8);
        PixelPack.packColor(MemoryUtil.memAddress(src), sdlFormat, storageBase(sdlFormat), texel.length, 1, 1, false, readPixels, format, type, ps(1), MemoryUtil.memAddress(dst));
        for (int i = outSize; i < outSize + 8; i++) assertEquals((byte) SENTINEL, dst.get(i), "wrote past pixel");
        return read(dst, outSize);
    }

    private static int storageBase(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM, SDL_GPU_TEXTUREFORMAT_R16_UNORM, SDL_GPU_TEXTUREFORMAT_R16_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32_FLOAT, SDL_GPU_TEXTUREFORMAT_R8_SNORM, SDL_GPU_TEXTUREFORMAT_R16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8_UINT, SDL_GPU_TEXTUREFORMAT_R8_INT, SDL_GPU_TEXTUREFORMAT_R16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16_INT, SDL_GPU_TEXTUREFORMAT_R32_UINT, SDL_GPU_TEXTUREFORMAT_R32_INT -> GL11.GL_RED;
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT,
                 SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, SDL_GPU_TEXTUREFORMAT_R16G16_SNORM,
                 SDL_GPU_TEXTUREFORMAT_R8G8_UINT, SDL_GPU_TEXTUREFORMAT_R8G8_INT, SDL_GPU_TEXTUREFORMAT_R16G16_UINT,
                 SDL_GPU_TEXTUREFORMAT_R16G16_INT, SDL_GPU_TEXTUREFORMAT_R32G32_UINT, SDL_GPU_TEXTUREFORMAT_R32G32_INT -> GL30.GL_RG;
            case SDL_GPU_TEXTUREFORMAT_A8_UNORM -> GL11.GL_ALPHA;
            case SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM -> GL11.GL_RGB;
            default -> GL11.GL_RGBA;
        };
    }

    private static byte[] packBase(int sdlFormat, int base, byte[] texel, boolean readPixels, int format, int type) {
        final ByteBuffer src = direct(texel, texel.length);
        final int outSize = PixelOps.glPixelSize(format, type);
        final ByteBuffer dst = direct(new byte[0], outSize);
        PixelPack.packColor(MemoryUtil.memAddress(src), sdlFormat, base, texel.length, 1, 1, false, readPixels, format, type, ps(1), MemoryUtil.memAddress(dst));
        return read(dst, outSize);
    }

    @Test
    void rgbBaseOverRgbaStorageReadsAlphaOne() {
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        final byte[] px = b(0x11, 0x22, 0x33, 0x80);
        assertArrayEquals(b(0x11, 0x22, 0x33, 0xFF), packBase(rgba8, GL11.GL_RGB, px, true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x11, 0x22, 0x33, 0xFF), packBase(rgba8, GL11.GL_RGB, px, false, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x33, 0x22, 0x11, 0xFF), packBase(rgba8, GL11.GL_RGB, px, true, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
        assertArrayEquals(b(0x11, 0x22, 0x33), packBase(rgba8, GL11.GL_RGB, px, true, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0xFF), packBase(rgba8, GL11.GL_RGB, px, true, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(f32(1.0f), packBase(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, GL11.GL_RGB, le16(0, 0, 0, 0x3800), true, GL11.GL_ALPHA, GL11.GL_FLOAT));
        assertArrayEquals(b(1, 2, 3, 1), packBase(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT, GL11.GL_RGB, b(1, 2, 3, 9), true, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void luminanceIntensityAlphaBasesRebase() {
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        final byte[] px = b(0x40, 0x50, 0x60, 0x70);
        assertArrayEquals(b(0x40, 0x00, 0x00, 0xFF), packBase(rgba8, GL11.GL_LUMINANCE, px, true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x00, 0x00, 0xFF), packBase(rgba8, GL11.GL_INTENSITY, px, false, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x00, 0x00, 0x70), packBase(rgba8, GL11.GL_LUMINANCE_ALPHA, px, true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x00, 0x00, 0x00, 0x70), packBase(rgba8, GL11.GL_ALPHA, px, true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40), packBase(rgba8, GL11.GL_LUMINANCE, px, true, GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x70), packBase(rgba8, GL11.GL_LUMINANCE_ALPHA, px, true, GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x00), packBase(rgba8, GL11.GL_ALPHA, px, true, GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void luminanceSumUsesRebasedChannels() {
        assertArrayEquals(b(0x80), packBase(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL30.GL_RG, b(0x40, 0x40, 0x40, 0x70), true, GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0xC0), packBase(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_RGB, b(0x40, 0x40, 0x40, 0x70), true, GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void glBaseFormatFollowsMesa() {
        assertEquals(GL11.GL_RGB, PixelPack.glBaseFormat(GL11.GL_RGB8));
        assertEquals(GL11.GL_RGB, PixelPack.glBaseFormat(GL30.GL_R11F_G11F_B10F));
        assertEquals(GL11.GL_RGB, PixelPack.glBaseFormat(GL30.GL_RGB16UI));
        assertEquals(GL11.GL_RGBA, PixelPack.glBaseFormat(GL11.GL_RGBA8));
        assertEquals(GL11.GL_RGBA, PixelPack.glBaseFormat(GL11.GL_RGB10_A2));
        assertEquals(GL11.GL_RGBA, PixelPack.glBaseFormat(GL21.GL_SRGB8_ALPHA8));
        assertEquals(GL11.GL_RGBA, PixelPack.glBaseFormat(GL30.GL_RGBA16F));
        assertEquals(GL11.GL_RED, PixelPack.glBaseFormat(GL30.GL_R16F));
        assertEquals(GL11.GL_RED, PixelPack.glBaseFormat(GL30.GL_R8I));
        assertEquals(GL30.GL_RG, PixelPack.glBaseFormat(GL30.GL_RG32UI));
        assertEquals(GL11.GL_ALPHA, PixelPack.glBaseFormat(GL11.GL_ALPHA8));
        assertEquals(GL11.GL_LUMINANCE, PixelPack.glBaseFormat(1));
        assertEquals(GL11.GL_LUMINANCE_ALPHA, PixelPack.glBaseFormat(GL11.GL_LUMINANCE8_ALPHA8));
        assertEquals(GL11.GL_INTENSITY, PixelPack.glBaseFormat(GL11.GL_INTENSITY16));
        assertEquals(GL11.GL_DEPTH_COMPONENT, PixelPack.glBaseFormat(GL30.GL_DEPTH_COMPONENT32F));
        assertEquals(GL30.GL_DEPTH_STENCIL, PixelPack.glBaseFormat(GL30.GL_DEPTH24_STENCIL8));
        assertEquals(GL11.GL_STENCIL_INDEX, PixelPack.glBaseFormat(GL30.GL_STENCIL_INDEX8));
        assertEquals(-1, PixelPack.glBaseFormat(0x1234));
        assertThrows(IllegalArgumentException.class, () -> packBase(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, -1, b(0, 0, 0, 0), true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertThrows(IllegalArgumentException.class, () -> packBase(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_DEPTH_COMPONENT, b(0, 0, 0, 0), true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
    }

    private static byte[] readPix(int sdlFormat, byte[] texel, int format, int type) {
        return pack1(sdlFormat, texel, true, format, type);
    }

    private static byte[] getTex(int sdlFormat, byte[] texel, int format, int type) {
        return pack1(sdlFormat, texel, false, format, type);
    }

    @Test
    void decodeRgba8() {
        assertArrayEquals(b(0x11, 0x22, 0x33, 0x44), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, b(0x11, 0x22, 0x33, 0x44), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le16(0x1111, 0x2222, 0x3333, 0x4444), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, b(0x11, 0x22, 0x33, 0x44), GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT));
    }

    @Test
    void decodeBgra8() {
        assertArrayEquals(b(0x11, 0x22, 0x33, 0x44), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, b(0x33, 0x22, 0x11, 0x44), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x33, 0x22, 0x11, 0x44), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, b(0x33, 0x22, 0x11, 0x44), GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x33, 0x22, 0x11, 0x44), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, b(0x33, 0x22, 0x11, 0x44), GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
        assertArrayEquals(le16(0x1111, 0x2222, 0x3333), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, b(0x33, 0x22, 0x11, 0x44), GL11.GL_RGB, GL11.GL_UNSIGNED_SHORT));
    }

    @Test
    void decodeSrgbReturnsStoredValues() {
        assertArrayEquals(b(0x80, 0x40, 0x20, 0x10), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, b(0x80, 0x40, 0x20, 0x10), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x80, 0x40, 0x20, 0x10), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB, b(0x20, 0x40, 0x80, 0x10), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void decodeR8AndRg8RebaseAlphaToOne() {
        assertArrayEquals(b(0xFF, 0x00, 0x00, 0xFF), readPix(SDL_GPU_TEXTUREFORMAT_R8_UNORM, b(0xFF), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x80, 0x00, 0xFF), readPix(SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, b(0x40, 0x80), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x80), readPix(SDL_GPU_TEXTUREFORMAT_R8G8_UNORM, b(0x40, 0x80), GL30.GL_RG, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void decodeUnorm16() {
        assertArrayEquals(b(0x12), readPix(SDL_GPU_TEXTUREFORMAT_R16_UNORM, le16(0x1234), GL11.GL_RED, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(f32(0.0f, 1.0f), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, le16(0x0000, 0xFFFF), GL30.GL_RG, GL11.GL_FLOAT));
        assertArrayEquals(b(18, 128, 255, 0), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM, le16(0x1234, 0x8000, 0xFFFF, 0x0000), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void decodeA8RebasesColorToZero() {
        assertArrayEquals(b(0, 0, 0, 0x7F), readPix(SDL_GPU_TEXTUREFORMAT_A8_UNORM, b(0x7F), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x7F), readPix(SDL_GPU_TEXTUREFORMAT_A8_UNORM, b(0x7F), GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void decodeHalfFloat() {
        assertArrayEquals(f32(1.0f, 0.0f, 0.0f, 1.0f), readPix(SDL_GPU_TEXTUREFORMAT_R16_FLOAT, le16(0x3C00), GL11.GL_RGBA, GL11.GL_FLOAT));
        assertArrayEquals(f32(0.5f, -2.0f), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, le16(0x3800, 0xC000), GL30.GL_RG, GL11.GL_FLOAT));
        assertArrayEquals(f32(0.25f, 0.5f, 1.0f, 2.0f), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, le16(0x3400, 0x3800, 0x3C00, 0x4000), GL11.GL_RGBA, GL11.GL_FLOAT));
        assertArrayEquals(le16(0x3400, 0x3800, 0x3C00, 0x4000), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, le16(0x3400, 0x3800, 0x3C00, 0x4000), GL11.GL_RGBA, GL30.GL_HALF_FLOAT));
    }

    @Test
    void decodeFloat32() {
        assertArrayEquals(b(191), readPix(SDL_GPU_TEXTUREFORMAT_R32_FLOAT, f32(0.75f), GL11.GL_RED, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(f32(0.75f), readPix(SDL_GPU_TEXTUREFORMAT_R32_FLOAT, f32(0.75f), GL11.GL_RED, GL11.GL_FLOAT));
        assertArrayEquals(f32(1.5f, -0.5f), readPix(SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, f32(1.5f, -0.5f), GL30.GL_RG, GL11.GL_FLOAT));
        assertArrayEquals(le16(0x3E00, 0xB800), readPix(SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, f32(1.5f, -0.5f), GL30.GL_RG, GL30.GL_HALF_FLOAT));
        assertArrayEquals(f32(-1.0f, 0.0f, 0.5f, 3.0f), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f32(-1.0f, 0.0f, 0.5f, 3.0f), GL11.GL_RGBA, GL11.GL_FLOAT));
    }

    @Test
    void decodeR11G11B10() {
        assertArrayEquals(f32(1.0f, 0.5f, 2.0f, 1.0f), readPix(SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, le32(0x801C03C0), GL11.GL_RGBA, GL11.GL_FLOAT));
        assertArrayEquals(le32(0x801C03C0), readPix(SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, le32(0x801C03C0), GL11.GL_RGB, GL30.GL_UNSIGNED_INT_10F_11F_11F_REV));
    }

    @Test
    void decodeR10G10B10A2() {
        assertArrayEquals(b(255, 128, 0, 255), readPix(SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, le32(0xC00803FF), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le32(0xC00803FF), readPix(SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, le32(0xC00803FF), GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
    }

    @Test
    void decodePacked16() {
        assertArrayEquals(b(255, 130, 8, 255), readPix(SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM, le16(0xFC01), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le16(0xFC01), readPix(SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM, le16(0xFC01), GL11.GL_RGB, GL12.GL_UNSIGNED_SHORT_5_6_5));
        assertArrayEquals(b(132, 0, 255, 255), readPix(SDL_GPU_TEXTUREFORMAT_B5G5R5A1_UNORM, le16(0xC01F), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(255, 136, 0, 68), readPix(SDL_GPU_TEXTUREFORMAT_B4G4R4A4_UNORM, le16(0x4F80), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void decodeSnorm() {
        assertArrayEquals(f32(-1.0f), getTex(SDL_GPU_TEXTUREFORMAT_R8_SNORM, b(0x81), GL11.GL_RED, GL11.GL_FLOAT));
        assertArrayEquals(f32(0.0f), readPix(SDL_GPU_TEXTUREFORMAT_R8_SNORM, b(0x81), GL11.GL_RED, GL11.GL_FLOAT));
        assertArrayEquals(b(255), getTex(SDL_GPU_TEXTUREFORMAT_R8_SNORM, b(0x7F), GL11.GL_RED, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(f32(1.0f, -1.0f), getTex(SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, b(0x7F, 0x80), GL30.GL_RG, GL11.GL_FLOAT));
        assertArrayEquals(b(0x7F, 0x00, 0x81, 0x7F), getTex(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_SNORM, b(0x7F, 0x00, 0x81, 0x7F), GL11.GL_RGBA, GL11.GL_BYTE));
        assertArrayEquals(f32(1.0f), getTex(SDL_GPU_TEXTUREFORMAT_R16_SNORM, le16(0x7FFF), GL11.GL_RED, GL11.GL_FLOAT));
        assertArrayEquals(f32(-1.0f, 0.0f), getTex(SDL_GPU_TEXTUREFORMAT_R16G16_SNORM, le16(0x8000, 0x0000), GL30.GL_RG, GL11.GL_FLOAT));
        assertArrayEquals(b(127, 0x81, 64, 0), getTex(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_SNORM, le16(0x7FFF, 0x8001, 0x4000, 0x0000), GL11.GL_RGBA, GL11.GL_BYTE));
    }

    @Test
    void decodeInteger8() {
        assertArrayEquals(b(200, 0, 0, 1), readPix(SDL_GPU_TEXTUREFORMAT_R8_UINT, b(200), GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0), readPix(SDL_GPU_TEXTUREFORMAT_R8_INT, b(0xFB), GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le32(-5), readPix(SDL_GPU_TEXTUREFORMAT_R8_INT, b(0xFB), GL30.GL_RED_INTEGER, GL11.GL_INT));
        assertArrayEquals(le16(9, 250), readPix(SDL_GPU_TEXTUREFORMAT_R8G8_UINT, b(9, 250), GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_SHORT));
        assertArrayEquals(b(0x80, 0x7F), readPix(SDL_GPU_TEXTUREFORMAT_R8G8_INT, b(0x80, 0x7F), GL30.GL_RG_INTEGER, GL11.GL_BYTE));
        assertArrayEquals(b(3, 2, 1, 4), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT, b(1, 2, 3, 4), GL30.GL_BGRA_INTEGER, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le16(0xFFFF, 2, 3, 4), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_INT, b(0xFF, 2, 3, 4), GL30.GL_RGBA_INTEGER, GL11.GL_SHORT));
    }

    @Test
    void decodeInteger16() {
        assertArrayEquals(le32(65535), readPix(SDL_GPU_TEXTUREFORMAT_R16_UINT, le16(0xFFFF), GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT));
        assertArrayEquals(le16(0x8000), readPix(SDL_GPU_TEXTUREFORMAT_R16_INT, le16(0x8000), GL30.GL_RED_INTEGER, GL11.GL_SHORT));
        assertArrayEquals(b(255, 1), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_UINT, le16(0xFFFF, 1), GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le16(0x7FFF, 1), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_UINT, le16(0xFFFF, 1), GL30.GL_RG_INTEGER, GL11.GL_SHORT));
        assertArrayEquals(b(0x80, 0x7F), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_INT, le16(0x8000, 0x7FFF), GL30.GL_RG_INTEGER, GL11.GL_BYTE));
        assertArrayEquals(le32(0xC03FFFFF), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UINT, le16(1023, 2000, 3, 5), GL30.GL_RGBA_INTEGER, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
        assertArrayEquals(le32(0, 2, 3, 4), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_INT, le16(0xFFFF, 2, 3, 4), GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT));
    }

    @Test
    void decodeInteger32() {
        assertArrayEquals(le16(0xFFFF), readPix(SDL_GPU_TEXTUREFORMAT_R32_UINT, le32(70000), GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT));
        assertArrayEquals(le32(-70000), readPix(SDL_GPU_TEXTUREFORMAT_R32_INT, le32(-70000), GL30.GL_RED_INTEGER, GL11.GL_INT));
        assertArrayEquals(le32(0x7FFFFFFF, 1), readPix(SDL_GPU_TEXTUREFORMAT_R32G32_UINT, le32(0xFFFFFFFF, 1), GL30.GL_RG_INTEGER, GL11.GL_INT));
        assertArrayEquals(le32(0, 7), readPix(SDL_GPU_TEXTUREFORMAT_R32G32_INT, le32(-7, 7), GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT));
        assertArrayEquals(le32(0x7FFFFFFF, 5, 0, 7), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_UINT, le32(0xFFFFFFFF, 5, 0, 7), GL30.GL_RGBA_INTEGER, GL11.GL_INT));
        assertArrayEquals(le32(0, 2, 3, 4), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT, le32(-1, 2, 3, 4), GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT));
        assertArrayEquals(le32(-1, 2, 3), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_INT, le32(-1, 2, 3, 4), GL30.GL_RGB_INTEGER, GL11.GL_INT));
    }

    @Test
    void encodeUnormToIntegerTypes() {
        final byte[] px = b(0xFF, 0x80, 0x00, 0x40);
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        assertArrayEquals(b(127, 64, 0, 32), readPix(rgba8, px, GL11.GL_RGBA, GL11.GL_BYTE));
        assertArrayEquals(le16(0xFFFF, 0x8080, 0x0000, 0x4040), readPix(rgba8, px, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT));
        assertArrayEquals(le16(0x7FFF, 0x4040, 0x0000, 0x2020), readPix(rgba8, px, GL11.GL_RGBA, GL11.GL_SHORT));
        assertArrayEquals(le32(0xFFFFFFFF, 0x80808080, 0x00000000, 0x40404040), readPix(rgba8, px, GL11.GL_RGBA, GL11.GL_UNSIGNED_INT));
        assertArrayEquals(le32(0x7FFFFFFF, 0x40404040, 0x00000000, 0x20202020), readPix(rgba8, px, GL11.GL_RGBA, GL11.GL_INT));
        assertArrayEquals(f32(1.0f, 0.0f, 1.0f, 0.0f), readPix(rgba8, b(0xFF, 0x00, 0xFF, 0x00), GL11.GL_RGBA, GL11.GL_FLOAT));
        assertArrayEquals(le16(0x3C00, 0x0000, 0x3C00, 0x0000), readPix(rgba8, b(0xFF, 0x00, 0xFF, 0x00), GL11.GL_RGBA, GL30.GL_HALF_FLOAT));
        assertArrayEquals(b(0x00, 0x80, 0xFF), readPix(rgba8, px, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x80), readPix(rgba8, px, GL11.GL_GREEN, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(le16(0x0000), readPix(rgba8, px, GL11.GL_BLUE, GL11.GL_UNSIGNED_SHORT));
    }

    @Test
    void encodePackedTypes() {
        final byte[] px = b(0xFF, 0x80, 0x00, 0x40);
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        assertArrayEquals(b(0xF0), readPix(rgba8, px, GL11.GL_RGB, GL12.GL_UNSIGNED_BYTE_3_3_2));
        assertArrayEquals(b(0x27), readPix(rgba8, px, GL11.GL_RGB, GL12.GL_UNSIGNED_BYTE_2_3_3_REV));
        assertArrayEquals(le16(0xFC00), readPix(rgba8, px, GL11.GL_RGB, GL12.GL_UNSIGNED_SHORT_5_6_5));
        assertArrayEquals(le16(0x041F), readPix(rgba8, px, GL11.GL_RGB, GL12.GL_UNSIGNED_SHORT_5_6_5_REV));
        assertArrayEquals(le16(0xF804), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_SHORT_4_4_4_4));
        assertArrayEquals(le16(0x408F), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV));
        assertArrayEquals(le16(0xFC00), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_SHORT_5_5_5_1));
        assertArrayEquals(le16(0x021F), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV));
        assertArrayEquals(le32(0xFF800040), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8));
        assertArrayEquals(le32(0x40FF8000), readPix(rgba8, px, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV));
        assertArrayEquals(le32(0xFFE02001), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_10_10_10_2));
        assertArrayEquals(le32(0x40080BFF), readPix(rgba8, px, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
    }

    @Test
    void encodeUnormUpConversionRoundsLikeSpec() {
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        assertArrayEquals(le32(0x666664CD), readPix(rgba8, b(51, 102, 153, 85), GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
        assertArrayEquals(le32(0x59964736), readPix(rgba8, b(205, 100, 102, 102), GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
        assertArrayEquals(le16(0x0102, 0xFEFE), getTex(SDL_GPU_TEXTUREFORMAT_R8G8_SNORM, b(1, 0xFF), GL30.GL_RG, GL11.GL_SHORT));
    }

    @Test
    void encodePackedFloatTypes() {
        final byte[] half = le16(0x3C00, 0x3800, 0x4000, 0x3C00);
        assertArrayEquals(le32(0x801C03C0), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, half, GL11.GL_RGB, GL30.GL_UNSIGNED_INT_10F_11F_11F_REV));
        assertArrayEquals(le32(0x81010100), getTex(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, le16(0x3C00, 0x3800, 0x3400, 0x3C00), GL11.GL_RGB, GL30.GL_UNSIGNED_INT_5_9_9_9_REV));
    }

    @Test
    void encodeHalfRoundsToNearestEven() {
        final byte[] src = f32(1.00048828125f, 1.00146484375f, Math.scalb(1.0f, -24), -2.0f);
        assertArrayEquals(le16(0x3C00, 0x3C02, 0x0001, 0xC000), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, src, GL11.GL_RGBA, GL30.GL_HALF_FLOAT));
        assertArrayEquals(le16(0x7BFF, 0x7C00), readPix(SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT, f32(65504.0f, 65520.0f), GL30.GL_RG, GL30.GL_HALF_FLOAT));
    }

    @Test
    void rebaseAlphaForRgbSources() {
        assertArrayEquals(f32(1.0f, 0.5f, 2.0f, 1.0f), readPix(SDL_GPU_TEXTUREFORMAT_R11G11B10_UFLOAT, le32(0x801C03C0), GL11.GL_RGBA, GL11.GL_FLOAT));
        assertArrayEquals(le16(0xFFFF), readPix(SDL_GPU_TEXTUREFORMAT_B5G6R5_UNORM, le16(0xFC01), GL11.GL_ALPHA, GL11.GL_UNSIGNED_SHORT));
    }

    @Test
    void luminanceSumsForReadPixelsButNotGetTexImage() {
        final int rgba8 = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
        assertArrayEquals(b(192), readPix(rgba8, b(0x40, 0x40, 0x40, 0x80), GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(192, 128), readPix(rgba8, b(0x40, 0x40, 0x40, 0x80), GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40), getTex(rgba8, b(0x40, 0x40, 0x40, 0x80), GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40, 0x80), getTex(rgba8, b(0x40, 0x40, 0x40, 0x80), GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0x40), readPix(SDL_GPU_TEXTUREFORMAT_R8_UNORM, b(0x40), GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void luminanceSumClampsForFixedPointOnly() {
        assertArrayEquals(b(255), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, b(0xFF, 0xFF, 0, 0), GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(f32(1.0f), readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, b(0xFF, 0xFF, 0, 0), GL11.GL_LUMINANCE, GL11.GL_FLOAT));
        assertArrayEquals(f32(1.5f), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f32(0.5f, 0.75f, 0.25f, 1.0f), GL11.GL_LUMINANCE, GL11.GL_FLOAT));
    }

    @Test
    void clampVersusNoClamp() {
        final byte[] half = le16(0x3400, 0x3800, 0x3C00, 0x4000);
        assertArrayEquals(b(64, 128, 255, 255), readPix(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, half, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertArrayEquals(b(0), readPix(SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT, le16(0xC000, 0x3C00), GL11.GL_RED, GL11.GL_UNSIGNED_BYTE));
        final byte[] f = f32(-1.0f, 0.0f, 0.5f, 3.0f);
        assertArrayEquals(le16(0, 0, 16384, 32767), readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f, GL11.GL_RGBA, GL11.GL_SHORT));
        assertArrayEquals(le16(0x8001, 0, 16384, 32767), getTex(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f, GL11.GL_RGBA, GL11.GL_SHORT));
        assertArrayEquals(f, getTex(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f, GL11.GL_RGBA, GL11.GL_FLOAT));
    }

    private static byte[] packR8(byte[] src, int w, int h, int format, int type, PackState ps, boolean flip, int dstSize) {
        final ByteBuffer s = direct(src, src.length);
        final ByteBuffer d = direct(new byte[0], dstSize);
        PixelPack.packColor(MemoryUtil.memAddress(s), SDL_GPU_TEXTUREFORMAT_R8_UNORM, GL11.GL_RED, w, w, h, flip, true, format, type, ps, MemoryUtil.memAddress(d));
        return read(d, dstSize);
    }

    @Test
    void packAlignment() {
        final byte[] src = b(1, 2, 3, 4, 5, 6);
        assertArrayEquals(b(1, 2, 3, 4, 5, 6, SENTINEL), packR8(src, 3, 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, ps(1), false, 7));
        assertEquals(6L, PixelPack.imageSize(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 3, 2, ps(1)));
        assertArrayEquals(b(1, 2, 3, SENTINEL, 4, 5, 6, SENTINEL), packR8(src, 3, 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, ps(4), false, 8));
        assertEquals(7L, PixelPack.imageSize(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 3, 2, ps(4)));
        assertArrayEquals(b(1, 2, 3, SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, 4, 5, 6, SENTINEL), packR8(src, 3, 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, ps(8), false, 12));
        assertEquals(11L, PixelPack.imageSize(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 3, 2, ps(8)));
        assertEquals(12, PixelPack.rowStride(GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, 3, ps(4)));
        assertEquals(24, PixelPack.rowStride(GL30.GL_RG, GL11.GL_FLOAT, 3, ps(8)));
        assertEquals(24, PixelPack.rowStride(GL30.GL_DEPTH_STENCIL, GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, 3, ps(4)));
    }

    @Test
    void packRowLengthAndSkips() {
        final PackState rl = ps(1);
        rl.rowLength = 5;
        assertArrayEquals(b(1, 2, SENTINEL, SENTINEL, SENTINEL, 3, 4, SENTINEL), packR8(b(1, 2, 3, 4), 2, 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, rl, false, 8));
        assertEquals(7L, PixelPack.imageSize(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 2, 2, rl));

        final PackState skip = ps(1);
        skip.rowLength = 4;
        skip.skipRows = 2;
        skip.skipPixels = 1;
        final byte[] out = packR8(b(7, 8), 2, 1, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, skip, false, 12);
        final byte[] expected = b(SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, SENTINEL, 7, 8, SENTINEL);
        assertArrayEquals(expected, out);
        assertEquals(11L, PixelPack.imageSize(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, 2, 1, skip));

        final PackState rgbSkip = ps(4);
        rgbSkip.skipPixels = 1;
        assertArrayEquals(b(SENTINEL, SENTINEL, SENTINEL, 9, 0, 0, SENTINEL), packR8(b(9), 1, 1, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, rgbSkip, false, 7));
    }

    @Test
    void packFlipRows() {
        assertArrayEquals(b(3, 4, 1, 2), packR8(b(1, 2, 3, 4), 2, 2, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, ps(1), true, 4));
        assertArrayEquals(b(3, 0, 4, 0, 1, 0, 2, 0), packR8(b(1, 2, 3, 4), 2, 2, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, ps(1), true, 8));
    }

    @Test
    void packFlipRowsWithChannelSwap() {
        final ByteBuffer s = direct(b(0xAA, 0x01, 0x02, 0x03, 0xBB, 0x10, 0x20, 0x30), 8);
        final ByteBuffer d = direct(new byte[0], 8);
        PixelPack.packColor(MemoryUtil.memAddress(s), SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_RGBA, 4, 1, 2, true, true, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, ps(4), MemoryUtil.memAddress(d));
        assertArrayEquals(b(0x20, 0x10, 0xBB, 0x30, 0x02, 0x01, 0xAA, 0x03), read(d, 8));
        assertArrayEquals(b(223, 164, 64, 255), readPix(SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, b(223, 164, 64, 255), GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE));
    }

    @Test
    void packSwapBytes() {
        final PackState swap = ps(1);
        swap.swapBytes = true;
        final ByteBuffer s16 = direct(le16(0x1234, 0xABCD), 4);
        final ByteBuffer d16 = direct(new byte[0], 4);
        PixelPack.packColor(MemoryUtil.memAddress(s16), SDL_GPU_TEXTUREFORMAT_R16G16_UNORM, GL30.GL_RG, 4, 1, 1, false, true, GL30.GL_RG, GL11.GL_UNSIGNED_SHORT, swap, MemoryUtil.memAddress(d16));
        assertArrayEquals(b(0x12, 0x34, 0xAB, 0xCD), read(d16, 4));

        final ByteBuffer s32 = direct(f32(1.0f), 4);
        final ByteBuffer d32 = direct(new byte[0], 4);
        PixelPack.packColor(MemoryUtil.memAddress(s32), SDL_GPU_TEXTUREFORMAT_R32_FLOAT, GL11.GL_RED, 4, 1, 1, false, true, GL11.GL_RED, GL11.GL_FLOAT, swap, MemoryUtil.memAddress(d32));
        assertArrayEquals(b(0x3F, 0x80, 0x00, 0x00), read(d32, 4));

        final ByteBuffer s8888 = direct(b(0xFF, 0x80, 0x00, 0x40), 4);
        final ByteBuffer d8888 = direct(new byte[0], 4);
        PixelPack.packColor(MemoryUtil.memAddress(s8888), SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_RGBA, 4, 1, 1, false, true, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8, swap, MemoryUtil.memAddress(d8888));
        assertArrayEquals(b(0xFF, 0x80, 0x00, 0x40), read(d8888, 4));

        final ByteBuffer s565 = direct(b(0xFF, 0x80, 0x00, 0x40), 4);
        final ByteBuffer d565 = direct(new byte[0], 2);
        PixelPack.packColor(MemoryUtil.memAddress(s565), SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_RGBA, 4, 1, 1, false, true, GL11.GL_RGB, GL12.GL_UNSIGNED_SHORT_5_6_5, swap, MemoryUtil.memAddress(d565));
        assertArrayEquals(b(0xFC, 0x00), read(d565, 2));

        final ByteBuffer sub = direct(b(0x11, 0x22, 0x33, 0x44), 4);
        final ByteBuffer dub = direct(new byte[0], 4);
        PixelPack.packColor(MemoryUtil.memAddress(sub), SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, GL11.GL_RGBA, 4, 1, 1, false, true, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, swap, MemoryUtil.memAddress(dub));
        assertArrayEquals(b(0x11, 0x22, 0x33, 0x44), read(dub, 4));
    }

    private static byte[] depth(int type, int outBytes, PackState ps, boolean flip, float... z) {
        final ByteBuffer s = direct(f32(z), z.length * 4);
        final ByteBuffer d = direct(new byte[0], outBytes);
        PixelPack.packDepth(MemoryUtil.memAddress(s), z.length * 4, z.length, 1, flip, type, ps, MemoryUtil.memAddress(d));
        return read(d, outBytes);
    }

    @Test
    void packDepthTypes() {
        final float[] z = {0.0f, 0.5f, 1.0f, 0.25f};
        assertArrayEquals(f32(z), depth(GL11.GL_FLOAT, 16, ps(4), false, z));
        assertArrayEquals(le32(0, 0x7FFFFFFF, 0xFFFFFFFF, 0x3FFFFFFF), depth(GL11.GL_UNSIGNED_INT, 16, ps(4), false, z));
        assertArrayEquals(le16(0, 0x8000, 0xFFFF, 0x4000), depth(GL11.GL_UNSIGNED_SHORT, 8, ps(4), false, z));
        assertArrayEquals(b(0, 127, 255, 63), depth(GL11.GL_UNSIGNED_BYTE, 4, ps(4), false, z));
        assertArrayEquals(b(0, 63, 127, 31), depth(GL11.GL_BYTE, 4, ps(4), false, z));
        assertArrayEquals(le16(0, 16383, 32767, 8191), depth(GL11.GL_SHORT, 8, ps(4), false, z));
        assertArrayEquals(le32(0, 1073741823, 2147483647, 536870911), depth(GL11.GL_INT, 16, ps(4), false, z));
        assertArrayEquals(le16(0x0000, 0x3800, 0x3C00, 0x3400), depth(GL30.GL_HALF_FLOAT, 8, ps(4), false, z));
    }

    @Test
    void packDepthFlipAndSwap() {
        final ByteBuffer s = direct(f32(0.0f, 1.0f), 8);
        final ByteBuffer d = direct(new byte[0], 4);
        final PackState swap = ps(1);
        swap.swapBytes = true;
        PixelPack.packDepth(MemoryUtil.memAddress(s), 4, 1, 2, true, GL11.GL_UNSIGNED_SHORT, swap, MemoryUtil.memAddress(d));
        assertArrayEquals(b(0xFF, 0xFF, 0x00, 0x00), read(d, 4));
    }

    private static byte[] stencil(int type, int outBytes) {
        final ByteBuffer s = direct(b(0, 1, 0x80, 0xFF), 4);
        final ByteBuffer d = direct(new byte[0], outBytes);
        PixelPack.packStencil(MemoryUtil.memAddress(s), 4, 4, 1, false, type, ps(4), MemoryUtil.memAddress(d));
        return read(d, outBytes);
    }

    @Test
    void packStencilTypes() {
        assertArrayEquals(b(0, 1, 0x80, 0xFF), stencil(GL11.GL_UNSIGNED_BYTE, 4));
        assertArrayEquals(b(0, 1, 0x00, 0x7F), stencil(GL11.GL_BYTE, 4));
        assertArrayEquals(le16(0, 1, 128, 255), stencil(GL11.GL_UNSIGNED_SHORT, 8));
        assertArrayEquals(le16(0, 1, 128, 255), stencil(GL11.GL_SHORT, 8));
        assertArrayEquals(le32(0, 1, 128, 255), stencil(GL11.GL_UNSIGNED_INT, 16));
        assertArrayEquals(le32(0, 1, 128, 255), stencil(GL11.GL_INT, 16));
        assertArrayEquals(f32(0.0f, 1.0f, 128.0f, 255.0f), stencil(GL11.GL_FLOAT, 16));
        assertArrayEquals(le16(0x0000, 0x3C00, 0x5800, 0x5BF8), stencil(GL30.GL_HALF_FLOAT, 8));
    }

    @Test
    void packStencilFlipRows() {
        final ByteBuffer s = direct(b(1, 2, 3, 4), 4);
        final ByteBuffer d = direct(new byte[0], 8);
        PixelPack.packStencil(MemoryUtil.memAddress(s), 2, 2, 2, true, GL11.GL_UNSIGNED_BYTE, ps(4), MemoryUtil.memAddress(d));
        assertArrayEquals(b(3, 4, SENTINEL, SENTINEL, 1, 2, SENTINEL, SENTINEL), read(d, 8));
    }

    private static byte[] depthStencil(int type, int outBytes, PackState ps) {
        final ByteBuffer z = direct(f32(1.0f, 0.5f), 8);
        final ByteBuffer st = direct(b(0xFF, 0x12), 2);
        final ByteBuffer d = direct(new byte[0], outBytes);
        PixelPack.packDepthStencil(MemoryUtil.memAddress(z), MemoryUtil.memAddress(st), 8, 2, 2, 1, false, type, ps, MemoryUtil.memAddress(d));
        return read(d, outBytes);
    }

    @Test
    void packDepthStencilTypes() {
        assertArrayEquals(le32(0xFFFFFFFF, 0x7FFFFF12), depthStencil(GL30.GL_UNSIGNED_INT_24_8, 8, ps(4)));
        final byte[] f = new byte[16];
        System.arraycopy(f32(1.0f), 0, f, 0, 4);
        System.arraycopy(le32(0xFF), 0, f, 4, 4);
        System.arraycopy(f32(0.5f), 0, f, 8, 4);
        System.arraycopy(le32(0x12), 0, f, 12, 4);
        assertArrayEquals(f, depthStencil(GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, 16, ps(4)));
        final PackState swap = ps(4);
        swap.swapBytes = true;
        assertArrayEquals(b(0xFF, 0xFF, 0xFF, 0xFF, 0x7F, 0xFF, 0xFF, 0x12), depthStencil(GL30.GL_UNSIGNED_INT_24_8, 8, swap));
    }

    @Test
    void validateRejectsBadCombinations() {
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL11.GL_RGBA, GL12.GL_UNSIGNED_SHORT_5_6_5));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL12.GL_BGR, GL12.GL_UNSIGNED_SHORT_5_6_5));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL11.GL_RGBA, GL30.GL_UNSIGNED_INT_10F_11F_11F_REV));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL30.GL_RGB_INTEGER, GL11.GL_FLOAT));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL30.GL_DEPTH_STENCIL, GL11.GL_UNSIGNED_INT));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL11.GL_DEPTH_COMPONENT, GL30.GL_UNSIGNED_INT_24_8));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL11.GL_RGBA, GL11.GL_DOUBLE));
        assertThrows(IllegalArgumentException.class, () -> PixelPack.validate(GL11.GL_RGBA, GL30.GL_UNSIGNED_INT_5_9_9_9_REV));
        assertDoesNotThrow(() -> PixelPack.validate(GL30.GL_RGBA_INTEGER, GL12.GL_UNSIGNED_INT_2_10_10_10_REV));
        assertDoesNotThrow(() -> PixelPack.validate(GL11.GL_STENCIL_INDEX, GL11.GL_FLOAT));
        assertDoesNotThrow(() -> PixelPack.validate(GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8));
        assertDoesNotThrow(() -> PixelPack.validate(GL11.GL_RGB, GL30.GL_UNSIGNED_INT_10F_11F_11F_REV));
    }

    @Test
    void packColorRejectsIntegerMismatchAndUnknownSources() {
        assertThrows(IllegalArgumentException.class, () -> readPix(SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, f32(0, 0, 0, 0), GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_BYTE));
        assertThrows(IllegalArgumentException.class, () -> readPix(SDL_GPU_TEXTUREFORMAT_R8_UINT, b(1), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertThrows(IllegalArgumentException.class, () -> readPix(SDL_GPU_TEXTUREFORMAT_BC1_RGBA_UNORM, b(0, 0, 0, 0), GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE));
        assertThrows(IllegalArgumentException.class, () -> readPix(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, b(0, 0, 0, 0), GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT));
    }

    @Test
    void formatClassifiers() {
        assertEquals(true, PixelPack.isDepthFormat(GL11.GL_DEPTH_COMPONENT));
        assertEquals(true, PixelPack.isStencilFormat(GL11.GL_STENCIL_INDEX));
        assertEquals(true, PixelPack.isDepthStencilFormat(GL30.GL_DEPTH_STENCIL));
        assertEquals(false, PixelPack.isDepthFormat(GL11.GL_RGBA));
    }
}
