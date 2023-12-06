package com.gtnewhorizons.angelica.compat.mojang;

import java.util.SortedMap;

@Deprecated
public class BufferBuilderStorage {
    private final SortedMap<RenderLayer, BufferBuilder> entityBuilders = null;
    private final VertexConsumerProvider.Immediate entityVertexConsumers;

    public BufferBuilderStorage() {
        this.entityVertexConsumers = VertexConsumerProvider.immediate(this.entityBuilders, new BufferBuilder(256));
    }


    public VertexConsumerProvider.Immediate getEntityVertexConsumers() {
        return null;
    }

    public MultiBufferSource getEffectVertexConsumers() {
        return null;
    }
}
