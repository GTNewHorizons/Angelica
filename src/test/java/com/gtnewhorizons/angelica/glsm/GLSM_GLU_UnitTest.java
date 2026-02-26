package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that GLStateManager's GLU implementations produce the same matrices as the equivalent JOML operations.
 */
@ExtendWith(AngelicaExtension.class)
public class GLSM_GLU_UnitTest {

    private static final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

    private Matrix4f getModelviewMatrix() {
        buffer.clear();
        GLStateManager.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        return new Matrix4f(buffer);
    }

    private Matrix4f getProjectionMatrix() {
        buffer.clear();
        GLStateManager.glGetFloat(GL11.GL_PROJECTION_MATRIX, buffer);
        return new Matrix4f(buffer);
    }

    private void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String message) {
        assertTrue(expected.equals(actual, 0.001f), () -> message + "\nExpected:\n" + expected + "\nActual:\n" + actual);
    }

    @Test
    void gluPerspective_matchesJOML() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        GLStateManager.gluPerspective(60.0f, 1.5f, 0.1f, 1000.0f);

        Matrix4f expected = new Matrix4f().perspective((float) Math.toRadians(60.0f), 1.5f, 0.1f, 1000.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), "gluPerspective standard");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void gluPerspective_accumulates() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glScalef(2.0f, 1.0f, 1.0f);

        GLStateManager.gluPerspective(90.0f, 1.0f, 1.0f, 100.0f);

        Matrix4f expected = new Matrix4f()
            .scale(2.0f, 1.0f, 1.0f)
            .perspective((float) Math.toRadians(90.0f), 1.0f, 1.0f, 100.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), "gluPerspective after scale");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void gluLookAt_matchesJOML() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();

        GLStateManager.gluLookAt(
            0.0f, 0.0f, 5.0f,   // eye
            0.0f, 0.0f, 0.0f,   // center
            0.0f, 1.0f, 0.0f);  // up

        Matrix4f expected = new Matrix4f().lookAt(
            0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f);
        assertMatrixEquals(expected, getModelviewMatrix(), "gluLookAt simple");
    }

    @Test
    void gluLookAt_offAxis() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();

        GLStateManager.gluLookAt(
            3.0f, 4.0f, 5.0f,
            1.0f, 2.0f, 0.0f,
            0.0f, 1.0f, 0.0f);

        Matrix4f expected = new Matrix4f().lookAt(
            3.0f, 4.0f, 5.0f,
            1.0f, 2.0f, 0.0f,
            0.0f, 1.0f, 0.0f);
        assertMatrixEquals(expected, getModelviewMatrix(), "gluLookAt off-axis");
    }

    @Test
    void gluLookAt_accumulates() {
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(10.0f, 0.0f, 0.0f);

        GLStateManager.gluLookAt(
            0.0f, 0.0f, 5.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f);

        Matrix4f expected = new Matrix4f()
            .translate(10.0f, 0.0f, 0.0f)
            .lookAt(0.0f, 0.0f, 5.0f,
                    0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f);
        assertMatrixEquals(expected, getModelviewMatrix(), "gluLookAt after translate");
    }

    @Test
    void gluOrtho2D_matchesJOML() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        GLStateManager.gluOrtho2D(0.0f, 800.0f, 0.0f, 600.0f);

        Matrix4f expected = new Matrix4f().ortho(0.0f, 800.0f, 0.0f, 600.0f, -1.0f, 1.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), "gluOrtho2D");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void gluPickMatrix_matchesManual() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, 0);   // x
        viewport.put(1, 0);   // y
        viewport.put(2, 800); // width
        viewport.put(3, 600); // height

        float x = 400.0f, y = 300.0f, deltaX = 5.0f, deltaY = 5.0f;

        GLStateManager.gluPickMatrix(x, y, deltaX, deltaY, viewport);

        // Reproduce the expected result: translate then scale
        Matrix4f expected = new Matrix4f()
            .translate(
                (800 - 2 * (x - 0)) / deltaX,
                (600 - 2 * (y - 0)) / deltaY,
                0.0f)
            .scale(
                800.0f / deltaX,
                600.0f / deltaY,
                1.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), "gluPickMatrix center");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void gluPickMatrix_offCenter() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, 100);
        viewport.put(1, 50);
        viewport.put(2, 640);
        viewport.put(3, 480);

        float x = 200.0f, y = 150.0f, deltaX = 10.0f, deltaY = 10.0f;

        GLStateManager.gluPickMatrix(x, y, deltaX, deltaY, viewport);

        Matrix4f expected = new Matrix4f()
            .translate(
                (640 - 2 * (x - 100)) / deltaX,
                (480 - 2 * (y - 50)) / deltaY,
                0.0f)
            .scale(
                640.0f / deltaX,
                480.0f / deltaY,
                1.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), "gluPickMatrix off-center viewport");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void gluPickMatrix_zeroSize_noOp() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, 0).put(1, 0).put(2, 800).put(3, 600);

        GLStateManager.gluPickMatrix(400.0f, 300.0f, 0.0f, 5.0f, viewport);

        // deltaX <= 0 should be a no-op, matrix stays identity
        assertMatrixEquals(new Matrix4f(), getProjectionMatrix(), "gluPickMatrix zero deltaX is no-op");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
