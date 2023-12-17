package com.gtnewhorizons.angelica.compat.toremove;

import java.util.Map;

@Deprecated
public interface MultiBufferSource {
    static BufferSource immediateWithBuffers(Map<RenderLayer, BufferBuilder> map, BufferBuilder arg) {
        return new BufferSource(arg, map);
    }

    VertexConsumer getBuffer(RenderLayer arg);
}
