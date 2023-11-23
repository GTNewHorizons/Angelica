package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import org.lwjgl.BufferUtils;
import org.lwjgl.MemoryUtil;

import java.nio.ByteBuffer;

public abstract class StructBuffer {
    protected ByteBuffer buffer;

    protected final int stride;

    protected StructBuffer(int bytes, int stride) {
        this.buffer = BufferUtils.createByteBuffer(bytes * stride);
        this.stride = stride;
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public long getBufferAddress() {
        return MemoryUtil.getAddress(this.buffer);
    }

    public void delete() {
        // no-op, because Java direct buffers free themselves
    }
}
