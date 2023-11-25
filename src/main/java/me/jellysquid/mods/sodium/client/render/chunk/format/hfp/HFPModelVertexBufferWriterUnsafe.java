package me.jellysquid.mods.sodium.client.render.chunk.format.hfp;

import com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil;
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

        CompatMemoryUtil.memPutShort(i, x);
        CompatMemoryUtil.memPutShort(i + 2, y);
        CompatMemoryUtil.memPutShort(i + 4, z);
        CompatMemoryUtil.memPutInt(i + 8, color);
        CompatMemoryUtil.memPutShort(i + 12, u);
        CompatMemoryUtil.memPutShort(i + 14, v);
        CompatMemoryUtil.memPutInt(i + 16, light);

        this.advance();
    }

}
