package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

/**
 * Provides a resizeable vector backed by native memory that can be used to build an array of chunk draw call
 * parameters.
 */
public abstract class ChunkDrawParamsVector extends StructBuffer {
    protected int capacity;
    protected int count;

    protected ChunkDrawParamsVector(int capacity) {
        super(capacity, 16);

        this.capacity = capacity;
    }

    public static ChunkDrawParamsVector create(int capacity) {
        return new NioChunkDrawCallVector(capacity);
    }

    public abstract void pushChunkDrawParams(float x, float y, float z);

    public void reset() {
        this.count = 0;
    }

    protected void growBuffer() {
        ByteBuffer oldBuffer = this.buffer;
        this.capacity = this.capacity * 2;
        this.buffer = BufferUtils.createByteBuffer(this.capacity * this.stride);
        buffer.put((ByteBuffer) oldBuffer.rewind());
    }

    public static class NioChunkDrawCallVector extends ChunkDrawParamsVector {
        private int writeOffset;

        public NioChunkDrawCallVector(int capacity) {
            super(capacity);
        }

        @Override
        public void pushChunkDrawParams(float x, float y, float z) {
            if (this.count++ >= this.capacity) {
                this.growBuffer();
            }

            ByteBuffer buf = this.buffer;
            buf.putFloat(this.writeOffset    , x);
            buf.putFloat(this.writeOffset + 4, y);
            buf.putFloat(this.writeOffset + 8, z);

            this.writeOffset += this.stride;
        }

        @Override
        public void reset() {
            super.reset();

            this.writeOffset = 0;
        }
    }
}
