package me.jellysquid.mods.sodium.client.render.chunk.compile.buffers;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;

public interface ChunkModelBuffers {
    ModelVertexSink getSink(ModelQuadFacing facing);

    @Deprecated
    ChunkRenderData.Builder getRenderData();
}
