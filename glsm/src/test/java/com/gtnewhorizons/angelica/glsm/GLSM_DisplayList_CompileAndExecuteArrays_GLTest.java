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
        final ViewportState vp = GLSMRasterizer.getViewportState();
        savedX = vp.x;
        savedY = vp.y;
        savedW = vp.width;
        savedH = vp.height;

        GLSMPrograms.glUseProgram(0);

        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        final ByteBuffer verts = ByteBuffer.allocateDirect(3 * 12).order(ByteOrder.nativeOrder());
        verts.putFloat(0f).putFloat(0.5f).putFloat(0f).putFloat(W).putFloat(0.5f).putFloat(0f).flip();
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
        GLSMVertexInput.glVertexAttribPointer(POSITION_LOC, 3, GL11.GL_FLOAT, false, 12, 0L);
        GLSMVertexInput.glEnableVertexAttribArray(POSITION_LOC);

        GLSMRasterizer.glViewport(0, 0, W, 1);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(0, W, 0, 1, -1, 1);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glLoadIdentity();
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        GLSMCurrentAttribs.glColor4f(1f, 1f, 1f, 1f);
    }

    @AfterEach
    void tearDown() {
        if (list > 0) GLSMDraws.glDeleteLists(list, 1);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPopMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPopMatrix();
        GLSMRasterizer.glViewport(savedX, savedY, savedW, savedH);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (vbo != 0) GLSMBuffers.glDeleteBuffers(vbo);
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    private void clear() {
        GLSMBlend.glClearColor(0f, 0f, 0f, 1f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private boolean[] readCoverage() {
        pixels.clear();
        GLSMFramebuffer.glReadPixels(0, 0, W, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
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
        list = GLSMDraws.glGenLists(1);
        GLSMDraws.glNewList(list, mode);
        GLSMMatrices.glPushMatrix();
        GLSMMatrices.glScalef(0.5f, 1f, 1f);
        GLSMDraws.glDrawArrays(GL11.GL_LINES, 0, 2);
        GLSMMatrices.glPopMatrix();
        GLSMDraws.glEndList();
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
