package com.gtnewhorizons.angelica.util;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class GLSMUtil {

    // Cached GL limits (queried once at class load time)
    private static final int MAX_CLIP_PLANES = GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES);
    private static final int MAX_LIGHTS = GL11.glGetInteger(GL11.GL_MAX_LIGHTS);
    private static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);

    // MAP1 evaluator constants for DRY loops
    private static final int[] MAP1_STATES = {
        GL11.GL_MAP1_COLOR_4, GL11.GL_MAP1_INDEX, GL11.GL_MAP1_NORMAL,
        GL11.GL_MAP1_TEXTURE_COORD_1, GL11.GL_MAP1_TEXTURE_COORD_2,
        GL11.GL_MAP1_TEXTURE_COORD_3, GL11.GL_MAP1_TEXTURE_COORD_4,
        GL11.GL_MAP1_VERTEX_3, GL11.GL_MAP1_VERTEX_4
    };

    // MAP2 evaluator constants for DRY loops
    private static final int[] MAP2_STATES = {
        GL11.GL_MAP2_COLOR_4, GL11.GL_MAP2_INDEX, GL11.GL_MAP2_NORMAL,
        GL11.GL_MAP2_TEXTURE_COORD_1, GL11.GL_MAP2_TEXTURE_COORD_2,
        GL11.GL_MAP2_TEXTURE_COORD_3, GL11.GL_MAP2_TEXTURE_COORD_4,
        GL11.GL_MAP2_VERTEX_3, GL11.GL_MAP2_VERTEX_4
    };

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
        GL11.glGetInteger(glCap, (IntBuffer) glIntBuffer.clear());
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
        GL11.glGetFloat(glCap, (FloatBuffer) glFloatBuffer.clear());
        GLStateManager.glGetFloat(glCap, (FloatBuffer) glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyNotDefaultState(int glCap, float[] expected, String message) {
        GL11.glGetFloat(glCap, (FloatBuffer) glFloatBuffer.clear());
        GLStateManager.glGetFloat(glCap, (FloatBuffer) glsmFloatBuffer.clear());
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

        GL11.glGetBoolean(glCap, (ByteBuffer) glByteBuffer.clear());
        GLStateManager.glGetBoolean(glCap, (ByteBuffer) glsmByteBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glByteBuffer.get(i), "GL State Mismatch: " + i),
            () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmByteBuffer.get(i), "GLSM State Mismatch: " + i)
        ));
    }


    public static void verifyLightState(int glLight, int pname, float[] expected, String message) {
        GL11.glGetLight(glLight, pname, (FloatBuffer) glFloatBuffer.clear());
        GLStateManager.glGetLight(glLight, pname, (FloatBuffer) glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i),  0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    public static void verifyMaterialState(int face, int pname, float[] expected, String message) {
        GL11.glGetMaterial(face, pname, (FloatBuffer) glFloatBuffer.clear());
        GLStateManager.glGetMaterial(face, pname, (FloatBuffer) glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i -> assertAll(message,
            () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
            () -> assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, "GLSM State Mismatch: " + i)
        ));
    }

    /**
     * Reset all GL state to known defaults to prevent test contamination.
     * This should be called at the end of tests that modify a lot of state.
     */
    public static void resetGLState() {
        // Disable all enable/disable states (except those with non-false defaults)
        GLStateManager.disableAlphaTest();
        GLStateManager.glDisable(GL11.GL_AUTO_NORMAL);
        GLStateManager.disableBlend();

        // Disable all clip planes (using cached MAX_CLIP_PLANES)
        for(int i = 0; i < MAX_CLIP_PLANES; i++) {
            GLStateManager.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }

        GLStateManager.disableColorMaterial();
        GLStateManager.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLStateManager.disableCull();
        GLStateManager.disableDepthTest();
        // GL_DITHER defaults to true
        GLStateManager.glEnable(GL11.GL_DITHER);
        GLStateManager.disableFog();
        GLStateManager.glDisable(GL11.GL_INDEX_LOGIC_OP);
        GLStateManager.disableLighting();

        // Disable all lights (using cached MAX_LIGHTS)
        for(int i = 0; i < MAX_LIGHTS; i++) {
            GLStateManager.disableLight(i);
        }

        GLStateManager.glDisable(GL11.GL_LINE_SMOOTH);
        GLStateManager.glDisable(GL11.GL_LINE_STIPPLE);

        // Disable all MAP1 evaluators (DRY loop)
        for(int state : MAP1_STATES) {
            GLStateManager.glDisable(state);
        }

        // Disable all MAP2 evaluators (DRY loop)
        for(int state : MAP2_STATES) {
            GLStateManager.glDisable(state);
        }

        // GL_MULTISAMPLE defaults to true (but may vary by driver)
        // We'll query the actual default rather than assume
        if(GL11.glIsEnabled(GL13.GL_MULTISAMPLE)) {
            GLStateManager.glEnable(GL13.GL_MULTISAMPLE);
        } else {
            GLStateManager.glDisable(GL13.GL_MULTISAMPLE);
        }

        GLStateManager.glDisable(GL11.GL_NORMALIZE);
        GLStateManager.glDisable(GL11.GL_POINT_SMOOTH);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_POINT);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GLStateManager.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLStateManager.glDisable(GL11.GL_POLYGON_SMOOTH);
        GLStateManager.glDisable(GL11.GL_POLYGON_STIPPLE);
        GLStateManager.disableRescaleNormal();
        GLStateManager.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
        GLStateManager.glDisable(GL13.GL_SAMPLE_ALPHA_TO_ONE);
        GLStateManager.glDisable(GL13.GL_SAMPLE_COVERAGE);
        GLStateManager.disableScissorTest();
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);

        // Reset texture state for all units (using cached MAX_TEXTURE_UNITS)
        for(int i = 0; i < MAX_TEXTURE_UNITS; i++) {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GLStateManager.glDisable(GL11.GL_TEXTURE_1D);
            GLStateManager.disableTexture(); // GL_TEXTURE_2D
            GLStateManager.glDisable(GL12.GL_TEXTURE_3D);
            GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_S);
            GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_T);
            GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_R);
            GLStateManager.glDisable(GL11.GL_TEXTURE_GEN_Q);
        }

        // Reset to texture unit 0
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        // Reset matrix mode to default
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);

        // Reset color to white
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Reset material colors to defaults (fixes GL_COLOR_MATERIAL side effects)
        FloatBuffer materialBuffer = BufferUtils.createFloatBuffer(4);
        materialBuffer.put(0.2F).put(0.2F).put(0.2F).put(1.0F).flip();
        GLStateManager.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT, materialBuffer);
        materialBuffer.clear();
        materialBuffer.put(0.8F).put(0.8F).put(0.8F).put(1.0F).flip();
        GLStateManager.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_DIFFUSE, materialBuffer);

        // Reset draw buffer
        GLStateManager.glDrawBuffer(GLStateManager.DEFAULT_DRAW_BUFFER);
    }


    public static void setClientState(int glCap, boolean state) {
        if (state) {
            GL11.glEnableClientState(glCap);
        } else {
            GL11.glDisableClientState(glCap);
        }
    }
}
