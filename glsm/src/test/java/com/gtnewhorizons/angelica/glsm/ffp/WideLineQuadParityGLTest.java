package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@GLCoreTest
class WideLineQuadParityGLTest {

    private static final int SIZE = 64;
    private static final int BACKGROUND = 0xFF000000;

    private boolean savedEmulation;
    private boolean savedVertexShaderLines;

    private static boolean overrideRegistered;
    private static boolean routeAttributes;

    @BeforeAll
    static void registerAttributeRouting() {
        if (overrideRegistered) return;
        overrideRegistered = true;
        VertexFormat.registerSetupBufferStateOverride((format, offset) -> {
            if (!routeAttributes) return false;
            final int stride = format.getVertexSize();
            int elementOffset = (int) offset;
            for (VertexFormatElement element : format.elementsArray) {
                switch (element.getUsage()) {
                    case POSITION -> FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, stride, elementOffset);
                    case NORMAL -> FfpFixture.attrib(4, 3, element.getType().getGlType(), true, stride, elementOffset);
                    default -> { }
                }
                elementOffset += element.getByteSize();
            }
            return true;
        });
    }

    @BeforeEach
    void setUp() {
        assumeTrue(GLStateManager.supportsGeometryShaders());
        routeAttributes = true;
        savedEmulation = GLStateManager.wideLineEmulationEnabled;
        savedVertexShaderLines = GLStateManager.widenLinesInVertexShader;
        GLStateManager.wideLineEmulationEnabled = true;

        FfpFixture.resetFfpState();
        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        GLStateManager.glViewport(0, 0, SIZE, SIZE);
        GLStateManager.glDisable(GL11.GL_DEPTH_TEST);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @AfterEach
    void tearDown() {
        routeAttributes = false;
        GLStateManager.wideLineEmulationEnabled = savedEmulation;
        GLStateManager.widenLinesInVertexShader = savedVertexShaderLines;
        GLStateManager.glLineWidth(1.0f);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        FfpFixture.resetFfpState();
    }

    private static int[] draw(float[] positions, boolean vertexShaderLines) {
        GLStateManager.widenLinesInVertexShader = vertexShaderLines;
        final ByteBuffer packed = BufferUtils.createByteBuffer(positions.length * Float.BYTES);
        packed.asFloatBuffer().put(positions);
        FfpFixture.clear();
        TessellatorStreamingDrawer.drawPacked(packed, GL11.GL_LINES, 0, positions.length / 3);
        return FfpFixture.readRegion(SIZE);
    }

    private static int[] compare(float[] positions) {
        final int[] geometryShader = draw(positions, false);
        final int[] vertexShader = draw(positions, true);
        int different = 0;
        int lit = 0;
        for (int i = 0; i < geometryShader.length; i++) {
            if (geometryShader[i] != BACKGROUND) lit++;
            if (geometryShader[i] != vertexShader[i]) different++;
        }
        return new int[] { different, lit };
    }

    @Test
    void orthographicLinesCoverTheSamePixels() {
        final Random random = new Random(1234);
        for (float width : new float[] { 1.5f, 2.0f, 2.5f, 3.0f, 4.3f, 7.0f }) {
            GLStateManager.glLineWidth(width);
            for (int batch = 0; batch < 8; batch++) {
                final float[] positions = new float[6 * 6];
                for (int i = 0; i < positions.length; i += 3) {
                    positions[i] = (random.nextFloat() * 2.0f - 1.0f) * 0.9f;
                    positions[i + 1] = (random.nextFloat() * 2.0f - 1.0f) * 0.9f;
                }
                final int b = batch;
                assertEquals(0, compare(positions)[0], () -> "width " + width + " batch " + b);
            }
        }
    }

    @Test
    void perspectiveLinesAcrossTheNearPlaneCoverTheSamePixels() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glFrustum(-0.05, 0.05, -0.05, 0.05, 0.05, 100.0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final Random random = new Random(5678);
        int different = 0;
        int lit = 0;
        for (float width : new float[] { 1.5f, 3.0f, 6.0f }) {
            GLStateManager.glLineWidth(width);
            for (int batch = 0; batch < 8; batch++) {
                final float[] positions = new float[6 * 6];
                for (int i = 0; i < positions.length; i += 6) {
                    positions[i] = random.nextFloat() * 2.0f - 1.0f;
                    positions[i + 1] = random.nextFloat() * 2.0f - 1.0f;
                    positions[i + 2] = -0.5f - random.nextFloat() * 3.0f;
                    positions[i + 3] = random.nextFloat() * 2.0f - 1.0f;
                    positions[i + 4] = random.nextFloat() * 2.0f - 1.0f;
                    positions[i + 5] = 1.0f - random.nextFloat() * 3.0f;
                }
                final int[] result = compare(positions);
                different += result[0];
                lit += result[1];
            }
        }
        final int differing = different, covered = lit;
        assertEquals(0, differing, () -> differing + " of " + covered + " lit pixels differ");
    }

    @Test
    void longLinesPastTheCameraCoverTheSamePixels() {
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glFrustum(-0.05, 0.05, -0.05, 0.05, 0.05, 300.0);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        final Random random = new Random(91011);
        int different = 0;
        int lit = 0;
        for (float width : new float[] { 1.5f, 2.0f, 4.0f }) {
            GLStateManager.glLineWidth(width);
            for (int batch = 0; batch < 8; batch++) {
                final float[] positions = new float[6 * 6];
                for (int i = 0; i < positions.length; i += 6) {
                    final float height = random.nextFloat() * 4.0f - 2.0f;
                    positions[i] = random.nextFloat() * 120.0f - 60.0f;
                    positions[i + 1] = height;
                    positions[i + 2] = -60.0f - random.nextFloat() * 20.0f;
                    positions[i + 3] = random.nextFloat() * 120.0f - 60.0f;
                    positions[i + 4] = height + random.nextFloat() * 0.5f;
                    positions[i + 5] = 20.0f + random.nextFloat() * 20.0f;
                }
                final int[] result = compare(positions);
                different += result[0];
                lit += result[1];
            }
        }
        final int differing = different, covered = lit;
        assertEquals(0, differing, () -> differing + " of " + covered + " lit pixels differ");
    }
}
