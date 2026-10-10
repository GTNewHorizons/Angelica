package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.SIZE;
import static com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlHeadlessRig.pixelAt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("glsm-sdl")
@ExtendWith(GlsmSdlFrameExtension.class)
class GlsmSdlWideLineTest {

    @BeforeAll
    static void boot() {
        GlsmSdlHeadlessRig.boot();
    }

    @AfterEach
    void resetLineState() {
        GLStateManager.glLineWidth(1.0f);
        GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void line(float[] start, float[] end) {
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glBegin(GL11.GL_LINES);
        GLStateManager.glVertex3f(start[0], start[1], start[2]);
        GLStateManager.glVertex3f(end[0], end[1], end[2]);
        GLStateManager.glEnd();
    }

    private static int litInRow(int[] pixels, int y) {
        int lit = 0;
        for (int x = 0; x < SIZE; x++) {
            if ((pixelAt(pixels, SIZE, x, y) & 0x00FFFFFF) != 0) lit++;
        }
        return lit;
    }

    @Test
    void stippledWideLineKeepsOnePhaseAcrossItsWidth() {
        GLStateManager.glLineWidth(3.0f);
        GLStateManager.glEnable(GL11.GL_LINE_STIPPLE);
        GLStateManager.glLineStipple(2, (short) 0x0F0F);
        final float y = pixelCenter(32);
        line(new float[] { pixelCenter(8), y, 0f }, new float[] { pixelCenter(56), y, 0f });

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        int fullColumns = 0;
        int emptyColumns = 0;
        for (int x = 9; x < 55; x++) {
            final int lit = litInColumn(pixels, x);
            final int column = x;
            assertTrue(lit == 0 || lit == 3, () -> "column " + column + " has " + lit + " of 3 rows lit");
            if (lit == 3) fullColumns++; else emptyColumns++;
        }
        assertTrue(fullColumns > 0 && emptyColumns > 0, "stipple pattern visible");
    }

    private static final float[] IN_FRONT = { 0f, -0.9875f, -2f };
    private static final float[] BEHIND = { 0f, 0.8f, 2f };

    private static void nearPlaneScene() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glFrustum(-0.05, 0.05, -0.05, 0.05, 0.05, 100.0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLineWidth(3.0f);
    }

    private static void assertWidthAcrossTheNearPlane(float[] start, float[] end) {
        nearPlaneScene();
        line(start, end);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        int rows = 0;
        for (int y = 0; y < SIZE; y++) {
            final int lit = litInRow(pixels, y);
            if (lit == 0) continue;
            rows++;
            final int row = y;
            assertTrue(lit <= 4, () -> "row " + row + " is " + lit + " pixels wide");
        }
        final int drawnRows = rows;
        assertTrue(drawnRows >= SIZE / 4, () -> "line covers only " + drawnRows + " rows");
    }

    @Test
    void lineEndingBehindTheCameraKeepsItsWidth() {
        assertWidthAcrossTheNearPlane(IN_FRONT, BEHIND);
    }

    @Test
    void lineStartingBehindTheCameraKeepsItsWidth() {
        assertWidthAcrossTheNearPlane(BEHIND, IN_FRONT);
    }

    @Test
    void lineFullyBehindTheCameraDrawsNothing() {
        nearPlaneScene();
        line(new float[] { 0f, -1f, 2f }, BEHIND);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        for (int y = 0; y < SIZE; y++) {
            assertEquals(0, litInRow(pixels, y), "row " + y);
        }
    }

    private static int litRowsInColumn(int[] pixels, int x, int fromRow, int toRow) {
        int lit = 0;
        for (int y = fromRow; y < toRow; y++) {
            if ((pixelAt(pixels, SIZE, x, y) & 0x00FFFFFF) != 0) lit++;
        }
        return lit;
    }

    private static void horizontalLines(float width, float lowerY, float upperY) {
        GLStateManager.glEnable(GL11.GL_CULL_FACE);
        GLStateManager.glLineWidth(width);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glBegin(GL11.GL_LINES);
        GLStateManager.glVertex3f(-0.8f, lowerY, 0.0f);
        GLStateManager.glVertex3f(0.8f, lowerY, 0.0f);
        GLStateManager.glVertex3f(0.8f, upperY, 0.0f);
        GLStateManager.glVertex3f(-0.8f, upperY, 0.0f);
        GLStateManager.glEnd();
    }

    @Test
    void wideLinesKeepTheirWidthInBothDirections() {
        horizontalLines(6.0f, -0.5f, 0.5f);
        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertEquals(6, litRowsInColumn(pixels, SIZE / 2, 0, SIZE / 2), "left-to-right line thickness");
        assertEquals(6, litRowsInColumn(pixels, SIZE / 2, SIZE / 2, SIZE), "right-to-left line thickness");
    }

    private static float pixelCenter(int pixel) {
        return (pixel + 0.5f) * 2.0f / SIZE - 1.0f;
    }

    private static int litInColumn(int[] pixels, int x) {
        return litRowsInColumn(pixels, x, 0, SIZE);
    }

    @Test
    void wideLineCoversItsFirstPixelButNotItsLast() {
        final float y = pixelCenter(16);
        GLStateManager.glLineWidth(2.0f);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GLStateManager.glBegin(GL11.GL_LINES);
        GLStateManager.glVertex3f(pixelCenter(40), y, 0.0f);
        GLStateManager.glVertex3f(pixelCenter(20), y, 0.0f);
        GLStateManager.glEnd();

        final int[] pixels = GlsmSdlHeadlessRig.readTarget();
        assertEquals(2, litInColumn(pixels, 40), "first pixel of a right-to-left line");
        assertEquals(0, litInColumn(pixels, 20), "last pixel of a right-to-left line");
        assertEquals(2, litInColumn(pixels, 21), "pixel next to the last");
    }
}
