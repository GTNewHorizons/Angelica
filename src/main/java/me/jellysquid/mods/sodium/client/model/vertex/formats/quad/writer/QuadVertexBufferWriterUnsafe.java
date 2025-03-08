package me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer;

import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.system.MemoryUtil.memPutInt;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;

public class QuadVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements QuadVertexSink {
    public QuadVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.QUADS);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int normal) {
        final long i = this.writePointer;

        memPutFloat(i, x);
        memPutFloat(i + 4, y);
        memPutFloat(i + 8, z);
        memPutInt(i + 12, color);
        memPutFloat(i + 16, u);
        memPutFloat(i + 20, v);
        memPutInt(i + 24, overlay);
        memPutInt(i + 28, light);
        memPutInt(i + 32, normal);

        this.advance();
    }
}
