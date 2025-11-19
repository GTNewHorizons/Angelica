package me.jellysquid.mods.sodium.client.render.chunk.compile.buffers;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;

public class FallbackChunkModelBuffers implements ChunkModelBuffers {
    public FallbackChunkModelBuffers() {

    }

    @Override
    public ModelVertexSink getSink(ModelQuadFacing facing) {
        return null;
    }

    @Override
    public ChunkRenderData.Builder getRenderData() {
        return null;
    }
}
