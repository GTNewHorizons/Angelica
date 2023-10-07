package net.coderbot.batchedentityrendering.impl;


import java.nio.ByteBuffer;

public interface BufferBuilderExt {
    void setupBufferSlice(ByteBuffer buffer, BufferBuilder.DrawState drawState);
    void teardownBufferSlice();
    void splitStrip();
}
