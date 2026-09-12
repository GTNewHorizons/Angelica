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

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlReadbackTest {

    private static final int TEX_SIZE = 8;
    private static final int R = 0x12;
    private static final int G = 0x34;
    private static final int B = 0x56;
    private static final int A = 0xFF;

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @Test
    void getTexImageSeesDeferredTextureUpload() {
        final int texture = GLStateManager.glGenTextures();
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        final ByteBuffer src = solidTexture(TEX_SIZE, TEX_SIZE);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);

        final ByteBuffer dst = BufferUtils.createByteBuffer(TEX_SIZE * TEX_SIZE * 4);
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, dst);

        assertPixels(dst, TEX_SIZE * TEX_SIZE);
        GLStateManager.glDeleteTextures(texture);
    }

    @Test
    void readPixelsSeesPriorDraw() {
        GlsmSdlHeadlessRig.bindTarget();
        GlsmSdlHeadlessRig.clearTo(0.0f, 0.0f, 0.0f, 1.0f);
        GlsmSdlHeadlessRig.solidQuad(1.0f, 0.0f, 0.0f);

        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GLStateManager.glReadPixels(
            GlsmSdlHeadlessRig.SIZE / 2, GlsmSdlHeadlessRig.SIZE / 2, 1, 1,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);

        assertEquals(0xFF, pixel.get(0) & 0xFF);
        assertEquals(0x00, pixel.get(1) & 0xFF);
        assertEquals(0x00, pixel.get(2) & 0xFF);
        assertEquals(0xFF, pixel.get(3) & 0xFF);
    }

    private static ByteBuffer solidTexture(int width, int height) {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height; i++) {
            pixels.put((byte) R);
            pixels.put((byte) G);
            pixels.put((byte) B);
            pixels.put((byte) A);
        }
        return pixels.flip();
    }

    private static void assertPixels(ByteBuffer pixels, int count) {
        for (int i = 0; i < count; i++) {
            assertEquals(R, pixels.get(i * 4) & 0xFF, "red at pixel " + i);
            assertEquals(G, pixels.get(i * 4 + 1) & 0xFF, "green at pixel " + i);
            assertEquals(B, pixels.get(i * 4 + 2) & 0xFF, "blue at pixel " + i);
            assertEquals(A, pixels.get(i * 4 + 3) & 0xFF, "alpha at pixel " + i);
        }
    }
}
