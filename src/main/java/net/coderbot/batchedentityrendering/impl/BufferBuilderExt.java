package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.toremove.DrawState;

import java.nio.ByteBuffer;

public interface BufferBuilderExt {
    void setupBufferSlice(ByteBuffer buffer, DrawState drawState);
    void teardownBufferSlice();
    void splitStrip();
}
