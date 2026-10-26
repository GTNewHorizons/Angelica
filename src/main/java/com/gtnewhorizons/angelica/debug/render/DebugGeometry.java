package com.gtnewhorizons.angelica.debug.render;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import org.lwjgl.opengl.GL15;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;

public final class DebugGeometry {

    private DebugGeometry() {}

    public static int createVao(int vbo, VertexFormat format) {
        final int vao = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        format.setupBufferState(0L);
        return vao;
    }

    public static int offsetOf(VertexFormat format, VertexFormatElement.Usage usage) {
        int offset = 0;
        for (final VertexFormatElement element : format.elementsArray) {
            if (element.getUsage() == usage) return offset;
            offset += element.getByteSize();
        }
        throw new IllegalArgumentException("Vertex format has no " + usage);
    }

    public static void putColor(long address, int argb) {
        memPutByte(address, (byte) (argb >> 16));
        memPutByte(address + 1, (byte) (argb >> 8));
        memPutByte(address + 2, (byte) argb);
        memPutByte(address + 3, (byte) (argb >>> 24));
    }

    public static void putPosition(long address, float x, float y, float z) {
        memPutFloat(address, x);
        memPutFloat(address + 4, y);
        memPutFloat(address + 8, z);
    }
}
