package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ReadbackFixture {

    public static final int SIZE = 4;

    private ReadbackFixture() {}

    public static int colorFbo(int internalFormat, int format, int type) {
        return colorFbo(internalFormat, format, type, null);
    }

    public static int colorFbo(int internalFormat, int format, int type, ByteBuffer data) {
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, SIZE, SIZE, 0, format, type, data);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        final int fbo = GLStateManager.glGenFramebuffers();
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GLStateManager.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        return fbo;
    }

    public static int depthStencilFbo() {
        final int fbo = colorFbo(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        final int rb = GLStateManager.glGenRenderbuffers();
        GLStateManager.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rb);
        GLStateManager.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, SIZE, SIZE);
        GLStateManager.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, rb);
        return fbo;
    }

    public static void release(int fbo) {
        GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLStateManager.glDeleteFramebuffers(fbo);
        resetPack();
    }

    public static void clearColor(float r, float g, float b, float a) {
        GLStateManager.glClearColor(r, g, b, a);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    public static void clearTopHalf(float r, float g, float b, float a) {
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, SIZE / 2, SIZE, SIZE / 2);
        clearColor(r, g, b, a);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void resetPack() {
        GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GLStateManager.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);
    }

    public static ByteBuffer read(int format, int type, int bytes) {
        final ByteBuffer out = BufferUtils.createByteBuffer(bytes);
        for (int i = 0; i < bytes; i++) out.put(i, (byte) 0x7E);
        GLStateManager.glReadPixels(0, 0, SIZE, SIZE, format, type, out);
        return out.order(ByteOrder.nativeOrder());
    }

    public static void assertBytes(ByteBuffer buf, int offset, int[] expected, String label) {
        for (int i = 0; i < expected.length; i++) {
            final int idx = i;
            assertEquals(expected[i], buf.get(offset + i) & 0xFF, () -> label + " byte " + idx);
        }
    }

    public static void assertFloats(ByteBuffer buf, int offset, float[] expected, String label) {
        for (int i = 0; i < expected.length; i++) {
            final int idx = i;
            assertEquals(expected[i], buf.getFloat(offset + i * 4), () -> label + " float " + idx);
        }
    }

    public static void assertFloatsNear(ByteBuffer buf, int offset, float[] expected, String label) {
        for (int i = 0; i < expected.length; i++) {
            final int idx = i;
            assertEquals(expected[i], buf.getFloat(offset + i * 4), 1f / 4096f, () -> label + " float " + idx);
        }
    }

    public static void assertShorts(ByteBuffer buf, int offset, int[] expected, String label) {
        for (int i = 0; i < expected.length; i++) {
            final int idx = i;
            assertEquals(expected[i], buf.getShort(offset + i * 2) & 0xFFFF, () -> label + " short " + idx);
        }
    }

    public static void rgba8() {
        final int fbo = colorFbo(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        clearColor(51 / 255f, 102 / 255f, 153 / 255f, 204 / 255f);
        clearTopHalf(1f, 0f, 0f, 1f);

        final ByteBuffer rgba = read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4);
        assertBytes(rgba, 0, new int[] { 51, 102, 153, 204 }, "RGBA8 as RGBA/UBYTE bottom row");
        assertBytes(rgba, (SIZE - 1) * SIZE * 4, new int[] { 255, 0, 0, 255 }, "RGBA8 as RGBA/UBYTE top row");

        assertBytes(read(GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 153, 102, 51, 204 }, "RGBA8 as BGRA/UBYTE");

        final ByteBuffer rev = read(GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, SIZE * SIZE * 4);
        assertEquals(0xCC996633, rev.getInt(0), "RGBA8 as RGBA/8_8_8_8_REV");

        final ByteBuffer f = read(GL11.GL_RGBA, GL11.GL_FLOAT, SIZE * SIZE * 16);
        assertFloatsNear(f, 0, new float[] { 51 / 255f, 102 / 255f, 153 / 255f, 204 / 255f }, "RGBA8 as RGBA/FLOAT");

        final ByteBuffer rgb = read(GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, SIZE * 12);
        assertBytes(rgb, 0, new int[] { 51, 102, 153, 51, 102, 153 }, "RGBA8 as RGB/UBYTE");
        assertBytes(rgb, 12 * (SIZE - 1), new int[] { 255, 0, 0 }, "RGBA8 as RGB/UBYTE top row at 4-aligned stride");

        assertBytes(read(GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE), 0, new int[] { 51, 51, 51, 51 }, "RGBA8 as RED/UBYTE");

        release(fbo);
    }

    public static void packState() {
        final int fbo = colorFbo(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        clearColor(51 / 255f, 102 / 255f, 153 / 255f, 204 / 255f);
        clearTopHalf(1f, 0f, 0f, 1f);

        GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        final ByteBuffer rgb = read(GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 3);
        assertBytes(rgb, (SIZE - 1) * SIZE * 3, new int[] { 255, 0, 0 }, "PACK_ALIGNMENT 1 tightly packs RGB rows");

        GLStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        GLStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 6);
        GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 1);
        GLStateManager.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 2);
        final int rowBytes = 6 * 4;
        final ByteBuffer skipped = read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rowBytes * (SIZE + 2));
        assertBytes(skipped, 0, new int[] { 0x7E, 0x7E, 0x7E, 0x7E }, "skipped rows untouched");
        assertBytes(skipped, 2 * rowBytes, new int[] { 0x7E, 0x7E, 0x7E, 0x7E }, "skipped pixel untouched");
        assertBytes(skipped, 2 * rowBytes + 4, new int[] { 51, 102, 153, 204 }, "first pixel after skips");
        assertBytes(skipped, (2 + SIZE - 1) * rowBytes + 4, new int[] { 255, 0, 0, 255 }, "top row at ROW_LENGTH stride");
        assertBytes(skipped, 2 * rowBytes + 4 + SIZE * 4, new int[] { 0x7E, 0x7E, 0x7E, 0x7E }, "row padding untouched");
        resetPack();

        GLStateManager.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_TRUE);
        final ByteBuffer swapped = read(GL11.GL_RGBA, GL30.GL_HALF_FLOAT, SIZE * SIZE * 8);
        assertBytes(swapped, 0, new int[] { 0x32, 0x66 }, "SWAP_BYTES on HALF_FLOAT");
        resetPack();

        release(fbo);
    }

    public static void pixelPackBuffer() {
        final int fbo = colorFbo(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        clearColor(51 / 255f, 102 / 255f, 153 / 255f, 204 / 255f);
        final int pbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        GLStateManager.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 16 + SIZE * SIZE * 4, GL15.GL_STREAM_READ);
        GLStateManager.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 16L);
        final ByteBuffer mapped = GLStateManager.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL15.GL_READ_ONLY);
        assertTrue(mapped != null, "PBO map");
        assertBytes(mapped, 16, new int[] { 51, 102, 153, 204 }, "PBO readPixels at offset 16");
        GLStateManager.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        GLStateManager.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GLStateManager.glDeleteBuffers(pbo);
        release(fbo);
    }

    public static void rgb8() {
        final int fbo = colorFbo(GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        clearColor(51 / 255f, 102 / 255f, 153 / 255f, 0.5f);
        assertBytes(read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 51, 102, 153, 255 }, "RGB8 as RGBA reads alpha 1");
        release(fbo);
    }

    public static void rgba16f() {
        final int fbo = colorFbo(GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT);
        clearColor(0.25f, 0.625f, 0.75f, 2.0f);
        assertFloats(read(GL11.GL_RGBA, GL11.GL_FLOAT, SIZE * SIZE * 16), 0, new float[] { 0.25f, 0.625f, 0.75f, 2.0f }, "RGBA16F as RGBA/FLOAT keeps >1");
        assertShorts(read(GL11.GL_RGBA, GL30.GL_HALF_FLOAT, SIZE * SIZE * 8), 0, new int[] { 0x3400, 0x3900, 0x3A00, 0x4000 }, "RGBA16F as RGBA/HALF_FLOAT");
        assertBytes(read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 64, 159, 191, 255 }, "RGBA16F as RGBA/UBYTE clamps");
        release(fbo);
    }

    public static void rgba32f() {
        final int fbo = colorFbo(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT);
        clearColor(0.2f, -0.5f, 3.0f, 1.0f);
        assertFloats(read(GL11.GL_RGBA, GL11.GL_FLOAT, SIZE * SIZE * 16), 0, new float[] { 0.2f, -0.5f, 3.0f, 1.0f }, "RGBA32F as RGBA/FLOAT");
        assertBytes(read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 51, 0, 255, 255 }, "RGBA32F as RGBA/UBYTE clamps both ends");
        release(fbo);
    }

    public static void r8() {
        final int fbo = colorFbo(GL30.GL_R8, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE);
        clearColor(0.2f, 0.9f, 0.9f, 0.9f);
        assertBytes(read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 51, 0, 0, 255 }, "R8 as RGBA rebases G/B=0 A=1");
        release(fbo);
    }

    public static void rg16f() {
        final int fbo = colorFbo(GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT);
        clearColor(0.25f, 0.625f, 0.9f, 0.9f);
        assertFloats(read(GL11.GL_RGBA, GL11.GL_FLOAT, SIZE * SIZE * 16), 0, new float[] { 0.25f, 0.625f, 0f, 1f }, "RG16F as RGBA/FLOAT");
        release(fbo);
    }

    public static void rgb10a2() {
        final int texel = (1 << 30) | (614 << 20) | (409 << 10) | 205;
        final ByteBuffer data = BufferUtils.createByteBuffer(SIZE * SIZE * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < SIZE * SIZE; i++) data.putInt(i * 4, texel);
        final int fbo = colorFbo(GL11.GL_RGB10_A2, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, data);
        final ByteBuffer raw = read(GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, SIZE * SIZE * 4);
        assertEquals(texel, raw.getInt(0), "RGB10A2 as RGBA/2_10_10_10_REV");
        assertBytes(read(GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE * 4), 0, new int[] { 51, 102, 153, 85 }, "RGB10A2 as RGBA/UBYTE");
        release(fbo);
    }

    public static void r11g11b10f() {
        final int fbo = colorFbo(GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT);
        clearColor(0.25f, 0.625f, 1.5f, 1f);
        assertFloats(read(GL11.GL_RGBA, GL11.GL_FLOAT, SIZE * SIZE * 16), 0, new float[] { 0.25f, 0.625f, 1.5f, 1f }, "R11G11B10F as RGBA/FLOAT");
        release(fbo);
    }

    public static void depthStencil() {
        final int fbo = depthStencilFbo();
        GLStateManager.glDepthMask(true);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClearDepth(0.25);
        GLStateManager.glClearStencil(0xC3);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glScissor(0, SIZE / 2, SIZE, SIZE / 2);
        GLStateManager.glClearDepth(0.75);
        GLStateManager.glClearStencil(0x5A);
        GLStateManager.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
        GLStateManager.glClearDepth(1.0);
        GLStateManager.glClearStencil(0);

        final ByteBuffer depth = read(GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, SIZE * SIZE * 4);
        assertEquals(0.25f, depth.getFloat(0), 1e-6f, "depth bottom row as FLOAT");
        assertEquals(0.75f, depth.getFloat((SIZE - 1) * SIZE * 4), 1e-6f, "depth top row as FLOAT");

        final ByteBuffer depthUs = read(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_SHORT, SIZE * SIZE * 2);
        assertEquals(16384, depthUs.getShort(0) & 0xFFFF, 1, "depth as UNSIGNED_SHORT");

        final ByteBuffer depthUi = read(GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, SIZE * SIZE * 4);
        assertEquals(0x40000000L >>> 8, (depthUi.getInt(0) & 0xFFFFFFFFL) >>> 8, 1, "depth as UNSIGNED_INT (top 24 bits)");

        final ByteBuffer stencil = read(GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, SIZE * SIZE);
        assertBytes(stencil, 0, new int[] { 0xC3, 0xC3, 0xC3, 0xC3 }, "stencil bottom row");
        assertBytes(stencil, (SIZE - 1) * SIZE, new int[] { 0x5A, 0x5A, 0x5A, 0x5A }, "stencil top row");

        final ByteBuffer ds = read(GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, SIZE * SIZE * 4);
        final int packed = ds.getInt(0);
        assertEquals(0xC3, packed & 0xFF, "DEPTH_STENCIL stencil byte");
        assertEquals(0x400000, (packed >>> 8) & 0xFFFFFF, 1, "DEPTH_STENCIL depth 24 bits");

        release(fbo);
    }
}
