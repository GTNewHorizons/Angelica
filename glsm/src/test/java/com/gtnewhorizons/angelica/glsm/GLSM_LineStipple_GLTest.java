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
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
public class GLSM_LineStipple_GLTest {

    private static final int W = 32;

    private static final int POSITION_LOC = VertexFormatElement.Usage.POSITION.getAttributeLocation();

    private final ByteBuffer pixels = BufferUtils.createByteBuffer(W * 4);
    private final ByteBuffer verts = ByteBuffer.allocateDirect(3 * 12).order(ByteOrder.nativeOrder());
    private int savedX, savedY, savedW, savedH;
    private int vao, vbo;

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
        GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
        GLStateManager.glLineStipple(1, (short) 0xFFFF);
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

    private boolean[] renderLine(float x0, float x1) {
        clear();
        drawLines(x0, x1);
        return readCoverage();
    }

    private void drawLines(float... xs) {
        verts.clear();
        for (float x : xs) verts.putFloat(x).putFloat(0.5f).putFloat(0f);
        verts.flip();
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STREAM_DRAW);
        GLStateManager.glDrawArrays(xs.length == 2 ? GL11.GL_LINES : GL11.GL_LINE_STRIP, 0, xs.length);
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

    private static boolean[] expected(IntPredicate on) {
        final boolean[] e = new boolean[W];
        for (int i = 0; i < W; i++) e[i] = on.test(i);
        return e;
    }

    private static void stipple(int factor, int pattern) {
        GLStateManager.glEnable(GL11.GL_LINE_STIPPLE);
        GLStateManager.glLineStipple(factor, (short) pattern);
    }

    @Test
    void solidPatternCoversEveryPixel() {
        stipple(1, 0xFFFF);
        assertArrayEquals(expected(i -> true), renderLine(0f, W));
    }

    @Test
    void emptyPatternCoversNothing() {
        stipple(1, 0x0000);
        assertArrayEquals(expected(i -> false), renderLine(0f, W));
    }

    @Test
    void alternatingPatternDashesEveryOtherPixel() {
        stipple(1, 0xAAAA);
        assertArrayEquals(expected(i -> (i & 1) == 1), renderLine(0f, W));
    }

    @Test
    void factorStretchesEachBitAcrossThatManyPixels() {
        stipple(2, 0xAAAA);
        assertArrayEquals(expected(i -> ((i / 2) & 1) == 1), renderLine(0f, W));
    }

    @Test
    void disabledStippleCoversEveryPixel() {
        GLStateManager.glLineStipple(1, (short) 0xAAAA);
        GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
        assertArrayEquals(expected(i -> true), renderLine(0f, W));
    }

    @Test
    void patternIsAnchoredAtTheSegmentStart() {
        stipple(1, 0xAAAA);
        final boolean[] covered = renderLine(8f, W);
        for (int i = 8; i < W; i++) {
            assertEquals(((i - 8) & 1) == 1, covered[i], "pixel " + i + " must be phased from x=8, not x=0");
        }
    }

    @Test
    void lineStripRestartsThePatternPerSegment() {
        stipple(1, 0xAAAA);

        clear();
        drawLines(0f, 9f, W);

        final boolean[] covered = readCoverage();
        for (int i = 0; i < 9; i++) {
            assertEquals((i & 1) == 1, covered[i], "segment 1 pixel " + i);
        }
        for (int i = 10; i < W; i++) {
            assertEquals(((i - 9) & 1) == 1, covered[i], "segment 2 pixel " + i + " must restart the phase at x=9");
        }
    }

    @Test
    void nonStippleDrawAfterStippleIsUnaffected() {
        stipple(1, 0xAAAA);
        renderLine(0f, W);

        GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
        assertArrayEquals(expected(i -> true), renderLine(0f, W));
    }
}
