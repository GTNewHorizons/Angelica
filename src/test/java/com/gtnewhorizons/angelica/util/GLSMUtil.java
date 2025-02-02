package com.gtnewhorizons.angelica.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class GLSMUtil {

    public static void verifyIsEnabled(int glCap, boolean expected) {
        verifyIsEnabled(glCap, expected, "Is Enabled Mismatch");
    }

    public static void verifyIsEnabled(int glCap, boolean expected, String message) {
        assertAll( message,
            () -> assertEquals(expected, GL11.glIsEnabled(glCap), "GL State Mismatch"),
            () -> assertEquals(expected, GLStateManager.glIsEnabled(glCap), "GLSM State Mismatch")
        );
    }

    public static void verifyState(int glCap, boolean expected) {
        verifyState(glCap, expected, "Boolean State Mismatch");
    }

    public static void verifyState(int glCap, boolean expected, String message) {
        assertAll( message,
            () -> assertEquals(expected, GL11.glGetBoolean(glCap), "GL State Mismatch"),
            () -> assertEquals(expected, GLStateManager.glGetBoolean(glCap), "GLSM State Mismatch")
        );
    }

    public static void verifyState(int glCap, int expected) {
        verifyState(glCap, expected, "Int State Mismatch");
    }
    public static void verifyState(int glCap, int expected, String message) {
        assertAll(message,
            () -> assertEquals(expected, GL11.glGetInteger(glCap), "GL State Mismatch"),
            () -> assertEquals(expected, GLStateManager.glGetInteger(glCap), "GLSM State Mismatch")
        );
    }

    public static void verifyState(int glCap, float expected) {
        verifyState(glCap, expected, "Float State Mismatch");
    }
    public static void verifyState(int glCap, float expected, String message) {
        assertAll(message,
            () -> assertEquals(expected, GL11.glGetFloat(glCap), 0.0001f, "GL State Mismatch"),
            () -> assertEquals(expected, GLStateManager.glGetFloat(glCap), 0.0001f, "GLSM State Mismatch")
        );
    }
    public static void verifyState(int glCap, int[] expected) {
        verifyState(glCap, expected, "Int State Mismatch");
    }

    static final IntBuffer glIntBuffer = BufferUtils.createIntBuffer(16);
    static final IntBuffer glsmIntBuffer = BufferUtils.createIntBuffer(16);

    public static void verifyState(int glCap, int[] expected, String message) {
        GL11.glGetIntegerv(glCap, glIntBuffer.clear());
        GLStateManager.glGetInteger(glCap, (IntBuffer) glsmIntBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glIntBuffer.get(i), "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmIntBuffer.get(i), "GLSM State Mismatch: " + i)
        ));
    }


    public static void verifyState(int glCap, float[] expected) {
        verifyState(glCap, expected, "Float State Mismatch");
    }

    static final FloatBuffer glFloatBuffer = BufferUtils.createFloatBuffer(16);
    static final FloatBuffer glsmFloatBuffer = BufferUtils.createFloatBuffer(16);

    public static void verifyState(int glCap, float[] expected, String message) {
        GL11.glGetFloatv(glCap, glFloatBuffer.clear());
        GLStateManager.glGetFloat(glCap, glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyNotDefaultState(int glCap, float[] expected, String message) {
        GL11.glGetFloatv(glCap, glFloatBuffer.clear());
        GLStateManager.glGetFloat(glCap, glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertNotEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertNotEquals(expected[i], glsmFloatBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyState(int glCap, boolean[] expected) {
        verifyState(glCap, expected, "Boolean State Mismatch");
    }

    static final ByteBuffer glByteBuffer = BufferUtils.createByteBuffer(16);
    static final ByteBuffer glsmByteBuffer = BufferUtils.createByteBuffer(16);

    public static void verifyState(int glCap, boolean[] expected, String message) {

        GL11.glGetBooleanv(glCap, glByteBuffer.clear());
        GLStateManager.glGetBoolean(glCap, (ByteBuffer) glsmByteBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glByteBuffer.get(i), "GL State Mismatch: " + i),
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmByteBuffer.get(i), "GLSM State Mismatch: " + i)
        ));
    }


    public static void verifyLightState(int glLight, int pname, float[] expected, String message) {
        GL11.glGetLightfv(glLight, pname, glFloatBuffer.clear());
        GLStateManager.glGetLight(glLight, pname, glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyMaterialState(int face, int pname, float[] expected, String message) {
        GL11.glGetMaterialfv(face, pname, glFloatBuffer.clear());
        GLStateManager.glGetMaterial(face, pname, glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, "GLSM State Mismatch: " + i)
        ));
    }
}
