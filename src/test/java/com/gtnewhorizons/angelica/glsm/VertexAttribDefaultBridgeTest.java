package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.AngelicaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AngelicaExtension.class)
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

    @Test
    void testGlColor4fSetsVertexAttrib() {
        GLStateManager.glColor4f(0.5f, 0.25f, 0.75f, 0.8f);
        GLStateManager.flushDeferredVertexAttribs();

        float[] color = getVertexAttrib4f(COLOR_ATTRIB);
        assertEquals(0.5f, color[0], EPSILON, "color.r");
        assertEquals(0.25f, color[1], EPSILON, "color.g");
        assertEquals(0.75f, color[2], EPSILON, "color.b");
        assertEquals(0.8f, color[3], EPSILON, "color.a");
    }

    @Test
    void testGlNormal3fSetsVertexAttrib() {
        GLStateManager.glNormal3f(0.0f, 1.0f, 0.0f);
        GLStateManager.flushDeferredVertexAttribs();

        float[] normal = getVertexAttrib4f(NORMAL_ATTRIB);
        assertEquals(0.0f, normal[0], EPSILON, "normal.x");
        assertEquals(1.0f, normal[1], EPSILON, "normal.y");
        assertEquals(0.0f, normal[2], EPSILON, "normal.z");
    }

    @Test
    void testSetLightmapTextureCoordsOnUnit1SetsVertexAttrib() {
        GLStateManager.setLightmapTextureCoords(GL13.GL_TEXTURE1, 240.0f, 128.0f);
        GLStateManager.flushDeferredVertexAttribs();

        float[] lm = getVertexAttrib4f(SECONDARY_UV_ATTRIB);
        assertEquals(240.0f, lm[0], EPSILON, "lightmap.s");
        assertEquals(128.0f, lm[1], EPSILON, "lightmap.t");
        assertEquals(0.0f, lm[2], EPSILON, "lightmap.r");
        assertEquals(1.0f, lm[3], EPSILON, "lightmap.q");
    }

    @Test
    void testGlTexCoord2fSetsVertexAttrib() {
        // glTexCoord2f outside recording → sets default vertex attrib 2 (PRIMARY_UV)
        GLStateManager.glTexCoord2f(0.5f, 0.75f);
        GLStateManager.flushDeferredVertexAttribs();

        float[] uv = getVertexAttrib4f(PRIMARY_UV_ATTRIB);
        assertEquals(0.5f, uv[0], EPSILON, "texcoord.s");
        assertEquals(0.75f, uv[1], EPSILON, "texcoord.t");
    }

    @Test
    void testCacheDedup_SameColorTwice_AttribStillCorrect() {
        GLStateManager.glColor4f(0.3f, 0.6f, 0.9f, 1.0f);
        // Second call is a cache hit — changeColor not called.
        // Vertex attrib should still hold the correct value from the first call.
        GLStateManager.glColor4f(0.3f, 0.6f, 0.9f, 1.0f);
        GLStateManager.flushDeferredVertexAttribs();

        float[] color = getVertexAttrib4f(COLOR_ATTRIB);
        assertEquals(0.3f, color[0], EPSILON, "color.r after dedup");
        assertEquals(0.6f, color[1], EPSILON, "color.g after dedup");
        assertEquals(0.9f, color[2], EPSILON, "color.b after dedup");
        assertEquals(1.0f, color[3], EPSILON, "color.a after dedup");
    }

    @Test
    void testCompileMode_DoesNotSetVertexAttrib() {
        GLStateManager.glColor4f(0.1f, 0.2f, 0.3f, 1.0f);
        GLStateManager.flushDeferredVertexAttribs();

        int listId = GLStateManager.glGenLists(1);
        GLStateManager.glNewList(listId, org.lwjgl.opengl.GL11.GL_COMPILE);

        // Color during COMPILE should NOT reach changeColor (early return)
        GLStateManager.glColor4f(0.9f, 0.8f, 0.7f, 0.6f);

        float[] color = getVertexAttrib4f(COLOR_ATTRIB);
        assertEquals(0.1f, color[0], EPSILON, "color.r unchanged during COMPILE");
        assertEquals(0.2f, color[1], EPSILON, "color.g unchanged during COMPILE");
        assertEquals(0.3f, color[2], EPSILON, "color.b unchanged during COMPILE");
        assertEquals(1.0f, color[3], EPSILON, "color.a unchanged during COMPILE");

        GLStateManager.glEndList();
        GLStateManager.glDeleteLists(listId, 1);
    }
}
