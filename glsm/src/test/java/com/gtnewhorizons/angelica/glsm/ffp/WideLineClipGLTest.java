package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class WideLineClipGLTest {

    private static final int SIZE = 64;
    private static final double NEAR = 0.05;
    private static final float LINE_WIDTH = 3.0f;
    private static final int BACKGROUND = 0xFF000000;

    private static final float[] IN_FRONT = { 0f, -1f, -2f };
    private static final float[] BEHIND = { 0f, 0.8f, 2f };

    private boolean savedEmulation;
    private int vao;
    private int vbo;

    @BeforeEach
    void setUp() {
        assumeTrue(GLStateManager.supportsGeometryShaders());
        savedEmulation = GLStateManager.wideLineEmulationEnabled;
        GLStateManager.wideLineEmulationEnabled = true;

        FfpFixture.resetFfpState();
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glFrustum(-NEAR, NEAR, -NEAR, NEAR, NEAR, 100.0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glLineWidth(LINE_WIDTH);
    }

    @AfterEach
    void tearDown() {
        GLStateManager.wideLineEmulationEnabled = savedEmulation;
        GLStateManager.glLineWidth(1.0f);
        if (vbo != 0) { GLStateManager.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GLStateManager.glDeleteVertexArrays(vao); vao = 0; }
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        FfpFixture.resetFfpState();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
    }

    @Test
    void lineEndingBehindTheCameraKeepsItsWidth() {
        assertConstantWidth(IN_FRONT, BEHIND);
    }

    @Test
    void lineStartingBehindTheCameraKeepsItsWidth() {
        assertConstantWidth(BEHIND, IN_FRONT);
    }

    @Test
    void lineFullyBehindTheCameraDrawsNothing() {
        final int[] pixels = drawLine(new float[] { 0f, -1f, 2f }, BEHIND);
        for (int px : pixels) {
            assertEquals(BACKGROUND, px);
        }
    }

    private static void orthographic() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static float pixelCenter(int pixel) {
        return (pixel + 0.5f) * 2.0f / SIZE - 1.0f;
    }

    private static int litInColumn(int[] pixels, int x) {
        int lit = 0;
        for (int row = 0; row < SIZE; row++) {
            if (pixels[row * SIZE + x] != BACKGROUND) lit++;
        }
        return lit;
    }

    private int[] drawLineOnce(float[] start, float[] end) {
        final int[] pixels = drawLine(start, end);
        GLStateManager.glDeleteBuffers(vbo);
        GLStateManager.glDeleteVertexArrays(vao);
        vbo = 0;
        vao = 0;
        return pixels;
    }

    @Test
    void wideLineCoversItsFirstPixelButNotItsLast() {
        orthographic();
        GLStateManager.glLineWidth(2.0f);
        final float y = pixelCenter(16);
        final int[] pixels = drawLineOnce(new float[] { pixelCenter(40), y, 0f }, new float[] { pixelCenter(20), y, 0f });
        assertEquals(2, litInColumn(pixels, 40), "first pixel of a right-to-left line");
        assertEquals(0, litInColumn(pixels, 20), "last pixel of a right-to-left line");
        assertEquals(2, litInColumn(pixels, 21), "pixel next to the last");
    }

    @Test
    void cullingKeepsWideLinesInBothDirections() {
        orthographic();
        GLStateManager.glLineWidth(2.0f);
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        try {
            final int[] rightward = drawLineOnce(new float[] { -0.8f, 0f, 0f }, new float[] { 0.8f, 0f, 0f });
            final int[] leftward = drawLineOnce(new float[] { 0.8f, 0f, 0f }, new float[] { -0.8f, 0f, 0f });
            assertEquals(2, litInColumn(rightward, SIZE / 2), "left-to-right line with culling on");
            assertEquals(2, litInColumn(leftward, SIZE / 2), "right-to-left line with culling on");
            assertTrue(GLStateManager.getCullState().isEnabled(), "the game's cull state survives");
        } finally {
            GLStateManager.glDisable(GL11.GL_CULL_FACE);
        }
    }

    private void assertConstantWidth(float[] start, float[] end) {
        final int[] pixels = drawLine(start, end);
        for (int row = 0; row < SIZE / 4; row++) {
            int lit = 0;
            for (int x = 0; x < SIZE; x++) {
                if (pixels[row * SIZE + x] != BACKGROUND) lit++;
            }
            final int y = row, count = lit;
            assertTrue(count > 0 && count <= LINE_WIDTH + 1, () -> "row " + y + " is " + count + " pixels wide");
        }
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    private int[] drawLine(float[] start, float[] end) {
        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        final FloatBuffer positions = BufferUtils.createFloatBuffer(6);
        positions.put(start).put(end).flip();
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
        FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        VAOManager.setCurrentVertexFlags(0);

        FfpFixture.clear();
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glDrawArrays(GL11.GL_LINES, 0, 2);
        GLStateManager.glBindVertexArray(0);
        return FfpFixture.readRegion(SIZE);
    }
}
