package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The four corners every rain and snow column is expanded from. x picks the end of the billboard, y picks
 * the bottom or top edge.
 */
public final class WeatherQuadMesh {

    public static final int VERTEX_FLAGS = VertexFlags.TEXTURE_BIT;
    public static final int VERTEX_COUNT = 4;

    private static final float[] CORNERS = { 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f };

    private static int vao;
    private static int vbo;

    private WeatherQuadMesh() {}

    public static int vao() {
        if (vao != 0) return vao;

        final ByteBuffer data = ByteBuffer.allocateDirect(CORNERS.length * Float.BYTES).order(ByteOrder.nativeOrder());
        for (float corner : CORNERS) data.putFloat(corner);
        data.flip();

        vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(vao);
        vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);

        final int corner = VertexFormatElement.Usage.PRIMARY_UV.getAttributeLocation();
        GLStateManager.glEnableVertexAttribArray(corner);
        GLStateManager.glVertexAttribPointer(corner, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);
        WeatherInstancedAttribs.enableInstanceArrays();

        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return vao;
    }

    public static void bindInstanceBuffer(int bufferId) {
        GLStateManager.glBindVertexArray(vao());
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        WeatherInstancedAttribs.pointInstanceAttribs(0L);
        GLStateManager.glBindVertexArray(0);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public static void delete() {
        if (vbo != 0) {
            GLStateManager.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            GLStateManager.glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}
