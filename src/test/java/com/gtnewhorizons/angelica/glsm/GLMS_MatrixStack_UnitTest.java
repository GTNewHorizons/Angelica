package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AngelicaExtension.class)
public class GLMS_MatrixStack_UnitTest {

    final static FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
    Matrix4f getMatrix(int matrix, boolean cached) {
        if(cached) {
            GLStateManager.glGetFloat(matrix, buffer);
        } else {
            GL11.glGetFloat(matrix, buffer);
        }
        return new Matrix4f(buffer);
    }
    void validateMatrix(Matrix4f expected, int matrix, String message) {
        // Be careful of precision here
        assertAll(message,
            () -> assertEquals(expected, getMatrix(matrix, true), "GL State Mismatch"),
            () -> assertEquals(expected, getMatrix(matrix, false), "GLSM State Mismatch")
        );

    }

    void validateMatrixOperations(int matrixMode, int matrixGetEnum, String matrixName) {
        GLStateManager.glMatrixMode(matrixMode);
        GLStateManager.glLoadIdentity();

        GL11.glPushMatrix();
        Matrix4f testMatrix = new Matrix4f().identity();

        validateMatrix(testMatrix, matrixGetEnum, "Identity " + matrixName + " Matrix");

        GLStateManager.glScalef(2.0f, 2.0f, 2.0f);
        testMatrix.scale(2.0f);
        validateMatrix(testMatrix, matrixGetEnum, "Scaled " + matrixName + " Matrix");

        GLStateManager.glTranslatef(2.0f, 2.0f, 2.0f);
        testMatrix.translate(2.0f, 2.0f, 2.0f);
        validateMatrix(testMatrix, matrixGetEnum, "Translated " + matrixName + " Matrix");

        GLStateManager.glRotatef(180.0f, 0.0f, 0.0f, 1.0f);
        testMatrix.rotate((float) Math.toRadians(180.0f), 0.0f, 0.0f, 1.0f);
        validateMatrix(testMatrix, matrixGetEnum, "Rotated " + matrixName + " Matrix");

        GLStateManager.glPopMatrix();
        testMatrix.identity();
        validateMatrix(testMatrix, matrixGetEnum, "Popped " + matrixName + " Matrix");
    }

    @Test
    void testGLSMMatrixStacks() {
        validateMatrixOperations(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_MATRIX, "Model");
        validateMatrixOperations(GL11.GL_PROJECTION, GL11.GL_PROJECTION_MATRIX, "Projection");
        validateMatrixOperations(GL11.GL_TEXTURE, GL11.GL_TEXTURE_MATRIX, "Texture");

        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
    }

}
