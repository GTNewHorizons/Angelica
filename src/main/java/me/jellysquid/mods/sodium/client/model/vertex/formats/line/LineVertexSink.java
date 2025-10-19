package me.jellysquid.mods.sodium.client.model.vertex.formats.line;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;

public interface LineVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_COLOR;

}
