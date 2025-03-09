package org.taumc.celeritas.impl.render.terrain;

import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

public class ArchaicChunkRenderer extends DefaultChunkRenderer {
    public ArchaicChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }
}
