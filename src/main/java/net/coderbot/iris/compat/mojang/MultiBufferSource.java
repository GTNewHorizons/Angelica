package net.coderbot.iris.compat.mojang;

import java.util.Map;

public interface MultiBufferSource {
    static BufferSource immediateWithBuffers(Map<RenderType, BufferBuilder> map, BufferBuilder arg) {
        return new BufferSource(arg, map);
    }

    VertexConsumer getBuffer(RenderType arg);
}
