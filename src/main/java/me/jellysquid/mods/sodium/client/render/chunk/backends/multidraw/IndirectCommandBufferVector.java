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
        this.buffer.flip();
    }

    public void pushCommandBuffer(ChunkDrawCallBatcher batcher) {
        int len = batcher.getArrayLength();

        if (this.buffer.remaining() < len) {
            this.growBuffer(len);
        }

        this.buffer.put(batcher.getBuffer());
    }

    protected void growBuffer(int n) {
        ByteBuffer oldBuffer = this.buffer;
        this.buffer = BufferUtils.createByteBuffer(Math.max(oldBuffer.capacity() * 2, oldBuffer.capacity() + n));
        buffer.put((ByteBuffer) oldBuffer.rewind());
    }
}
