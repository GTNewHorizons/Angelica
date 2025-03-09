package org.embeddedt.embeddium.impl.render.chunk.compile;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;

public class ChunkBuildContext {
    public final ChunkBuildBuffers buffers;

    public ChunkBuildContext(RenderPassConfiguration renderPassConfiguration) {
        this.buffers = new ChunkBuildBuffers(renderPassConfiguration);
    }

    public void cleanup() {
        this.buffers.destroy();
    }
}
