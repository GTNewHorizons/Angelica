package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLCoreTest;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.testutil.ClipTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCoreTest
class ClipPlaneGLTest {

    private static final int SIZE = 64;
    private static final int BACKGROUND = 0xFF000000;
    private static final int QUAD = 0xFF0000FF;

    @BeforeEach
    void setUp() {
        FfpFixture.resetFfpState();
        FfpFixture.activateFfp();
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
    }

    @AfterEach
    void tearDown() {
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }
        FfpFixture.resetFfpState();
    }

    @ParameterizedTest(name = "emulated={0}")
    @ValueSource(booleans = {false, true})
    void disabledPlaneDoesNotClip(boolean emulated) {
        try (var ignored = ClipTestSupport.emulation(emulated)) {
            ClipTestSupport.setPlane(1, 0, 0, 0, -1);
            GLSMEnable.glEnable(GL11.GL_CLIP_PLANE1);
            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE1);
            ClipTestSupport.setPlane(0, 1, 0, 0, 0);
            GLSMEnable.glEnable(GL11.GL_CLIP_PLANE0);
            assertRightHalfOnly(drawQuad(), "plane1 disabled, plane0 enabled");
        }
    }

    @ParameterizedTest(name = "emulated={0}")
    @ValueSource(booleans = {false, true})
    void attribPopRestoresClipping(boolean emulated) {
        try (var ignored = ClipTestSupport.emulation(emulated)) {
            ClipTestSupport.setPlane(0, 1, 0, 0, 0);
            GLSMEnable.glEnable(GL11.GL_CLIP_PLANE0);
            assertDriverClipPlane0(!emulated, "after glEnable");
            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            GLSMEnable.glDisable(GL11.GL_CLIP_PLANE0);
            final int[] unclipped = drawQuad();
            GLSMAttribStack.glPopAttrib();
            assertDriverClipPlane0(!emulated, "after glPopAttrib");
            GLSMAttribStack.replayStateToBackend();
            assertDriverClipPlane0(!emulated, "after replayStateToBackend");
            final int[] restored = drawQuad();

            for (int i = 0; i < unclipped.length; i++) {
                final int px = i;
                assertEquals(QUAD, unclipped[i], () -> "plane disabled inside the pushed scope, pixel " + px % SIZE + "," + px / SIZE);
            }
            assertRightHalfOnly(restored, "after glPopAttrib");
        }
    }

    private static void assertDriverClipPlane0(boolean expected, String when) {
        assertEquals(expected, GL11.glIsEnabled(GL11.GL_CLIP_PLANE0), () -> when + ": clip plane reaches the driver only when clip distances are native");
    }

    private static void assertRightHalfOnly(int[] pixels, String what) {
        for (int i = 0; i < pixels.length; i++) {
            final int x = i % SIZE, y = i / SIZE;
            assertEquals(x < SIZE / 2 ? BACKGROUND : QUAD, pixels[i], () -> what + ": first mismatch at pixel " + x + "," + y);
        }
    }

    private static int[] drawQuad() {
        final int vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        final int vbo = GLSMBuffers.glGenBuffers();
        try {
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            final FloatBuffer positions = BufferUtils.createFloatBuffer(18);
            positions.put(new float[] { -1, -1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, 1, 1, 0, -1, 1, 0 }).flip();
            GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, positions, GL15.GL_STATIC_DRAW);
            FfpFixture.attrib(0, 3, GL11.GL_FLOAT, false, 12, 0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            VAOManager.setCurrentVertexFlags(0);

            FfpFixture.clear();
            GLSMCurrentAttribs.glColor4f(1.0f, 0.0f, 0.0f, 1.0f);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GLSMVertexInput.glBindVertexArray(0);
            return FfpFixture.readRegion(SIZE);
        } finally {
            GLSMBuffers.glDeleteBuffers(vbo);
            GLSMVertexInput.glDeleteVertexArrays(vao);
        }
    }
}
