package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@GLCompatTest
public class StreamingDrawVAOBindingTest {

    private static final int FLAGS = VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.NORMAL_BIT | VertexFlags.BRIGHTNESS_BIT;

    private static ByteBuffer triangle() {
        final int vertexSize = DefaultVertexFormat.ALL_FORMATS[FLAGS].getVertexSize();
        final ByteBuffer buf = memAlloc(vertexSize * 3);
        for (int i = 0; i < buf.capacity(); i++) buf.put(i, (byte) 0);
        buf.position(0).limit(buf.capacity());
        return buf;
    }

    @Test
    void consecutiveDrawsKeepOneVaoBound() {
        final ByteBuffer data = triangle();
        try {
            TessellatorStreamingDrawer.drawPacked(data, GL11.GL_TRIANGLES, FLAGS, 3);
            final int afterFirst = GLStateManager.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            assertNotEquals(0, afterFirst, "a streaming draw must leave its own VAO bound, not VAO 0");

            data.position(0).limit(data.capacity());
            TessellatorStreamingDrawer.drawPacked(data, GL11.GL_TRIANGLES, FLAGS, 3);
            final int afterSecond = GLStateManager.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            assertEquals(afterFirst, afterSecond, "the same format must reuse the same VAO across draws");

            assertEquals(afterSecond, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), "GLSM's cached binding must match the driver's");
        } finally {
            memFree(data);
        }
    }
}
