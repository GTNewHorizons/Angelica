package me.jellysquid.mods.sodium.client.model.vertex.formats.line.writer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.line.LineVertexSink;

public class LineVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements LineVertexSink {
    public LineVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.LINES);
    }

    @Override
    public void vertexLine(float x, float y, float z, int color) {
        long i = this.writePointer;

        memPutFloat(i, x);
        memPutFloat(i + 4, y);
        memPutFloat(i + 8, z);
        memPutInt(i + 12, color);

        this.advance();
    }
}
