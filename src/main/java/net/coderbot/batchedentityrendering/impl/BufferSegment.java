package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;

public class BufferSegment {
    private final RenderLayer type;
    private final int blockEntityId;
    private final int firstVertex;
    private final int vertexCount;
    private final SegmentedBufferBuilder.LayerBuffer owner;

    public BufferSegment(RenderLayer type, int blockEntityId, int firstVertex, int vertexCount, SegmentedBufferBuilder.LayerBuffer owner) {
        this.type = type;
        this.blockEntityId = blockEntityId;
        this.firstVertex = firstVertex;
        this.vertexCount = vertexCount;
        this.owner = owner;
    }

    public RenderLayer getRenderType() {
        return type;
    }

    public int getBlockEntityId() {
        return blockEntityId;
    }

    public int getFirstVertex() {
        return firstVertex;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public SegmentedBufferBuilder.LayerBuffer getOwner() {
        return owner;
    }
}
