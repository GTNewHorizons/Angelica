package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.DrawState;
import com.gtnewhorizons.angelica.compat.mojang.RenderType;

import java.nio.ByteBuffer;

public class BufferSegment {
    private final ByteBuffer slice;
    private final DrawState drawState;
    private final RenderType type;

    public BufferSegment(ByteBuffer slice, DrawState drawState, RenderType type) {
        this.slice = slice;
        this.drawState = drawState;
        this.type = type;
    }

    public ByteBuffer getSlice() {
        return slice;
    }

    public DrawState getDrawState() {
        return drawState;
    }

    public RenderType getRenderType() {
        return type;
    }
}
