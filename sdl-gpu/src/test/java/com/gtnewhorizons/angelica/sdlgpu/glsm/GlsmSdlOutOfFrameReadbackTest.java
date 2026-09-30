package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.fail;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlOutOfFrameExtension.class)
class GlsmSdlOutOfFrameReadbackTest {

    private static final int TEX_SIZE = 64;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void outOfFrameTexImageReadback() {
        final ByteBuffer pattern = pattern(TEX_SIZE, TEX_SIZE, 1);
        final int tex = GlsmSdlHeadlessRig.newNearestTexture();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pattern);
        assertReadback(tex, TEX_SIZE, TEX_SIZE, pattern);
        GLStateManager.glDeleteTextures(tex);
    }

    @Test
    void inFrameCopyImageSubDataReadback() {
        GlsmSdlHeadlessRig.beginFrame();
        copyAndReadBack(3);
    }

    @Test
    void outOfFrameCopyImageSubDataReadback() {
        copyAndReadBack(4);
    }

    private static void copyAndReadBack(int seed) {
        final ByteBuffer pattern = pattern(TEX_SIZE, TEX_SIZE, seed);
        final int src = GlsmSdlHeadlessRig.newNearestTexture();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pattern);
        final int dst = GlsmSdlHeadlessRig.newNearestTexture();
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLStateManager.glCopyImageSubData(src, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, dst, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, TEX_SIZE, TEX_SIZE, 1);
        assertReadback(dst, TEX_SIZE, TEX_SIZE, pattern);
        GLStateManager.glDeleteTextures(src);
        GLStateManager.glDeleteTextures(dst);
    }

    private static ByteBuffer pattern(int w, int h, int seed) {
        final ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            buf.put((byte) (i * 7 + seed));
            buf.put((byte) (i * 13 + 0x40));
            buf.put((byte) (i * 29 + seed * 3));
            buf.put((byte) (0x80 | i));
        }
        return buf.flip();
    }

    private static void assertReadback(int tex, int w, int h, ByteBuffer expected) {
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        final ByteBuffer got = BufferUtils.createByteBuffer(w * h * 4);
        for (int i = 0; i < got.capacity(); i++) got.put(i, (byte) 0xCD);
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, got);

        final int n = w * h * 4;
        int mismatches = 0;
        int zeros = 0;
        int untouched = 0;
        int first = -1;
        for (int i = 0; i < n; i++) {
            final int g = got.get(i) & 0xFF;
            if (g == 0) zeros++;
            if (g == 0xCD) untouched++;
            if (g != (expected.get(i) & 0xFF)) {
                mismatches++;
                if (first < 0) first = i;
            }
        }
        if (mismatches == 0) return;
        fail("readback mismatch: " + mismatches + "/" + n + " bytes differ, zeroBytes=" + zeros + " untouched0xCD=" + untouched + ", firstMismatch=" + first + "\n expected[0..16]=" + hex(expected, 16) + "\n actual[0..16]  =" + hex(got, 16));
    }

    private static String hex(ByteBuffer buf, int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(String.format(" %02x", buf.get(i) & 0xFF));
        return sb.toString();
    }
}
