package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;

import java.util.List;

public final class BufferSourceProbe {

    private BufferSourceProbe() {}

    public static List<RenderLayer> prepare(AngelicaBufferSource source) {
        return source.prepare();
    }

    public static List<BufferSegment> segmentsFor(AngelicaBufferSource source, RenderLayer layer) {
        return source.segmentsFor(layer);
    }
}
