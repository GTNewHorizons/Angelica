package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

import java.nio.ByteBuffer;

public abstract class StructBuffer {
    protected ByteBuffer buffer;

    protected final int stride;

    protected StructBuffer(int bytes, int stride) {
        this.buffer = memAlloc(bytes * stride);
        this.stride = stride;
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public long getBufferAddress() {
        return memAddress(this.buffer);
    }

    public void delete() {
        // no-op, because Java direct buffers free themselves
    }
}
