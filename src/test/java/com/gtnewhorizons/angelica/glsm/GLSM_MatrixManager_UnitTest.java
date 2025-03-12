package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.OpenGLTestBase;
import com.gtnewhorizons.angelica.glsm.managers.GLMatrixManager;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GLSM_MatrixManager_UnitTest extends OpenGLTestBase {

    private static final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);
    private static final DoubleBuffer doubleBuffer = BufferUtils.createDoubleBuffer(16);

    @BeforeEach
    void setupMatrices() {
        // Start each test with identity matrices
        GLMatrixManager.reset();
    }

    private Matrix4f getMatrix(int matrix, boolean cached) {
        floatBuffer.clear();
        if (cached) {
            GLStateManager.glGetFloat(matrix, floatBuffer);
        } else {
            GL11.glGetFloatv(matrix, floatBuffer);
        }
        return new Matrix4f(floatBuffer);
    }

    private boolean matrixEquals(Matrix4f expected, Matrix4f actual) {
        if (expected.equals(actual, 0.0001f)) {
            return true;
        } else {
            System.out.println("Expected: " + expected);
            System.out.println("Actual: " + actual);
            return false;
        }
    }

    private void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String message) {
        assertTrue(matrixEquals(expected, actual), message);
    }

    private void validateMatrix(Matrix4f expected, int matrix, String message) {
        // Be careful of precision here
        assertAll(message,
            () -> assertMatrixEquals(expected, getMatrix(matrix, false), "GL State Mismatch"),
            () -> assertMatrixEquals(expected, getMatrix(matrix, true), "GLSM State Mismatch")
        );
    }

    enum MatrixMode {
        MODELVIEW(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_MATRIX, "Model"),
        PROJECTION(GL11.GL_PROJECTION, GL11.GL_PROJECTION_MATRIX, "Projection"),
        TEXTURE(GL11.GL_TEXTURE, GL11.GL_TEXTURE_MATRIX, "Texture");

        protected final int mode;
        protected final int getEnum;
        protected final String name;

        MatrixMode(int mode, int getEnum, String name) {
            this.mode = mode;
            this.getEnum = getEnum;
            this.name = name;
        }
    }

    @Test
    void testGetMatrixStackDepth() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final int initialDepth = GLMatrixManager.getMatrixStackDepth(GLMatrixManager.modelViewMatrix);

        GLMatrixManager.glPushMatrix();
        final int afterPushDepth = GLMatrixManager.getMatrixStackDepth(GLMatrixManager.modelViewMatrix);
        assertEquals(initialDepth + 1, afterPushDepth, "Stack depth should increase by 1 after push");

        GLMatrixManager.glPopMatrix();
        final int afterPopDepth = GLMatrixManager.getMatrixStackDepth(GLMatrixManager.modelViewMatrix);
        assertEquals(initialDepth, afterPopDepth, "Stack depth should decrease by 1 after pop");
    }

    @Test
    void testMatrixMode() {
        // Set to model view
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        assertEquals(GL11.GL_MODELVIEW, GLMatrixManager.matrixMode.getMode(), "Matrix mode should be GL_MODELVIEW");
        assertEquals(GLMatrixManager.modelViewMatrix, GLMatrixManager.getMatrixStack(), "Matrix stack should be modelViewMatrix");

        // Set to projection
        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        assertEquals(GL11.GL_PROJECTION, GLMatrixManager.matrixMode.getMode(), "Matrix mode should be GL_PROJECTION");
        assertEquals(GLMatrixManager.projectionMatrix, GLMatrixManager.getMatrixStack(), "Matrix stack should be projectionMatrix");

        // Set to texture
        GLMatrixManager.glMatrixMode(GL11.GL_TEXTURE);
        assertEquals(GL11.GL_TEXTURE, GLMatrixManager.matrixMode.getMode(), "Matrix mode should be GL_TEXTURE");

        // Reset to model view for other tests
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void testLoadMatrixf() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final Matrix4f testMatrix = new Matrix4f();
        testMatrix.m00(2.0f);
        testMatrix.m11(3.0f);
        testMatrix.m22(4.0f);

        final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        testMatrix.get(matrix);

        GLMatrixManager.glLoadMatrixf(matrix);
        validateMatrix(testMatrix, GL11.GL_MODELVIEW_MATRIX, "Matrix should be loaded correctly with glLoadMatrixf");
    }

    @Test
    void testLoadMatrixd() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        // Create a test matrix
        final Matrix4d testMatrixD = new Matrix4d().m00(2.0).m11(3.0).m22(4.0);

        // Load the matrix using GLMatrixManager
        final DoubleBuffer matrixBuffer = BufferUtils.createDoubleBuffer(16);
        testMatrixD.get(matrixBuffer);
        GLMatrixManager.glLoadMatrixd(matrixBuffer);

        // Validate the matrix loaded in OpenGL state
        final Matrix4f expectedMatrix = new Matrix4f(testMatrixD);
        validateMatrix(expectedMatrix, GL11.GL_MODELVIEW_MATRIX, "Matrix should be loaded correctly with GLMatrixManager");

        // Validate the cached matrix in GLMatrixManager
        final Matrix4f cachedMatrix = getMatrix(GL11.GL_MODELVIEW_MATRIX, true);
        assertMatrixEquals(expectedMatrix, cachedMatrix, "Cached matrix should match the loaded matrix");
    }

    @Test
    void testGetMatrixStack() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        Matrix4fStack stack = GLMatrixManager.getMatrixStack();
        assertEquals(GLMatrixManager.modelViewMatrix, stack, "ModelView stack should be returned when mode is GL_MODELVIEW");

        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        stack = GLMatrixManager.getMatrixStack();
        assertEquals(GLMatrixManager.projectionMatrix, stack, "Projection stack should be returned when mode is GL_PROJECTION");

        GLMatrixManager.glMatrixMode(GL11.GL_TEXTURE);
        stack = GLMatrixManager.getMatrixStack();
        assertNotNull(stack, "Texture stack should be returned when mode is GL_TEXTURE");

        // Reset to model view for other tests
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void testLoadIdentity() {
        for (MatrixMode mode : MatrixMode.values()) {
            GLMatrixManager.glMatrixMode(mode.mode);

            // Set some non-identity values
            GLMatrixManager.glTranslatef(1.0f, 2.0f, 3.0f);

            // Load identity and verify
            GLMatrixManager.glLoadIdentity();
            final Matrix4f identity = new Matrix4f();
            validateMatrix(identity, mode.getEnum, "Matrix should be identity after glLoadIdentity in " + mode.name + " mode");
        }

        // Reset to model view for other tests
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

    @Test
    void testTranslatef() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final float x = 1.0f;
        float y = 2.0f;
        final float z = 3.0f;
        GLMatrixManager.glTranslatef(x, y, z);

        final Matrix4f expected = new Matrix4f().translate(x, y, z);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be translated correctly with glTranslatef");
    }

    @Test
    void testTranslated() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final double x = 1.0;
        double y = 2.0;
        final double z = 3.0;
        GLMatrixManager.glTranslated(x, y, z);

        final Matrix4f expected = new Matrix4f().translate((float)x, (float)y, (float)z);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be translated correctly with glTranslated");
    }

    @Test
    void testScalef() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final float x = 2.0f;
        float y = 3.0f;
        final float z = 4.0f;
        GLMatrixManager.glScalef(x, y, z);

        final Matrix4f expected = new Matrix4f().scale(x, y, z);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be scaled correctly with glScalef");
    }

    @Test
    void testScaled() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final double x = 2.0;
        double y = 3.0;
        final double z = 4.0;
        GLMatrixManager.glScaled(x, y, z);

        final Matrix4f expected = new Matrix4f().scale((float)x, (float)y, (float)z);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be scaled correctly with glScaled");
    }

    @Test
    void testMultMatrixf() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        // Create a test matrix
        final Matrix4f testMatrix = new Matrix4f().translate(1.0f, 2.0f, 3.0f);

        final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        testMatrix.get(matrix);

        GLMatrixManager.glMultMatrixf(matrix);
        validateMatrix(testMatrix, GL11.GL_MODELVIEW_MATRIX, "Matrix should be multiplied correctly with glMultMatrixf");
    }

    @Test
    void testMultMatrixd() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        // Create a test matrix
        final Matrix4d testMatrixD = new Matrix4d().translate(1.0, 2.0, 3.0);
        final Matrix4f testMatrix = new Matrix4f(testMatrixD);

        final DoubleBuffer matrix = BufferUtils.createDoubleBuffer(16);
        testMatrixD.get(matrix);

        GLMatrixManager.glMultMatrixd(matrix);
        validateMatrix(testMatrix, GL11.GL_MODELVIEW_MATRIX, "Matrix should be multiplied correctly with glMultMatrixd");
    }

    @Test
    void testRotatef() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final float angle = 45.0f;
        final float x = 0.0f;
        float y = 0.0f;
        final float z = 1.0f;
        GLMatrixManager.glRotatef(angle, x, y, z);

        final Vector3f axis = new Vector3f(x, y, z).normalize();
        final Matrix4f expected = new Matrix4f().rotate((float)Math.toRadians(angle), axis);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be rotated correctly with glRotatef");
    }

    @Test
    void testRotated() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        final double angle = 45.0;
        final double x = 0.0;
        double y = 0.0;
        final double z = 1.0;
        GLMatrixManager.glRotated(angle, x, y, z);

        final Vector3f axis = new Vector3f((float)x, (float)y, (float)z).normalize();
        final Matrix4f expected = new Matrix4f().rotate((float)Math.toRadians(angle), axis);
        validateMatrix(expected, GL11.GL_MODELVIEW_MATRIX, "Matrix should be rotated correctly with glRotated");
    }

    @Test
    void testOrtho() {
        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        GLMatrixManager.glLoadIdentity();

        final double left = -1.0;
        double right = 1.0;
        double bottom = -1.0;
        double top = 1.0;
        double zNear = -1.0;
        final double zFar = 1.0;
        GLMatrixManager.glOrtho(left, right, bottom, top, zNear, zFar);

        final Matrix4f expected = new Matrix4f().ortho(
            (float)left, (float)right,
            (float)bottom, (float)top,
            (float)zNear, (float)zFar
        );
        validateMatrix(expected, GL11.GL_PROJECTION_MATRIX, "Matrix should be set to ortho correctly with glOrtho");
    }

    @Test
    void testFrustum() {
        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        GLMatrixManager.glLoadIdentity();

        final double left = -1.0;
        double right = 1.0;
        double bottom = -1.0;
        double top = 1.0;
        double zNear = 1.0;
        final double zFar = 100.0;
        GLMatrixManager.glFrustum(left, right, bottom, top, zNear, zFar);

        final Matrix4f expected = new Matrix4f().frustum(
            (float)left, (float)right,
            (float)bottom, (float)top,
            (float)zNear, (float)zFar
        );
        validateMatrix(expected, GL11.GL_PROJECTION_MATRIX, "Matrix should be set to frustum correctly with glFrustum");
    }

    @Test
    void testPushPopMatrix() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();

        // Save the initial matrix state
        final Matrix4f initialMatrix = getMatrix(GL11.GL_MODELVIEW_MATRIX, true);

        // Push matrix, make changes
        GLMatrixManager.glPushMatrix();
        GLMatrixManager.glTranslatef(1.0f, 2.0f, 3.0f);
        final Matrix4f modifiedMatrix = getMatrix(GL11.GL_MODELVIEW_MATRIX, true);
        assertFalse(matrixEquals(initialMatrix, modifiedMatrix), "Matrix should be modified after translation");

        // Pop matrix, verify we're back to the initial state
        GLMatrixManager.glPopMatrix();
        final Matrix4f afterPopMatrix = getMatrix(GL11.GL_MODELVIEW_MATRIX, true);
        assertTrue(matrixEquals(initialMatrix, afterPopMatrix), "Matrix should be restored to initial state after pop");
    }

    @Test
    void testGluPerspective() {
        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        GLMatrixManager.glLoadIdentity();

        final float fovy = 45.0f;
        final float aspect = 1.0f;
        final float zNear = 0.1f;
        final float zFar = 100.0f;

        GLMatrixManager.gluPerspective(fovy, aspect, zNear, zFar);

        final Matrix4f expected = new Matrix4f().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);
        validateMatrix(expected, GL11.GL_PROJECTION_MATRIX, "Matrix should be set to perspective correctly with gluPerspective");
    }

    @ParameterizedTest
    @EnumSource(MatrixMode.class)
    void validateMatrixOperations(MatrixMode matrix) {
        GLMatrixManager.glMatrixMode(matrix.mode);
        GLMatrixManager.glLoadIdentity();

        GLMatrixManager.glPushMatrix();

        final Matrix4fStack testMatrix = new Matrix4fStack(2);

        validateMatrix(testMatrix, matrix.getEnum, "Identity " + matrix.name + " Matrix");

        GLMatrixManager.glScalef(2.0f, 2.0f, 2.0f);
        testMatrix.scale(2.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Scaled " + matrix.name + " Matrix");

        GLMatrixManager.glTranslatef(2.0f, 2.0f, 2.0f);
        testMatrix.translate(2.0f, 2.0f, 2.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Translated " + matrix.name + " Matrix");

        GLMatrixManager.glRotatef(180.0f, 0.0f, 0.0f, 1.0f);
        testMatrix.rotate((float) Math.toRadians(180.0f), 0.0f, 0.0f, 1.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Rotated " + matrix.name + " Matrix");

        GLMatrixManager.glOrtho(0, 800, 0, 600, -1, 1);
        testMatrix.ortho(0, 800, 0, 600, -1, 1);
        validateMatrix(testMatrix, matrix.getEnum, "Ortho " + matrix.name + " Matrix");

        final Matrix4f testLoad = new Matrix4f();
        testLoad.m02(5f);
        testLoad.m12(12f);
        testLoad.get(floatBuffer);

        GLMatrixManager.glLoadMatrix(floatBuffer);
        validateMatrix(testLoad, matrix.getEnum, "Loaded " + matrix.name + " Matrix");

        GLMatrixManager.glPopMatrix();
        testMatrix.identity();
        validateMatrix(testMatrix, matrix.getEnum, "Popped " + matrix.name + " Matrix");
    }

    @AfterAll
    static void cleanup() {
        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLMatrixManager.glLoadIdentity();
        GLMatrixManager.glMatrixMode(GL11.GL_PROJECTION);
        GLMatrixManager.glLoadIdentity();
        GLMatrixManager.glMatrixMode(GL11.GL_TEXTURE);
        GLMatrixManager.glLoadIdentity();

        GLMatrixManager.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
