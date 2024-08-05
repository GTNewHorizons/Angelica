package me.jellysquid.mods.sodium.client.gl.attribute;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;

public interface BufferVertexFormat {
    static BufferVertexFormat from(VertexFormat format) {
        return (BufferVertexFormat) format;
    }

    int getStride();
}
