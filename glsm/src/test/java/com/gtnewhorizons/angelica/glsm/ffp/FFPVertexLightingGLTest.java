package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags.COLOR_BIT;
import static com.gtnewhorizons.angelica.glsm.GlTestSupport.floats;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCoreTest
class FFPVertexLightingGLTest {

    private static final int POSITION_LOC = VertexFormatElement.Usage.POSITION.getAttributeLocation();
    private static final int COLOR_LOC = VertexFormatElement.Usage.COLOR.getAttributeLocation();
    private static final int STRIDE = 16;

    private static final int VERTEX_COLOR = 0xFF808080;
    private static final float VERTEX_CHANNEL = 0x80 / 255.0f;

    private static final Vector3f IDENTITY_FACTOR = new Vector3f(1.0f, 1.0f, 1.0f);

    private final Vector3f factor = new Vector3f();

    private int vao;
    private int vbo;

    @BeforeEach
    void setUpState() {
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMRasterizer.glViewport(0, 0, 800, 600);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMRasterizer.disableCull();
        GLSMBlend.disableBlend();
        GLSMTextures.disableTexture();
        GLSMCurrentAttribs.glNormal3f(0.0f, 0.0f, 1.0f);
        standardItemLighting();

        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_EMISSION, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_SPECULAR, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLSMLighting.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 0.0f);
    }

    @AfterEach
    void cleanup() {
        GLSMEnable.glDisable(GL11.GL_NORMALIZE);
        GLSMEnable.glDisable(GL12.GL_RESCALE_NORMAL);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        GLSMEnable.glDisable(GL11.GL_LIGHT0);
        GLSMEnable.glDisable(GL11.GL_LIGHT1);
        GLSMEnable.glDisable(GL11.GL_LIGHT2);
        GLSMEnable.glDisable(GL11.GL_COLOR_MATERIAL);
        final ShaderManager sm = ShaderManager.getInstance();
        if (sm.isActive()) sm.deactivate();
        GLSMVertexInput.glBindVertexArray(0);
        if (vbo != 0) {
            GLSMBuffers.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GLSMVertexInput.glDeleteVertexArrays(vao);
            vao = 0;
        }
    }

    private static void standardItemLighting() {
        GLSMEnable.glEnable(GL11.GL_LIGHTING);
        GLSMEnable.glEnable(GL11.GL_LIGHT0);
        GLSMEnable.glEnable(GL11.GL_LIGHT1);
        GLSMEnable.glEnable(GL11.GL_COLOR_MATERIAL);
        GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, floats(0.4f, 0.4f, 0.4f, 1.0f));
        light(GL11.GL_LIGHT0, 0.2f, 1.0f, -0.7f);
        light(GL11.GL_LIGHT1, -0.2f, 1.0f, 0.7f);
    }

    private static void light(int light, float x, float y, float z) {
        GLSMLighting.glLight(light, GL11.GL_POSITION, floats(x, y, z, 0.0f));
        GLSMLighting.glLight(light, GL11.GL_DIFFUSE, floats(0.6f, 0.6f, 0.6f, 1.0f));
        GLSMLighting.glLight(light, GL11.GL_AMBIENT, floats(0.0f, 0.0f, 0.0f, 1.0f));
        GLSMLighting.glLight(light, GL11.GL_SPECULAR, floats(0.0f, 0.0f, 0.0f, 1.0f));
    }

    private static String gateState() {
        final var material = GLSMLighting.getFrontMaterial();
        return "lighting=" + GLSMLighting.getLightingState().isEnabled()
            + " colorMaterial=" + GLSMLighting.getColorMaterial().isEnabled()
            + " cmParam=0x" + Integer.toHexString(GLSMLighting.getColorMaterialParameter().getValue())
            + " emission=" + material.emission
            + " specular=" + material.specular
            + " shininess=" + material.shininess
            + " lmAmbient=" + GLSMLighting.getLightModel().ambient
            + " light0=" + GLSMLighting.getLightStates()[0].isEnabled()
            + " light1=" + GLSMLighting.getLightStates()[1].isEnabled();
    }

    private void buildTriangle() {
        vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);

        final ByteBuffer data = BufferUtils.createByteBuffer(3 * STRIDE);
        putVertex(data, -0.9f, -0.9f);
        putVertex(data, 0.9f, -0.9f);
        putVertex(data, 0.0f, 0.9f);
        data.flip();

        vbo = GLSMBuffers.glGenBuffers();
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        GLSMVertexInput.glEnableVertexAttribArray(POSITION_LOC);
        GLSMVertexInput.glVertexAttribPointer(POSITION_LOC, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GLSMVertexInput.glEnableVertexAttribArray(COLOR_LOC);
        GLSMVertexInput.glVertexAttribPointer(COLOR_LOC, 4, GL11.GL_UNSIGNED_BYTE, true, STRIDE, 12L);
        VAOManager.setCurrentVertexFlags(COLOR_BIT);
    }

    private static void putVertex(ByteBuffer buf, float x, float y) {
        final int base = buf.position();
        buf.putFloat(base, x).putFloat(base + 4, y).putFloat(base + 8, 0.0f);
        buf.putInt(base + 12, VERTEX_COLOR);
        buf.position(base + STRIDE);
    }

    private float[] drawAndReadCenter() {
        buildTriangle();
        final ShaderManager sm = ShaderManager.getInstance();
        ShaderManager.enable();
        sm.activate();

        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "FFP draw must not raise a GL error");

        final int[] pixel = FfpFixture.readPixel(400, 200);
        return new float[] { pixel[0] / 255.0f, pixel[1] / 255.0f, pixel[2] / 255.0f };
    }

    private void assertSolverMatchesShader(String context) {
        assertTrue(FFPVertexLighting.modulatesVertexColor(factor), context + ": expected a representable factor");
        final float[] actual = drawAndReadCenter();
        final float[] expected = { Math.min(1.0f, VERTEX_CHANNEL * factor.x), Math.min(1.0f, VERTEX_CHANNEL * factor.y), Math.min(1.0f, VERTEX_CHANNEL * factor.z) };
        for (int c = 0; c < 3; c++) {
            assertEquals(expected[c], actual[c], 2.0f / 255.0f, context + ": channel " + c + " solver=" + expected[c] + " shader=" + actual[c] + " factor=" + factor);
        }
    }

    static Stream<Arguments> normals() {
        return Stream.of(
            Arguments.of("face-on normal", 0.0f, 0.0f, 1.0f, Float.POSITIVE_INFINITY),
            // A 0.0104-long normal must stay at the 0.4 light model ambient floor rather than pick up 0.6 per light
            Arguments.of("sign normal", 0.0f, 0.0f, -0.010416667f, 0.45f),
            Arguments.of("tilted normal", 0.3f, 0.8f, 0.5f, Float.POSITIVE_INFINITY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normals")
    void normalMatchesGeneratedShader(String name, float x, float y, float z, float factorCeiling) {
        GLSMCurrentAttribs.glNormal3f(x, y, z);
        assertSolverMatchesShader(name);
        assertTrue(factor.x < factorCeiling, name + ": factor must stay below " + factorCeiling + ", got " + factor.x);
    }

    @Test
    void rescaleNormalMatchesGeneratedShader() {
        GLSMEnable.glEnable(GL12.GL_RESCALE_NORMAL);
        GLSMMatrices.glScalef(1.0f, 2.0f, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 1.0f, 0.0f);
        assertSolverMatchesShader("rescale normal");
    }

    @Test
    void normalizeMatchesGeneratedShader() {
        GLSMEnable.glEnable(GL11.GL_NORMALIZE);
        GLSMMatrices.glScalef(1.0f, 2.0f, 1.0f);
        GLSMCurrentAttribs.glNormal3f(0.0f, 1.0f, 0.0f);
        assertSolverMatchesShader("normalize");
        assertTrue(factor.x > 1.2f, "normalize restores the unit normal, which must pick up more diffuse than rescale's 0.5-long one, got " + factor.x);
    }

    static Stream<Arguments> unrepresentableStates() {
        return Stream.of(
            unrepresentable("emission is additive, so the lit colour is not vertexColor * factor",
                () -> GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_EMISSION, floats(0.1f, 0.1f, 0.1f, 1.0f))),
            unrepresentable("specular is additive, so the lit colour is not vertexColor * factor", () -> {
                GLSMLighting.glMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_SPECULAR, floats(1.0f, 1.0f, 1.0f, 1.0f));
                GLSMLighting.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, 16.0f);
                GLSMLighting.glLight(GL11.GL_LIGHT0, GL11.GL_SPECULAR, floats(1.0f, 1.0f, 1.0f, 1.0f));
            }),
            unrepresentable("only AMBIENT_AND_DIFFUSE makes the lit colour scale with the vertex colour",
                () -> GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_EMISSION)));
    }

    private static Arguments unrepresentable(String reason, Runnable state) {
        return Arguments.of(reason, state);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unrepresentableStates")
    void additiveLightingIsNotRepresentable(String reason, Runnable state) {
        state.run();
        assertFalse(FFPVertexLighting.modulatesVertexColor(factor), reason);
        assertEquals(IDENTITY_FACTOR, factor, "a rejected solve must leave an identity factor");
    }

    @Test
    void lightsBeyondTheFfpCountAreIgnored() {
        assertTrue(FFPVertexLighting.modulatesVertexColor(factor), FFPVertexLightingGLTest::gateState);
        final Vector3f twoLights = new Vector3f(factor);

        light(GL11.GL_LIGHT2, 0.0f, 0.0f, 1.0f);
        GLSMEnable.glEnable(GL11.GL_LIGHT2);
        assertTrue(FFPVertexLighting.modulatesVertexColor(factor), FFPVertexLightingGLTest::gateState);

        assertEquals(twoLights, factor, "VertexShaderGenerator emits only GL_LIGHT0/GL_LIGHT1, so text must not see GL_LIGHT2 either");
    }
}
