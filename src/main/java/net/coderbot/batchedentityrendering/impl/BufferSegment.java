package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.toremove.RenderLayer;
import lombok.Getter;

import java.nio.ByteBuffer;

public class BufferSegment {
    private final ByteBuffer slice;
    @Getter
    private final RenderLayer type;

    public BufferSegment(ByteBuffer slice, RenderLayer type) {
        this.slice = slice;
        this.type = type;
    }

    public ByteBuffer getSlice() {
        return slice;
    }

    public RenderLayer getRenderType() {
        return type;
    }
}
