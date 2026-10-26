package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The executed half of a GL_COMPILE_AND_EXECUTE glDrawArrays sees transforms issued since the last matrix barrier. */
@GLCoreTest
public class GLSM_DisplayList_CompileAndExecuteArrays_GLTest {

    private static final int W = 32;

    private static final int POSITION_LOC = VertexFormatElement.Usage.POSITION.getAttributeLocation();

    private final ByteBuffer pixels = BufferUtils.createByteBuffer(W * 4);
    private int savedX, savedY, savedW, savedH;
    private int vao, vbo, list;

    @BeforeEach
    void setUp() {
        final ViewportState vp = GLStateManager.getViewportState();
        savedX = vp.x;
        savedY = vp.y;
        savedW = vp.width;
        savedH = vp.height;

        GLStateManager.glUseProgram(0);

        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        final ByteBuffer verts = ByteBuffer.allocateDirect(3 * 12).order(ByteOrder.nativeOrder());
        verts.putFloat(0f).putFloat(0.5f).putFloat(0f).putFloat(W).putFloat(0.5f).putFloat(0f).flip();
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
        GLStateManager.glVertexAttribPointer(POSITION_LOC, 3, GL11.GL_FLOAT, false, 12, 0L);
        GLStateManager.glEnableVertexAttribArray(POSITION_LOC);

        GLStateManager.glViewport(0, 0, W, 1);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0, W, 0, 1, -1, 1);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glDisable(GL11.GL_BLEND);
        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glColor4f(1f, 1f, 1f, 1f);
    }

    @AfterEach
    void tearDown() {
        if (list > 0) GLStateManager.glDeleteLists(list, 1);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glPopMatrix();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glPopMatrix();
        GLStateManager.glViewport(savedX, savedY, savedW, savedH);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (vbo != 0) GLStateManager.glDeleteBuffers(vbo);
        if (vao != 0) GLStateManager.glDeleteVertexArrays(vao);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private void clear() {
        GLStateManager.glClearColor(0f, 0f, 0f, 1f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private boolean[] readCoverage() {
        pixels.clear();
        GLStateManager.glReadPixels(0, 0, W, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GL error during readback");

        final boolean[] covered = new boolean[W];
        for (int i = 0; i < W; i++) {
            covered[i] = (pixels.get(i * 4) & 0xFF) > 127;
        }
        return covered;
    }

    private static boolean[] leftHalf() {
        final boolean[] e = new boolean[W];
        for (int i = 0; i < W / 2; i++) e[i] = true;
        return e;
    }

    /** The scale is issued inside the list, after the push that flushed everything before it - AE2 monitor's shape. */
    private void compileScaledDraw(int mode) {
        list = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(list, mode);
        GLStateManager.glPushMatrix();
        GLStateManager.glScalef(0.5f, 1f, 1f);
        GLStateManager.glDrawArrays(GL11.GL_LINES, 0, 2);
        GLStateManager.glPopMatrix();
        GLStateManager.glEndList();
    }

    // Replay is not asserted here: a compiled list plays back through FFP client arrays, which this core context lacks,
    // and in the compat context this attribute setup draws nothing at all.
    @Test
    void compileAndExecuteDrawsWithPendingScale() {
        clear();
        compileScaledDraw(GL11.GL_COMPILE_AND_EXECUTE);
        assertArrayEquals(leftHalf(), readCoverage(), "executed half of GL_COMPILE_AND_EXECUTE");
    }
}
