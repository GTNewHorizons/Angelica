package me.jellysquid.mods.sodium.client.render.chunk.format.hfp;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.format.DefaultModelVertexFormats;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;

public class HFPModelVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements ModelVertexSink {
    public HFPModelVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, DefaultModelVertexFormats.MODEL_VERTEX_HFP);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light, int shaderBlockId) {
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

        memPutShort(i, x);
        memPutShort(i + 2, y);
        memPutShort(i + 4, z);
        memPutInt(i + 8, color);
        memPutShort(i + 12, u);
        memPutShort(i + 14, v);
        memPutInt(i + 16, light);

        this.advance();
    }

}
