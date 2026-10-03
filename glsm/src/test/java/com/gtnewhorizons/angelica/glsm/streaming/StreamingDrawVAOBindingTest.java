package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLCompatTest;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void rawBufferShrinksOnlyWellBelowOneEighthFull() {
        final Tessellator tess = Tessellator.instance;
        final int[] savedRawBuffer = tess.rawBuffer;
        final int savedRawBufferSize = tess.rawBufferSize;
        final int savedRawBufferIndex = tess.rawBufferIndex;
        final int savedVertexCount = tess.vertexCount;
        final int savedDrawMode = tess.drawMode;
        final boolean savedIsDrawing = tess.isDrawing;
        try {
            final int[] oversized = new int[0x40000];
            tess.rawBuffer = oversized;
            tess.rawBufferSize = 0x40000;
            tess.drawMode = GL11.GL_QUADS;
            tess.vertexCount = 4096;
            tess.rawBufferIndex = 0x8000;
            tess.isDrawing = true;

            TessellatorStreamingDrawer.draw(tess);

            assertSame(oversized, tess.rawBuffer, "at exactly 1/8 full, the buffer must not shrink");
            assertEquals(0x40000, tess.rawBufferSize, "at exactly 1/8 full, the buffer size must not shrink");

            tess.drawMode = GL11.GL_QUADS;
            tess.vertexCount = 4;
            tess.rawBufferIndex = 32;
            tess.isDrawing = true;

            TessellatorStreamingDrawer.draw(tess);

            assertEquals(0x10000, tess.rawBufferSize, "well below 1/8 full, the buffer must shrink");
            assertEquals(0x10000, tess.rawBuffer.length, "the shrunk array must match the new size");
        } finally {
            tess.rawBuffer = savedRawBuffer;
            tess.rawBufferSize = savedRawBufferSize;
            tess.rawBufferIndex = savedRawBufferIndex;
            tess.vertexCount = savedVertexCount;
            tess.drawMode = savedDrawMode;
            tess.isDrawing = savedIsDrawing;
        }
    }
}
