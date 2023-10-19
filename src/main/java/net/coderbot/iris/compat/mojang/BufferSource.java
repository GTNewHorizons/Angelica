package net.coderbot.iris.compat.mojang;

import java.util.Map;

public class BufferSource implements MultiBufferSource {

    public <V, K> BufferSource(BufferBuilder bufferBuilder, Map<K,V> kvMap) {}

    public VertexConsumer getBuffer(RenderType renderType) {
        return null;
    }
    public void endBatch() {}
    public void endBatch(RenderType type) {}
}
