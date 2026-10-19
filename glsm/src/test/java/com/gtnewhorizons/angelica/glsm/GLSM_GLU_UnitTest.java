package com.gtnewhorizons.angelica.glsm;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that GLStateManager's GLU implementations produce the same matrices as the equivalent JOML operations.
 */
@GLCompatTest
public class GLSM_GLU_UnitTest {

    private Matrix4f getModelviewMatrix() {
        return new Matrix4f(GlTestSupport.modelview());
    }

    private Matrix4f getProjectionMatrix() {
        return new Matrix4f(GlTestSupport.matrix(GL11.GL_PROJECTION_MATRIX));
    }

    private void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String message) {
        assertTrue(expected.equals(actual, 0.001f), () -> message + "\nExpected:\n" + expected + "\nActual:\n" + actual);
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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "gluPickMatrix center, 0, 0, 800, 600, 400, 300, 5",
        "gluPickMatrix off-center viewport, 100, 50, 640, 480, 200, 150, 10",
    })
    void gluPickMatrix_matchesManual(String label, int vx, int vy, int vw, int vh, float x, float y, float delta) {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, vx).put(1, vy).put(2, vw).put(3, vh);

        GLStateManager.gluPickMatrix(x, y, delta, delta, viewport);

        Matrix4f expected = new Matrix4f()
            .translate((vw - 2 * (x - vx)) / delta, (vh - 2 * (y - vy)) / delta, 0.0f)
            .scale(vw / delta, vh / delta, 1.0f);
        assertMatrixEquals(expected, getProjectionMatrix(), label);

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

    static Stream<Arguments> errorStrings() {
        return Stream.of(
            Arguments.of(GL11.GL_NO_ERROR, "No error"),
            Arguments.of(GL11.GL_INVALID_ENUM, "Invalid enum"),
            Arguments.of(GL11.GL_INVALID_VALUE, "Invalid value"),
            Arguments.of(GL11.GL_INVALID_OPERATION, "Invalid operation"),
            Arguments.of(GL11.GL_OUT_OF_MEMORY, "Out of memory"),
            Arguments.of(100900, "Invalid enum (glu)"),
            Arguments.of(100901, "Invalid value (glu)"),
            Arguments.of(100902, "Out of memory (glu)"),
            Arguments.of(99999, "Unknown error: 99999"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("errorStrings")
    void gluErrorString(int code, String expected) {
        assertEquals(expected, GLStateManager.gluErrorString(code));
    }

    static Stream<Arguments> gluUnProject_roundTrips() {
        return Stream.of(
            Arguments.of("identity", new Matrix4f(), new Matrix4f(), 0.001f),
            Arguments.of("perspective with lookAt", new Matrix4f().perspective((float) Math.toRadians(60.0f), 800.0f / 600.0f, 0.1f, 100.0f),
                new Matrix4f().lookAt(0, 0, 5, 0, 0, 0, 0, 1, 0), 0.01f));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void gluUnProject_roundTrips(String label, Matrix4f proj, Matrix4f model, float tolerance) {
        Vector4f worldPt = new Vector4f(0, 0, 0, 1);
        new Matrix4f(proj).mul(model).transform(worldPt);
        float winX = (worldPt.x / worldPt.w * 0.5f + 0.5f) * 800.0f;
        float winY = (worldPt.y / worldPt.w * 0.5f + 0.5f) * 600.0f;
        float winZ = worldPt.z / worldPt.w * 0.5f + 0.5f;

        FloatBuffer modelBuf = BufferUtils.createFloatBuffer(16);
        FloatBuffer projBuf = BufferUtils.createFloatBuffer(16);
        model.get(modelBuf);
        proj.get(projBuf);

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, 0).put(1, 0).put(2, 800).put(3, 600);

        FloatBuffer result = BufferUtils.createFloatBuffer(3);
        assertTrue(GLStateManager.gluUnProject(winX, winY, winZ, modelBuf, projBuf, viewport, result), "gluUnProject should succeed");
        assertEquals(0.0f, result.get(0), tolerance, "X should unproject back to 0");
        assertEquals(0.0f, result.get(1), tolerance, "Y should unproject back to 0");
        assertEquals(0.0f, result.get(2), tolerance, "Z should unproject back to 0");
    }

    @Test
    void gluUnProject_singularMatrix() {
        FloatBuffer model = BufferUtils.createFloatBuffer(16);
        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        for (int i = 0; i < 16; i++) {
            model.put(i, 0.0f);
            proj.put(i, 0.0f);
        }

        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        viewport.put(0, 0).put(1, 0).put(2, 800).put(3, 600);

        FloatBuffer result = BufferUtils.createFloatBuffer(3);
        assertFalse(GLStateManager.gluUnProject(400, 300, 0.5f, model, proj, viewport, result));
    }


    @Test
    void gluBuild2DMipmaps_invalidSize() {
        assertEquals(100901, GLStateManager.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, GL11.GL_RGBA, 0, 10, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(1)));
        assertEquals(100901, GLStateManager.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, GL11.GL_RGBA, 10, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(1)));
    }

    @Test
    void gluBuild2DMipmaps_uploadsTexture() {
        int texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int i = 0; i < 4 * 4 * 4; i++) pixels.put((byte) 0xFF);
        pixels.flip();

        int result = GLStateManager.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, GL11.GL_RGBA, 4, 4, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        assertEquals(0, result, "gluBuild2DMipmaps should return 0 on success");
        assertEquals(4, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        assertEquals(1, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 2, GL11.GL_TEXTURE_WIDTH));

        GL11.glDeleteTextures(texId);
    }
}
