package com.gtnewhorizons.angelica.util;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    public static void verifyState(int glCap, float[] expected) {
        verifyState(glCap, expected, "Float State Mismatch");
    }

    public static void verifyState(int glCap, float[] expected, String message) {
        final FloatBuffer glBuffer = BufferUtils.createFloatBuffer(16);
        final FloatBuffer glsmBuffer = BufferUtils.createFloatBuffer(16);

        GL11.glGetFloat(glCap, glBuffer);
        GLStateManager.glGetFloat(glCap, glsmBuffer);
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyState(int glCap, boolean[] expected) {
        verifyState(glCap, expected, "Boolean State Mismatch");
    }

    public static void verifyState(int glCap, boolean[] expected, String message) {
        final ByteBuffer glBuffer = BufferUtils.createByteBuffer(16);
        final ByteBuffer glsmBuffer = BufferUtils.createByteBuffer(16);

        GL11.glGetBoolean(glCap, glBuffer);
        GLStateManager.glGetBoolean(glCap, glsmBuffer);
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glBuffer.get(i), "GL State Mismatch: " + i),
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmBuffer.get(i), "GLSM State Mismatch: " + i)
        ));

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glBuffer.get(i), "GL State Mismatch");
            ;
        }
    }
}
