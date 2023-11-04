package me.jellysquid.mods.sodium.client.model.vertex.formats.line.writer;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.line.LineVertexSink;
import org.lwjgl.system.MemoryUtil;

public class LineVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements LineVertexSink {
    public LineVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.LINES);
    }

    @Override
    public void vertexLine(float x, float y, float z, int color) {
        long i = this.writePointer;

        MemoryUtil.memPutFloat(i, x);
        MemoryUtil.memPutFloat(i + 4, y);
        MemoryUtil.memPutFloat(i + 8, z);
        MemoryUtil.memPutInt(i + 12, color);

        this.advance();
    }
}
