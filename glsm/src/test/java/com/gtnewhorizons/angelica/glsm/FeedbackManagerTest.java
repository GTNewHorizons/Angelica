package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GLCompatTest
class FeedbackManagerTest {

    @BeforeEach
    void setUp() {
        FeedbackManager.reset();
    }

    @AfterEach
    void tearDown() {
        // Ensure we're back in render mode
        if (FeedbackManager.isFeedbackMode()) {
            FeedbackManager.glRenderMode(GL11.GL_RENDER);
        }
        FeedbackManager.reset();
    }

    @Test
    void testStateMachine_noDraws_returnsZero() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(64);
        FeedbackManager.glFeedbackBuffer(GL11.GL_2D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        assertTrue(FeedbackManager.isFeedbackMode());

        final int result = FeedbackManager.glRenderMode(GL11.GL_RENDER);
        assertEquals(0, result, "No draws should produce 0 tokens");
        assertFalse(FeedbackManager.isFeedbackMode());
    }

    @Test
    void testOverflow_returnsNegative() {
        // Buffer with only 2 floats — any triangle will overflow
        final FloatBuffer buf = BufferUtils.createFloatBuffer(2);
        FeedbackManager.glFeedbackBuffer(GL11.GL_2D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        // Set up identity matrices and a viewport
        GLStateManager.getModelViewMatrix().identity();
        GLStateManager.getProjectionMatrix().identity();
        GLStateManager.getViewportState().setViewPort(0, 0, 800, 600);
        GLStateManager.getViewportState().setDepthRange(0.0, 1.0);

        // Upload 3 vertices to a VBO and draw
        final int vao = GL30.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        final float[] verts = { 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f };
        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(9);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GLStateManager.glEnableVertexAttribArray(0);

        FeedbackManager.processDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        final int result = FeedbackManager.glRenderMode(GL11.GL_RENDER);
        assertEquals(-1, result, "Overflow should return -1");

        // Cleanup
        GLStateManager.glDisableVertexAttribArray(0);
        GLStateManager.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GLStateManager.glDeleteVertexArrays(vao);
    }

    @Test
    void testPassThrough() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(64);
        FeedbackManager.glFeedbackBuffer(GL11.GL_2D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        FeedbackManager.glPassThrough(42.0f);

        final int result = FeedbackManager.glRenderMode(GL11.GL_RENDER);
        assertEquals(2, result, "PassThrough should write 2 floats");
        assertEquals(GL11.GL_PASS_THROUGH_TOKEN, buf.get(0), "First token should be PASS_THROUGH_TOKEN");
        assertEquals(42.0f, buf.get(1), "Second token should be the pass-through value");
    }

    // Identity MVP on an 800x600 viewport: NDC (0,0) -> window (400,300), (1,0) -> (800,300), (0,1) -> (400,600)
    static Stream<Arguments> primitives() {
        return Stream.of(
            Arguments.of("triangle", GL11.GL_TRIANGLES,
                new float[] { 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f },
                new float[] { GL11.GL_POLYGON_TOKEN, 3f, 400f, 300f, 800f, 300f, 400f, 600f }),
            Arguments.of("polygon", GL11.GL_POLYGON,
                new float[] { 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f },
                new float[] { GL11.GL_POLYGON_TOKEN, 4f, 400f, 300f, 800f, 300f, 800f, 600f, 400f, 600f }),
            Arguments.of("quad strip", GL11.GL_QUAD_STRIP,
                new float[] { 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f, 0f },
                new float[] { GL11.GL_POLYGON_TOKEN, 4f, 400f, 300f, 400f, 600f, 800f, 600f, 800f, 300f }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("primitives")
    void testPrimitiveFeedback_GL2D(String name, int mode, float[] verts, float[] expected) {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(128);
        FeedbackManager.glFeedbackBuffer(GL11.GL_2D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        GLStateManager.getModelViewMatrix().identity();
        GLStateManager.getProjectionMatrix().identity();
        GLStateManager.getViewportState().setViewPort(0, 0, 800, 600);
        GLStateManager.getViewportState().setDepthRange(0.0, 1.0);

        final int vao = GL30.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(verts.length);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GLStateManager.glEnableVertexAttribArray(0);

        FeedbackManager.processDrawArrays(mode, 0, verts.length / 3);

        final int count = FeedbackManager.glRenderMode(GL11.GL_RENDER);

        // GL_2D: POLYGON_TOKEN, vertex count, then x, y per vertex
        assertEquals(expected.length, count, name + " float count");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], buf.get(i), 0.01f, name + " feedback value " + i);
        }

        GLStateManager.glDisableVertexAttribArray(0);
        GLStateManager.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GLStateManager.glDeleteVertexArrays(vao);
    }

    @Test
    void testIdentityTransform_viewportMapping() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(64);
        FeedbackManager.glFeedbackBuffer(GL11.GL_3D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        GLStateManager.getModelViewMatrix().identity();
        GLStateManager.getProjectionMatrix().identity();
        GLStateManager.getViewportState().setViewPort(0, 0, 800, 600);
        GLStateManager.getViewportState().setDepthRange(0.0, 1.0);

        final int vao = GL30.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);

        // Single point at NDC origin (0,0,0)
        final float[] verts = { 0f, 0f, 0f };
        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(3);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GLStateManager.glEnableVertexAttribArray(0);

        FeedbackManager.processDrawArrays(GL11.GL_POINTS, 0, 1);

        final int count = FeedbackManager.glRenderMode(GL11.GL_RENDER);

        // GL_3D point: POINT_TOKEN, x, y, z = 4 floats
        assertEquals(4, count);
        assertEquals(GL11.GL_POINT_TOKEN, buf.get(0), 0.001f);
        // NDC(0,0,0) → window center (400, 300, 0.5)
        assertEquals(400.0f, buf.get(1), 0.01f);
        assertEquals(300.0f, buf.get(2), 0.01f);
        assertEquals(0.5f, buf.get(3), 0.01f);

        // Cleanup
        GLStateManager.glDisableVertexAttribArray(0);
        GLStateManager.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GLStateManager.glDeleteVertexArrays(vao);
    }
}
