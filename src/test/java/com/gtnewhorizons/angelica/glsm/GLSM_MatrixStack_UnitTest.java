package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AngelicaExtension.class)
public class GLSM_MatrixStack_UnitTest {

    final static FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
    Matrix4f getMatrix(int matrix, boolean cached) {
        buffer.clear();
        if(cached) {
            GLStateManager.glGetFloat(matrix, buffer);
        } else {
            GL11.glGetFloat(matrix, buffer);
        }
        return new Matrix4f(buffer);
    }
    boolean matrixEquals(Matrix4f expected, Matrix4f actual) {
        if(expected.equals(actual, 0.0001f)) {
            return true;
        } else {
            System.out.println("Expected: " + expected);
            System.out.println("Actual: " + actual);
            return false;
        }
    }
    void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String message) {
        assertTrue(matrixEquals(expected, actual), message);
    }
    void validateMatrix(Matrix4f expected, int matrix, String message) {
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

    @ParameterizedTest
    @EnumSource(MatrixMode.class)
    void validateMatrixOperations(MatrixMode matrix) {
        GLStateManager.glMatrixMode(matrix.mode);
        GLStateManager.glLoadIdentity();

        GLStateManager.glPushMatrix();

        Matrix4fStack testMatrix = new Matrix4fStack(2);

        validateMatrix(testMatrix, matrix.getEnum, "Identity " + matrix.name + " Matrix");

        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        testMatrix.scale(2.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Scaled " + matrix.name + " Matrix");

        GLStateManager.glTranslatef(2.0f, 2.0f, 2.0f);
        testMatrix.translate(2.0f, 2.0f, 2.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Translated " + matrix.name + " Matrix");

        GLStateManager.glRotatef(180.0f, 0.0f, 0.0f, 1.0f);
        testMatrix.rotate((float) Math.toRadians(180.0f), 0.0f, 0.0f, 1.0f);
        validateMatrix(testMatrix, matrix.getEnum, "Rotated " + matrix.name + " Matrix");

        GLStateManager.glOrtho(0, 800, 0, 600, -1, 1);
        testMatrix.ortho(0, 800, 0, 600, -1, 1);
        validateMatrix(testMatrix, matrix.getEnum, "Ortho " + matrix.name + " Matrix");

        Matrix4f testLoad = new Matrix4f();
        testLoad.m02(5f);
        testLoad.m12(12f);
        testLoad.get(buffer);

        GLStateManager.glLoadMatrix(buffer);
        validateMatrix(testLoad, matrix.getEnum, "Loaded " + matrix.name + " Matrix");

        GLStateManager.glPopMatrix();
        testMatrix.identity();
        validateMatrix(testMatrix, matrix.getEnum, "Popped " + matrix.name + " Matrix");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
