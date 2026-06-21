package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(GLSMCoreExtension.class)
public class GLSM_DisplayList_PixelStore_Test {

    private static final int SRC = 8;
    private static final int SUB = 4;

    @AfterEach
    void resetUnpackState() {
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        setUnpack(0, 0, 0);
    }

    private static void setUnpack(int rowLength, int skipRows, int skipPixels) {
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
    }

    private static void setStridedUnpack() {
        setUnpack(SRC, 1, 2);
    }

    /** RGBA source; red channel = y*size+x, alpha = 255. */
    private static ByteBuffer makeSource(int size) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < size * size; i++) {
            buf.put((byte) i).put((byte) 0).put((byte) 0).put((byte) 255);
        }
        buf.flip();
        return buf;
    }

    private static int makeTexture(int size, ByteBuffer pixels) {
        final int tex = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return tex;
    }

    private static ByteBuffer readback(int size) {
        final ByteBuffer out = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
        GLStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out);
        return out;
    }

    private static int red(ByteBuffer pixels, int size, int x, int y) {
        return pixels.get((y * size + x) * 4) & 0xFF;
    }

    private static void assertStridedSubImage(ByteBuffer pixels) {
        for (int y = 0; y < SUB; y++) {
            for (int x = 0; x < SUB; x++) {
                assertEquals((y + 1) * SRC + (x + 2), red(pixels, SUB, x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    private static void assertTightSubImage(ByteBuffer pixels) {
        for (int y = 0; y < SUB; y++) {
            for (int x = 0; x < SUB; x++) {
                assertEquals(y * SUB + x, red(pixels, SUB, x, y), "pixel (" + x + "," + y + ")");
            }
        }
    }

    private static void assertUnpack(int rowLength, int skipRows, int skipPixels) {
        assertEquals(rowLength, GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));
        assertEquals(skipRows, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));
        assertEquals(skipPixels, GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS));
    }

    @Test
    void pixelStoreExecutesImmediatelyAndIsNotRecorded() {
        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 7);
        assertEquals(7, GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), "must execute immediately during recording");
        GLStateManager.glEndList();
        assertEquals(7, GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), "must persist after glEndList");

        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GLStateManager.glCallList(list);
        assertEquals(0, GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), "must not have been recorded into the list");

        GLStateManager.glDeleteLists(list, 1);
    }

    @Test
    void stridedTexSubImage2DCompile() {
        final int tex = makeTexture(SUB, ByteBuffer.allocateDirect(SUB * SUB * 4).order(ByteOrder.nativeOrder()));
        final ByteBuffer src = makeSource(SRC);

        setStridedUnpack();

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SUB, SUB, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);
        GLStateManager.glEndList();

        assertEquals(0, red(readback(SUB), SUB, 0, 0), "GL_COMPILE must not execute the upload");

        setUnpack(0, 0, 0);

        GLStateManager.glCallList(list);
        assertStridedSubImage(readback(SUB));
        assertUnpack(0, 0, 0);

        GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glDeleteTextures(tex);
    }

    @Test
    void stridedTexSubImage2DCompileAndExecute() {
        final ByteBuffer zeros = ByteBuffer.allocateDirect(SUB * SUB * 4).order(ByteOrder.nativeOrder());
        final int tex = makeTexture(SUB, zeros);
        final ByteBuffer src = makeSource(SRC);

        setStridedUnpack();

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE_AND_EXECUTE);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SUB, SUB, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);
        GLStateManager.glEndList();

        assertStridedSubImage(readback(SUB));

        setUnpack(0, 0, 0);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SUB, SUB, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zeros);

        GLStateManager.glCallList(list);
        assertStridedSubImage(readback(SUB));
        assertUnpack(0, 0, 0);

        GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glDeleteTextures(tex);
    }

    @Test
    void stridedTexImage2DCompile() {
        final int tex = makeTexture(SUB, ByteBuffer.allocateDirect(SUB * SUB * 4).order(ByteOrder.nativeOrder()));
        final ByteBuffer src = makeSource(SRC);

        setStridedUnpack();

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SUB, SUB, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);
        GLStateManager.glEndList();

        setUnpack(0, 0, 0);

        GLStateManager.glCallList(list);
        assertStridedSubImage(readback(SUB));
        assertUnpack(0, 0, 0);

        GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glDeleteTextures(tex);
    }

    @Test
    void defaultRecordedTexSubImage2DReplaysWithDefaults() {
        final int tex = makeTexture(SUB, ByteBuffer.allocateDirect(SUB * SUB * 4).order(ByteOrder.nativeOrder()));
        final ByteBuffer src = makeSource(SUB);

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SUB, SUB, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);
        GLStateManager.glEndList();

        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        setStridedUnpack();

        GLStateManager.glCallList(list);
        assertTightSubImage(readback(SUB));
        assertEquals(1, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT), "live state must survive replay");
        assertUnpack(SRC, 1, 2);

        GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glDeleteTextures(tex);
    }

    @Test
    void defaultRecordedTexImage2DReplaysWithDefaults() {
        final int tex = makeTexture(SUB, ByteBuffer.allocateDirect(SUB * SUB * 4).order(ByteOrder.nativeOrder()));
        final ByteBuffer src = makeSource(SUB);

        final int list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, GL11.GL_COMPILE);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SUB, SUB, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, src);
        GLStateManager.glEndList();

        GLStateManager.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        setStridedUnpack();

        GLStateManager.glCallList(list);
        assertTightSubImage(readback(SUB));
        assertEquals(1, GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT), "live state must survive replay");
        assertUnpack(SRC, 1, 2);

        GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glDeleteTextures(tex);
    }
}
