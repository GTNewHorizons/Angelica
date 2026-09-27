package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;

/**
 * Covers the storage-format-aware R/B channel handling in SDL-GPU readback / upload paths.
 */
class BgraStorageChannelTest {

    private static final int RGBA = SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM;
    private static final int BGRA = SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM;
    private static final int BGRA_SRGB = SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;

    private static ByteBuffer pixel(int c0, int c1, int c2, int c3) {
        final ByteBuffer b = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        b.put((byte) c0).put((byte) c1).put((byte) c2).put((byte) c3).rewind();
        return b;
    }

    private static byte[] toArray(ByteBuffer b, int len) {
        final byte[] out = new byte[len];
        final int pos = b.position();
        for (int i = 0; i < len; i++) out[i] = b.get(pos + i);
        return out;
    }

    @Test
    void isBgraSdlFormat_recognizesAllBgraVariants() {
        assertTrue(PixelOps.isBgraSdlFormat(BGRA));
        assertTrue(PixelOps.isBgraSdlFormat(BGRA_SRGB));
        assertFalse(PixelOps.isBgraSdlFormat(RGBA));
        assertFalse(PixelOps.isBgraSdlFormat(SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB));
    }

    @Test
    void swapRedBlueIfNeeded_noOpWhenFalse() {
        final ByteBuffer p = pixel(1, 2, 3, 4);
        PixelOps.swapRedBlueIfNeeded(p, 1, 1, false);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, toArray(p, 4));
    }

    @Test
    void swapRedBlueIfNeeded_swapsWhenTrue() {
        final ByteBuffer p = pixel(1, 2, 3, 4);
        PixelOps.swapRedBlueIfNeeded(p, 1, 1, true);
        assertArrayEquals(new byte[]{3, 2, 1, 4}, toArray(p, 4));
    }

    @Test
    void upload_rgbaSrc_rgbaStorage_passesThrough() {
        final ByteBuffer src = pixel(0x11, 0x22, 0x33, 0x44);
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, RGBA, src, 1, 1);
        assertSame(src, out, "no transform expected");
    }

    @Test
    void upload_bgraSrc_bgraStorage_passesThrough() {
        final ByteBuffer src = pixel(0x11, 0x22, 0x33, 0x44);
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, BGRA, src, 1, 1);
        assertSame(src, out);
    }

    @Test
    void upload_rgbaSrc_bgraStorage_swaps() {
        final ByteBuffer src = pixel(0x11, 0x22, 0x33, 0x44);
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BGRA, src, 1, 1);
        try {
            assertNotSame(src, out);
            assertArrayEquals(new byte[]{0x33, 0x22, 0x11, 0x44}, toArray(out, 4));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void upload_bgraSrc_rgbaStorage_swaps() {
        final ByteBuffer src = pixel(0x11, 0x22, 0x33, 0x44);
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, RGBA, src, 1, 1);
        try {
            assertNotSame(src, out);
            assertArrayEquals(new byte[]{0x33, 0x22, 0x11, 0x44}, toArray(out, 4));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void upload_luminanceSrc_rgbaStorage_expandsOnly() {
        final ByteBuffer src = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder());
        src.put((byte) 0x77).rewind();
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE, RGBA, src, 1, 1);
        try {
            assertNotSame(src, out);
            assertArrayEquals(new byte[]{0x77, 0x77, 0x77, (byte) 0xFF}, toArray(out, 4));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void upload_luminanceSrc_bgraStorage_expandsAndSwaps() {
        final ByteBuffer src = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder());
        src.put((byte) 0x77).rewind();
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE, BGRA, src, 1, 1);
        try {
            assertNotSame(src, out);
            assertArrayEquals(new byte[]{0x77, 0x77, 0x77, (byte) 0xFF}, toArray(out, 4));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void upload_luminanceAlphaSrc_bgraStorage_expandsAndSwaps() {
        final ByteBuffer src = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder());
        src.put((byte) 0x42).put((byte) 0x88).rewind();
        final ByteBuffer out = PixelOps.prepareUploadBuffer(GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE, BGRA, src, 1, 1);
        try {
            assertNotSame(src, out);
            assertArrayEquals(new byte[]{0x42, 0x42, 0x42, (byte) 0x88}, toArray(out, 4));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    @Test
    void swapRedBlueCopy_independentOfSourcePosition() {
        final ByteBuffer src = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
        src.put(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x11, 0x22, 0x33, 0x44}).position(4);
        final ByteBuffer out = PixelOps.swapRedBlueCopy(src, 1, 1);
        try {
            assertArrayEquals(new byte[]{0x33, 0x22, 0x11, 0x44}, toArray(out, 4));
            assertEquals(4, src.position(), "source position must be preserved");
        } finally {
            MemoryUtil.memFree(out);
        }
    }
}
