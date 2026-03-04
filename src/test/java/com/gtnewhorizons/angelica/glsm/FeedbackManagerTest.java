package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AngelicaExtension.class)
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
        GL30.glBindVertexArray(vao);

        final float[] verts = { 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f };
        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(9);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GL20.glEnableVertexAttribArray(0);

        FeedbackManager.processDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        final int result = FeedbackManager.glRenderMode(GL11.GL_RENDER);
        assertEquals(-1, result, "Overflow should return -1");

        // Cleanup
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
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

    @Test
    void testTriangleFeedback_GL2D() {
        final FloatBuffer buf = BufferUtils.createFloatBuffer(128);
        FeedbackManager.glFeedbackBuffer(GL11.GL_2D, buf);
        FeedbackManager.glRenderMode(GL11.GL_FEEDBACK);

        // Identity MV/proj, viewport (0,0,800,600)
        GLStateManager.getModelViewMatrix().identity();
        GLStateManager.getProjectionMatrix().identity();
        GLStateManager.getViewportState().setViewPort(0, 0, 800, 600);
        GLStateManager.getViewportState().setDepthRange(0.0, 1.0);

        // Upload 3 vertices forming a triangle
        final int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // NDC vertices: (0,0,0), (1,0,0), (0,1,0)
        // With identity MVP and viewport 800x600:
        //   NDC(0,0) → window(400, 300)
        //   NDC(1,0) → window(800, 300)
        //   NDC(0,1) → window(400, 600)
        final float[] verts = { 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f };
        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(9);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GL20.glEnableVertexAttribArray(0);

        FeedbackManager.processDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        final int count = FeedbackManager.glRenderMode(GL11.GL_RENDER);

        // GL_2D: POLYGON_TOKEN, 3, x0, y0, x1, y1, x2, y2 = 8 floats
        assertEquals(8, count, "Triangle in GL_2D should produce 8 floats");

        assertEquals(GL11.GL_POLYGON_TOKEN, buf.get(0), 0.001f);
        assertEquals(3.0f, buf.get(1), 0.001f);

        // Vertex 0: NDC(0,0) → window(400, 300)
        assertEquals(400.0f, buf.get(2), 0.01f);
        assertEquals(300.0f, buf.get(3), 0.01f);

        // Vertex 1: NDC(1,0) → window(800, 300)
        assertEquals(800.0f, buf.get(4), 0.01f);
        assertEquals(300.0f, buf.get(5), 0.01f);

        // Vertex 2: NDC(0,1) → window(400, 600)
        assertEquals(400.0f, buf.get(6), 0.01f);
        assertEquals(600.0f, buf.get(7), 0.01f);

        // Cleanup
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
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
        GL30.glBindVertexArray(vao);

        // Single point at NDC origin (0,0,0)
        final float[] verts = { 0f, 0f, 0f };
        final FloatBuffer vertBuf = BufferUtils.createFloatBuffer(3);
        vertBuf.put(verts).flip();

        final int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0);
        GL20.glEnableVertexAttribArray(0);

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
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
        GL30.glDeleteVertexArrays(vao);
    }
}
