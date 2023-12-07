package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.DrawState;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import lombok.Getter;

import java.nio.ByteBuffer;

public class BufferSegment {
    private final ByteBuffer slice;
    @Getter
    private final DrawState drawState;
    private final RenderLayer type;

    public BufferSegment(ByteBuffer slice, DrawState drawState, RenderLayer type) {
        this.slice = slice;
        this.drawState = drawState;
        this.type = type;
    }

    public ByteBuffer getSlice() {
        return slice;
    }

    public RenderLayer getRenderType() {
        return type;
    }
}
