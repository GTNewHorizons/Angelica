package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

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
        GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);
        GLSMRasterizer.glLineStipple(1, (short) 0xFFFF);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glPopMatrix();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPopMatrix();
        GLSMRasterizer.glViewport(savedX, savedY, savedW, savedH);
        GLSMVertexInput.glBindVertexArray(0);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        if (vbo != 0) GLSMBuffers.glDeleteBuffers(vbo);
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
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
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STREAM_DRAW);
        GLSMDraws.glDrawArrays(xs.length == 2 ? GL11.GL_LINES : GL11.GL_LINE_STRIP, 0, xs.length);
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

    private static boolean[] expected(IntPredicate on) {
        final boolean[] e = new boolean[W];
        for (int i = 0; i < W; i++) e[i] = on.test(i);
        return e;
    }

    private static void stipple(int factor, int pattern) {
        GLSMEnable.glEnable(GL11.GL_LINE_STIPPLE);
        GLSMRasterizer.glLineStipple(factor, (short) pattern);
    }

    static Stream<Arguments> patterns() {
        return Stream.of(
            pattern("solid pattern covers every pixel", true, 1, 0xFFFF, i -> true),
            pattern("empty pattern covers nothing", true, 1, 0x0000, i -> false),
            pattern("alternating pattern dashes every other pixel", true, 1, 0xAAAA, i -> (i & 1) == 1),
            pattern("factor stretches each bit across that many pixels", true, 2, 0xAAAA, i -> ((i / 2) & 1) == 1),
            pattern("disabled stipple covers every pixel", false, 1, 0xAAAA, i -> true));
    }

    private static Arguments pattern(String name, boolean enabled, int factor, int pattern, IntPredicate on) {
        return Arguments.of(name, enabled, factor, pattern, on);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("patterns")
    void patternSelectsTheCoveredPixels(String name, boolean enabled, int factor, int pattern, IntPredicate on) {
        GLSMRasterizer.glLineStipple(factor, (short) pattern);
        if (enabled) {
            GLSMEnable.glEnable(GL11.GL_LINE_STIPPLE);
        } else {
            GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);
        }
        assertArrayEquals(expected(on), renderLine(0f, W));
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

        GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);
        assertArrayEquals(expected(i -> true), renderLine(0f, W));
    }
}
