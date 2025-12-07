package com.gtnewhorizons.angelica.glsm.recording;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.recording.commands.DrawCommand;
import me.jellysquid.mods.sodium.client.gl.attribute.BufferVertexFormat;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Represents recorded geometry from a display list compilation.
 * Contains the vertex data buffer and metadata about draw commands.
 */
@Desugar
public record RecordedGeometry(ByteBuffer buffer, BufferVertexFormat format, List<DrawCommand> drawCommands, int totalVertexCount) {
    public int getByteSize() {
        return buffer.remaining();
    }
}
