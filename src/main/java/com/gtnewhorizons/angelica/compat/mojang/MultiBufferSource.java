package com.gtnewhorizons.angelica.compat.mojang;

import java.util.Map;

public interface MultiBufferSource {
    static BufferSource immediateWithBuffers(Map<RenderLayer, BufferBuilder> map, BufferBuilder arg) {
        return new BufferSource(arg, map);
    }

    VertexConsumer getBuffer(RenderLayer arg);
}
