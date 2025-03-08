package me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer;

import static org.lwjgl.system.MemoryUtil.memPutFloat;
import static org.lwjgl.system.MemoryUtil.memPutInt;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.GlyphVertexSink;

public class GlyphVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements GlyphVertexSink {
    public GlyphVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.GLYPHS);
    }

    @Override
    public void writeGlyph(float x, float y, float z, int color, float u, float v, int light) {
        long i = this.writePointer;

        memPutFloat(i, x);
        memPutFloat(i + 4, y);
        memPutFloat(i + 8, z);
        memPutInt(i + 12, color);
        memPutFloat(i + 16, u);
        memPutFloat(i + 20, v);
        memPutInt(i + 24, light);

        this.advance();

    }
}
