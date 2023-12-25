package me.jellysquid.mods.sodium.client.gl.attribute;

import com.gtnewhorizons.angelica.compat.toremove.VertexFormat;

public interface BufferVertexFormat {
    static BufferVertexFormat from(VertexFormat format) {
        return (BufferVertexFormat) format;
    }

    int getStride();
}
