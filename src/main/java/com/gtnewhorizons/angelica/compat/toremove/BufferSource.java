package com.gtnewhorizons.angelica.compat.toremove;

import java.util.Map;

@Deprecated
public class BufferSource implements MultiBufferSource {

    public <V, K> BufferSource(BufferBuilder bufferBuilder, Map<K,V> kvMap) {}

    public VertexConsumer getBuffer(RenderLayer renderType) {
        return null;
    }
    public void endBatch() {}
    public void endBatch(RenderLayer type) {}
}
