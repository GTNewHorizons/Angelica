package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public final class TemplateCapture {

    private TemplateCapture() {}

    public static TemplateBuffer toTemplate(DirectTessellator direct) {
        final int vertexCount = direct.getVertexCount();
        if (vertexCount == 0) return null;
        final int drawMode = direct.getDrawMode();
        final VertexFormat format = direct.getVertexFormat();
        final ByteBuffer copy = direct.allocateBufferCopy();
        final int[] data = VertexTransform.decode(memAddress0(copy), format, vertexCount, drawMode);
        memFree(copy);
        return new TemplateBuffer(data, vertexCount, drawMode);
    }
}
