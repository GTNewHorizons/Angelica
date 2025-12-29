package com.gtnewhorizons.angelica.rendering.celeritas.iris;

import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

public interface ContextAwareChunkVertexEncoder extends ChunkVertexEncoder {

    void prepareToRenderBlock(BlockRenderContext ctx, Block block, short renderType, byte lightValue);

    void prepareToRenderFluid(BlockRenderContext ctx, Block block, byte lightValue);

    void finishRenderingBlock();
}
