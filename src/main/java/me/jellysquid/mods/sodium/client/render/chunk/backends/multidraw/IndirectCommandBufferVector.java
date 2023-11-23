package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public class IndirectCommandBufferVector extends StructBuffer {
    protected IndirectCommandBufferVector(int capacity) {
        super(capacity, 16);
    }

    public static IndirectCommandBufferVector create(int capacity) {
        return new IndirectCommandBufferVector(capacity);
    }

    public void begin() {
        this.buffer.clear();
    }

    public void end() {
        if(this.buffer.position() > 0) {
            this.buffer.flip();
        }
    }

    public void pushCommandBuffer(ChunkDrawCallBatcher batcher) {
        int len = batcher.getArrayLength();

        if (this.buffer.remaining() < len) {
            this.growBuffer(len);
        }

        this.buffer.put(batcher.getBuffer());
    }

    protected void growBuffer(int n) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(Math.max(this.buffer.capacity() * 2, this.buffer.capacity() + n));
        this.buffer.rewind();
        buffer.put(this.buffer);
        buffer.position(0);

        this.buffer = buffer;
    }
}
