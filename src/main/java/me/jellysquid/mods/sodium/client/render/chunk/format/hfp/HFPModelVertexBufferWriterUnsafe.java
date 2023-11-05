package me.jellysquid.mods.sodium.client.render.chunk.format.hfp;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.format.DefaultModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;
import org.lwjgl.system.MemoryUtil;

public class HFPModelVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements ModelVertexSink {
    public HFPModelVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, DefaultModelVertexFormats.MODEL_VERTEX_HFP);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light) {
        this.writeQuadInternal(
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(x),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(y),
                ModelVertexUtil.denormalizeVertexPositionFloatAsShort(z),
                color,
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(u),
                ModelVertexUtil.denormalizeVertexTextureFloatAsShort(v),
                ModelVertexUtil.encodeLightMapTexCoord(light)
        );
    }

    private void writeQuadInternal(short x, short y, short z, int color, short u, short v, int light) {
        long i = this.writePointer;

        MemoryUtil.memPutShort(i, x);
        MemoryUtil.memPutShort(i + 2, y);
        MemoryUtil.memPutShort(i + 4, z);
        MemoryUtil.memPutInt(i + 8, color);
        MemoryUtil.memPutShort(i + 12, u);
        MemoryUtil.memPutShort(i + 14, v);
        MemoryUtil.memPutInt(i + 16, light);

        this.advance();
    }

}
