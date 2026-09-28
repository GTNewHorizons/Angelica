package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import lombok.Getter;

public class BufferSegment {
    private final RenderLayer type;
    @Getter
    private final int blockEntityId;
    @Getter
    private final int itemId;
    @Getter
    private final int entityId;
    @Getter
    private final int renderedBlockEntityId;
    @Getter
    private final int entityColor;
    @Getter
    private final int firstVertex;
    @Getter
    private final int vertexCount;
    @Getter
    private final SegmentedBufferBuilder.LayerBuffer owner;

    public BufferSegment(RenderLayer type, int blockEntityId, int entityId, int renderedBlockEntityId, int itemId, int entityColor, int firstVertex, int vertexCount, SegmentedBufferBuilder.LayerBuffer owner) {
        this.type = type;
        this.blockEntityId = blockEntityId;
        this.itemId = itemId;
        this.entityId = entityId;
        this.renderedBlockEntityId = renderedBlockEntityId;
        this.entityColor = entityColor;
        this.firstVertex = firstVertex;
        this.vertexCount = vertexCount;
        this.owner = owner;
    }

    public RenderLayer getRenderType() {
        return type;
    }

}
