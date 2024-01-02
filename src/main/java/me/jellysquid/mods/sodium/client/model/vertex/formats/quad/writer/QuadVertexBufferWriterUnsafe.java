package me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer;

import com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil;
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

        CompatMemoryUtil.memPutFloat(i, x);
        CompatMemoryUtil.memPutFloat(i + 4, y);
        CompatMemoryUtil.memPutFloat(i + 8, z);
        CompatMemoryUtil.memPutInt(i + 12, color);
        CompatMemoryUtil.memPutFloat(i + 16, u);
        CompatMemoryUtil.memPutFloat(i + 20, v);
        CompatMemoryUtil.memPutInt(i + 24, overlay);
        CompatMemoryUtil.memPutInt(i + 28, light);
        CompatMemoryUtil.memPutInt(i + 32, normal);

        this.advance();
    }
}
