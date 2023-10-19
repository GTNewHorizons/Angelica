package net.coderbot.batchedentityrendering.impl;

import net.coderbot.iris.compat.mojang.DrawState;

import java.nio.ByteBuffer;

public interface BufferBuilderExt {
    void setupBufferSlice(ByteBuffer buffer, DrawState drawState);
    void teardownBufferSlice();
    void splitStrip();
}
