package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GLCompatTest
class VertexAttribDefaultBridgeTest {

    private static final int COLOR_ATTRIB = 1;        // VertexFormatElement.Usage.COLOR
    private static final int PRIMARY_UV_ATTRIB = 2;   // VertexFormatElement.Usage.PRIMARY_UV
    private static final int SECONDARY_UV_ATTRIB = 3; // VertexFormatElement.Usage.SECONDARY_UV
    private static final int NORMAL_ATTRIB = 4;       // VertexFormatElement.Usage.NORMAL
    private static final float EPSILON = 1e-5f;

    private float[] getVertexAttrib4f(int index) {
        FloatBuffer buf = BufferUtils.createFloatBuffer(4);
        GL20.glGetVertexAttrib(index, GL20.GL_CURRENT_VERTEX_ATTRIB, buf);
        return new float[] { buf.get(0), buf.get(1), buf.get(2), buf.get(3) };
    }

    private void assertAttrib(int index, String label, float... expected) {
        final float[] actual = getVertexAttrib4f(index);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], EPSILON, label + "[" + i + "]");
        }
    }

    @Test
    void testGlColor4fSetsVertexAttrib() {
        GLStateManager.glColor4f(0.5f, 0.25f, 0.75f, 0.8f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        assertAttrib(COLOR_ATTRIB, "color", 0.5f, 0.25f, 0.75f, 0.8f);
    }

    @Test
    void testGlNormal3fSetsVertexAttrib() {
        GLStateManager.glNormal3f(0.0f, 1.0f, 0.0f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        assertAttrib(NORMAL_ATTRIB, "normal", 0.0f, 1.0f, 0.0f);
    }

    @Test
    void testSetLightmapTextureCoordsOnUnit1SetsVertexAttrib() {
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, 240.0f, 128.0f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        assertAttrib(SECONDARY_UV_ATTRIB, "lightmap", 240.0f, 128.0f, 0.0f, 1.0f);
    }

    @Test
    void testGlTexCoord2fSetsVertexAttrib() {
        // glTexCoord2f outside recording → sets default vertex attrib 2 (PRIMARY_UV)
        GLStateManager.glTexCoord2f(0.5f, 0.75f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        assertAttrib(PRIMARY_UV_ATTRIB, "texcoord", 0.5f, 0.75f);
    }

    @Test
    void testCacheDedup_SameColorTwice_AttribStillCorrect() {
        GLStateManager.glColor4f(0.3f, 0.6f, 0.9f, 1.0f);
        // Second call is a cache hit — changeColor not called.
        // Vertex attrib should still hold the correct value from the first call.
        GLStateManager.glColor4f(0.3f, 0.6f, 0.9f, 1.0f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        assertAttrib(COLOR_ATTRIB, "color after dedup", 0.3f, 0.6f, 0.9f, 1.0f);
    }

    @Test
    void testCompileMode_DoesNotSetVertexAttrib() {
        GLStateManager.glColor4f(0.1f, 0.2f, 0.3f, 1.0f);
        GLStateManager.flushDeferredVertexAttribs(GLStateManager.ctx(), false, false, false, false);

        int listId = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(listId, GL11.GL_COMPILE);

        // Color during COMPILE should NOT reach changeColor (early return)
        GLStateManager.glColor4f(0.9f, 0.8f, 0.7f, 0.6f);

        assertAttrib(COLOR_ATTRIB, "color unchanged during COMPILE", 0.1f, 0.2f, 0.3f, 1.0f);

        GLStateManager.glEndList();
        GLStateManager.glDeleteLists(listId, 1);
    }
}
