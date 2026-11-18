package com.gtnewhorizons.angelica.util;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GLSMUtil {

    // Compat-only pnames, so they load with resetGLState() and not with the class: a core driver answers GL_INVALID_ENUM
    private static final class CompatLimits {
        static final int MAX_CLIP_PLANES = GL11.glGetInteger(GL11.GL_MAX_CLIP_PLANES);
        static final int MAX_LIGHTS = GL11.glGetInteger(GL11.GL_MAX_LIGHTS);
        static final int MAX_TEXTURE_UNITS = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
    }

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

    private static final Set<Integer> FFP_ONLY_CAPS = ffpOnlyCaps();

    private static Set<Integer> ffpOnlyCaps() {
        final Set<Integer> caps = new HashSet<>(Set.of(
        // Enable/disable caps (BooleanState ffpStateOnly=true)
        GL11.GL_FOG, GL11.GL_ALPHA_TEST, GL11.GL_LIGHTING,
        GL12.GL_RESCALE_NORMAL, GL11.GL_NORMALIZE, GL11.GL_COLOR_MATERIAL,
        GL11.GL_LIGHT0, GL11.GL_LIGHT1, GL11.GL_LIGHT2, GL11.GL_LIGHT3,
        GL11.GL_LIGHT4, GL11.GL_LIGHT5, GL11.GL_LIGHT6, GL11.GL_LIGHT7,
        // Texture enable/disable (TextureUnitBooleanStateStack ffpStateOnly=true)
        GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_3D,
        GL11.GL_TEXTURE_GEN_S, GL11.GL_TEXTURE_GEN_T,
        GL11.GL_TEXTURE_GEN_R, GL11.GL_TEXTURE_GEN_Q,
        // Matrix state
        GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW_MATRIX,
        GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX,
        // Current vertex state
        GL11.GL_CURRENT_COLOR, GL11.GL_CURRENT_NORMAL, GL11.GL_CURRENT_TEXTURE_COORDS,
        // Alpha test params
        GL11.GL_ALPHA_TEST_FUNC, GL11.GL_ALPHA_TEST_REF,
        // Shade model
        GL11.GL_SHADE_MODEL,
        // Color material params
        GL11.GL_COLOR_MATERIAL_FACE, GL11.GL_COLOR_MATERIAL_PARAMETER,
        // Light model params
        GL11.GL_LIGHT_MODEL_AMBIENT, GL11.GL_LIGHT_MODEL_LOCAL_VIEWER,
        GL11.GL_LIGHT_MODEL_TWO_SIDE, GL12.GL_LIGHT_MODEL_COLOR_CONTROL,
        // Line stipple params (removed from core profile)
        GL11.GL_LINE_STIPPLE, GL11.GL_LINE_STIPPLE_PATTERN, GL11.GL_LINE_STIPPLE_REPEAT,
        // Fog params
        GL11.GL_FOG_COLOR, GL11.GL_FOG_DENSITY,
        GL11.GL_FOG_START, GL11.GL_FOG_END,
        GL11.GL_FOG_MODE, GL11.GL_FOG_INDEX,
        // Also removed from core profile
        GL11.GL_POINT_SMOOTH, GL11.GL_POLYGON_STIPPLE,
        GL11.GL_INDEX_LOGIC_OP, GL11.GL_AUTO_NORMAL,
        GL13.GL_CLIENT_ACTIVE_TEXTURE,
        GL11.GL_VERTEX_ARRAY, GL11.GL_NORMAL_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY,
        GL11.GL_INDEX_ARRAY, GL11.GL_EDGE_FLAG_ARRAY, GL14.GL_SECONDARY_COLOR_ARRAY, GL14.GL_FOG_COORDINATE_ARRAY,
        GL11.GL_VERTEX_ARRAY_SIZE, GL11.GL_VERTEX_ARRAY_TYPE, GL11.GL_VERTEX_ARRAY_STRIDE));

        for (int cap : MAP1_STATES) caps.add(cap);
        for (int cap : MAP2_STATES) caps.add(cap);
        return caps;
    }

    // A core driver rejects these caps with GL_INVALID_ENUM. Only that error is dropped: one already pending is the test's.
    private static boolean driverBoolean(int glCap, boolean isEnabled) {
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "GL error pending before the driver query");
        final boolean value = isEnabled ? GL11.glIsEnabled(glCap) : GL11.glGetBoolean(glCap);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {}
        return value;
    }

    private static boolean isFFPOnly(int glCap) {
        return FFP_ONLY_CAPS.contains(glCap);
    }

    public static void verifyIsEnabled(int glCap, boolean expected) {
        verifyIsEnabled(glCap, expected, "Is Enabled Mismatch");
    }

    public static void verifyIsEnabled(int glCap, boolean expected, String message) {
        if (isFFPOnly(glCap)) {
            assertAll(message,
                () -> assertFalse(driverBoolean(glCap, true), "GL State Mismatch - cache-only cap reached the driver"),
                () -> assertEquals(expected, GLSMEnable.glIsEnabled(glCap), "GLSM State Mismatch")
            );
        } else {
            assertAll(message,
                () -> assertEquals(expected, GL11.glIsEnabled(glCap), "GL State Mismatch"),
                () -> assertEquals(expected, GLSMEnable.glIsEnabled(glCap), "GLSM State Mismatch")
            );
        }
    }

    public static void verifyState(int glCap, boolean expected) {
        verifyState(glCap, expected, "Boolean State Mismatch");
    }

    public static void verifyState(int glCap, boolean expected, String message) {
        if (isFFPOnly(glCap)) {
            assertAll(message,
                () -> assertFalse(driverBoolean(glCap, false), "GL State Mismatch - cache-only cap reached the driver"),
                () -> assertEquals(expected, GLSMQueries.glGetBoolean(glCap), "GLSM State Mismatch")
            );
        } else {
            assertAll(message,
                () -> assertEquals(expected, GL11.glGetBoolean(glCap), "GL State Mismatch"),
                () -> assertEquals(expected, GLSMQueries.glGetBoolean(glCap), "GLSM State Mismatch")
            );
        }
    }

    public static void verifyState(int glCap, int expected) {
        verifyState(glCap, expected, "Int State Mismatch");
    }
    public static void verifyState(int glCap, int expected, String message) {
        if (isFFPOnly(glCap)) {
            assertEquals(expected, GLSMQueries.glGetInteger(glCap), message + " - GLSM State Mismatch");
        } else {
            assertAll(message,
                () -> assertEquals(expected, GL11.glGetInteger(glCap), "GL State Mismatch"),
                () -> assertEquals(expected, GLSMQueries.glGetInteger(glCap), "GLSM State Mismatch")
            );
        }
    }

    public static void verifyState(int glCap, short expected, String message) {
        if (isFFPOnly(glCap)) {
            assertEquals(expected, (short) GLSMQueries.glGetInteger(glCap), message + " - GLSM State Mismatch");
        } else {
            assertAll(message,
                () -> assertEquals(expected, (short) GL11.glGetInteger(glCap), "GL State Mismatch"),
                () -> assertEquals(expected, (short) GLSMQueries.glGetInteger(glCap), "GLSM State Mismatch")
            );
        }
    }

    public static void verifyState(int glCap, float expected) {
        verifyState(glCap, expected, "Float State Mismatch");
    }
    public static void verifyState(int glCap, float expected, String message) {
        if (isFFPOnly(glCap)) {
            assertEquals(expected, GLSMQueries.glGetFloat(glCap), 0.0001f, message + " - GLSM State Mismatch");
        } else {
            assertAll(message,
                () -> assertEquals(expected, GL11.glGetFloat(glCap), 0.0001f, "GL State Mismatch"),
                () -> assertEquals(expected, GLSMQueries.glGetFloat(glCap), 0.0001f, "GLSM State Mismatch")
            );
        }
    }
    public static void verifyState(int glCap, int[] expected) {
        verifyState(glCap, expected, "Int State Mismatch");
    }

    static final IntBuffer glIntBuffer = BufferUtils.createIntBuffer(16);
    static final IntBuffer glsmIntBuffer = BufferUtils.createIntBuffer(16);

    public static void verifyState(int glCap, int[] expected, String message) {
        if (isFFPOnly(glCap)) {
            GLSMQueries.glGetInteger(glCap, (IntBuffer) glsmIntBuffer.clear());
            IntStream.range(0, expected.length).forEach(i ->
                assertEquals(expected[i], glsmIntBuffer.get(i), message + " - GLSM State Mismatch: " + i)
            );
        } else {
            GL11.glGetInteger(glCap, (IntBuffer) glIntBuffer.clear());
            GLSMQueries.glGetInteger(glCap, (IntBuffer) glsmIntBuffer.clear());
            IntStream.range(0, expected.length).forEach(i -> assertAll(message,
                () -> assertEquals(expected[i], glIntBuffer.get(i), "GL State Mismatch: " + i),
                () -> assertEquals(expected[i], glsmIntBuffer.get(i), "GLSM State Mismatch: " + i)
            ));
        }
    }


    public static void verifyState(int glCap, float[] expected) {
        verifyState(glCap, expected, "Float State Mismatch");
    }

    static final FloatBuffer glFloatBuffer = BufferUtils.createFloatBuffer(16);
    static final FloatBuffer glsmFloatBuffer = BufferUtils.createFloatBuffer(16);

    public static void verifyState(int glCap, float[] expected, String message) {
        if (isFFPOnly(glCap)) {
            GLSMQueries.glGetFloat(glCap, (FloatBuffer) glsmFloatBuffer.clear());
            IntStream.range(0, expected.length).forEach(i ->
                assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, message + " - GLSM State Mismatch: " + i)
            );
        } else {
            GL11.glGetFloat(glCap, (FloatBuffer) glFloatBuffer.clear());
            GLSMQueries.glGetFloat(glCap, (FloatBuffer) glsmFloatBuffer.clear());
            IntStream.range(0, expected.length).forEach(i -> assertAll(message,
                () -> assertEquals(expected[i], glFloatBuffer.get(i), 0.0001f, "GL State Mismatch: " + i),
                () -> assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, "GLSM State Mismatch: " + i)
            ));
        }
    }

    public static void verifyState(int glCap, boolean[] expected) {
        verifyState(glCap, expected, "Boolean State Mismatch");
    }

    static final ByteBuffer glByteBuffer = BufferUtils.createByteBuffer(16);
    static final ByteBuffer glsmByteBuffer = BufferUtils.createByteBuffer(16);

    public static void verifyState(int glCap, boolean[] expected, String message) {
        if (isFFPOnly(glCap)) {
            GLSMQueries.glGetBoolean(glCap, (ByteBuffer) glsmByteBuffer.clear());
            IntStream.range(0, expected.length).forEach(i ->
                assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmByteBuffer.get(i), message + " - GLSM State Mismatch: " + i)
            );
        } else {
            GL11.glGetBoolean(glCap, (ByteBuffer) glByteBuffer.clear());
            GLSMQueries.glGetBoolean(glCap, (ByteBuffer) glsmByteBuffer.clear());
            IntStream.range(0, expected.length).forEach(i -> assertAll(message,
                () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glByteBuffer.get(i), "GL State Mismatch: " + i),
                () -> assertEquals(expected[i] ? GL11.GL_TRUE : GL11.GL_FALSE, glsmByteBuffer.get(i), "GLSM State Mismatch: " + i)
            ));
        }
    }


    /** Light params are FFP-only — verify GLSM cache only. */
    public static void verifyLightState(int glLight, int pname, float[] expected, String message) {
        GLSMLighting.glGetLight(glLight, pname, (FloatBuffer) glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i ->
            assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, message + " - GLSM State Mismatch: " + i)
        );
    }

    /** Material params are FFP-only — verify GLSM cache only. */
    public static void verifyMaterialState(int face, int pname, float[] expected, String message) {
        GLSMLighting.glGetMaterial(face, pname, (FloatBuffer) glsmFloatBuffer.clear());
        IntStream.range (0, expected.length).forEach(i ->
            assertEquals(expected[i], glsmFloatBuffer.get(i), 0.0001f, message + " - GLSM State Mismatch: " + i)
        );
    }

    /**
     * Reset all GL state to known defaults to prevent test contamination.
     * This should be called at the end of tests that modify a lot of state.
     */
    public static void resetGLState() {
        // Disable all enable/disable states (except those with non-false defaults)
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMEnable.glDisable(GL11.GL_AUTO_NORMAL);
        GLSMBlend.disableBlend();

        // Disable all clip planes (using cached MAX_CLIP_PLANES)
        for(int i = 0; i < CompatLimits.MAX_CLIP_PLANES; i++) {
            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }

        GLSMLighting.disableColorMaterial();
        GLSMEnable.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GLSMRasterizer.disableCull();
        GLSMDepthStencilAlpha.disableDepthTest();
        // GL_DITHER defaults to true
        GLSMEnable.glEnable(GL11.GL_DITHER);
        GLSMFog.disableFog();
        GLSMEnable.glDisable(GL11.GL_INDEX_LOGIC_OP);
        GLSMLighting.disableLighting();

        // Disable all lights (using cached MAX_LIGHTS)
        for(int i = 0; i < CompatLimits.MAX_LIGHTS; i++) {
            GLSMLighting.disableLight(i);
        }

        GLSMEnable.glDisable(GL11.GL_LINE_SMOOTH);
        GLSMEnable.glDisable(GL11.GL_LINE_STIPPLE);

        // Disable all MAP1 evaluators (DRY loop)
        for(int state : MAP1_STATES) {
            GLSMEnable.glDisable(state);
        }

        // Disable all MAP2 evaluators (DRY loop)
        for(int state : MAP2_STATES) {
            GLSMEnable.glDisable(state);
        }

        // GL_MULTISAMPLE defaults to true (but may vary by driver)
        // We'll query the actual default rather than assume
        if(GL11.glIsEnabled(GL13.GL_MULTISAMPLE)) {
            GLSMEnable.glEnable(GL13.GL_MULTISAMPLE);
        } else {
            GLSMEnable.glDisable(GL13.GL_MULTISAMPLE);
        }

        GLSMEnable.glDisable(GL11.GL_NORMALIZE);
        GLSMEnable.glDisable(GL11.GL_POINT_SMOOTH);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_POINT);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_LINE);
        GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GLSMEnable.glDisable(GL11.GL_POLYGON_SMOOTH);
        GLSMEnable.glDisable(GL11.GL_POLYGON_STIPPLE);
        GLSMLighting.disableRescaleNormal();
        GLSMEnable.glDisable(GL13.GL_SAMPLE_ALPHA_TO_COVERAGE);
        GLSMEnable.glDisable(GL13.GL_SAMPLE_ALPHA_TO_ONE);
        GLSMEnable.glDisable(GL13.GL_SAMPLE_COVERAGE);
        GLSMRasterizer.disableScissorTest();
        GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);

        // Reset texture state for all units (using cached MAX_TEXTURE_UNITS)
        for(int i = 0; i < CompatLimits.MAX_TEXTURE_UNITS; i++) {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_1D);
            GLSMTextures.disableTexture(); // GL_TEXTURE_2D
            GLSMEnable.glDisable(GL12.GL_TEXTURE_3D);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_S);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_T);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_R);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_GEN_Q);
        }

        // Reset to texture unit 0
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);

        // Reset matrix mode to default
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);

        // Reset color to white
        GLSMCurrentAttribs.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Reset material colors to defaults (fixes GL_COLOR_MATERIAL side effects)
        FloatBuffer materialBuffer = BufferUtils.createFloatBuffer(4);
        materialBuffer.put(0.2F).put(0.2F).put(0.2F).put(1.0F).flip();
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT, materialBuffer);
        materialBuffer.clear();
        materialBuffer.put(0.8F).put(0.8F).put(0.8F).put(1.0F).flip();
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_DIFFUSE, materialBuffer);

        // Reset draw buffer
        GLSMFramebuffer.glDrawBuffer(GLSMFramebuffer.DEFAULT_DRAW_BUFFER);

        // Reset line state
        GLSMRasterizer.glLineWidth(1.0f);

        // Reset point state
        GLSMRasterizer.glPointSize(1.0f);

        // Reset polygon state
        GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GLSMRasterizer.glPolygonOffset(0.0f, 0.0f);
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMRasterizer.glFrontFace(GL11.GL_CCW);

        // Reset stencil state
        GLSMDepthStencilAlpha.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFFFFFFFF);
        GLSMDepthStencilAlpha.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GLSMDepthStencilAlpha.glStencilMask(0xFFFFFFFF);
        GLSMDepthStencilAlpha.glClearStencil(0);
    }


    public static void setClientState(int glCap, boolean state) {
        if (state) {
            GLSMVertexInput.glEnableClientState(glCap);
        } else {
            GLSMVertexInput.glDisableClientState(glCap);
        }
    }
}
