package me.jellysquid.mods.sodium.client.model.vertex.type;

import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.BufferVertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;

public interface VanillaVertexType<T extends VertexSink> extends BufferVertexType<T> {
    default BufferVertexFormat getBufferVertexFormat() {
        return BufferVertexFormat.from(this.getVertexFormat());
    }

    VertexFormat getVertexFormat();
}
