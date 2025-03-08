package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

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
        return SodiumClientMod.isDirectMemoryAccessEnabled() ? new UnsafeChunkDrawCallVector(capacity) : new NioChunkDrawCallVector(capacity);
    }

    public abstract void pushChunkDrawParams(float x, float y, float z);

    public void reset() {
        this.count = 0;
    }

    protected void growBuffer() {
        this.capacity = this.capacity * 2;
        this.buffer = memRealloc(this.buffer, this.capacity * this.stride);
    }

    public static class UnsafeChunkDrawCallVector extends ChunkDrawParamsVector {
        private long basePointer;
        private long writePointer;

        public UnsafeChunkDrawCallVector(int capacity) {
            super(capacity);

            this.basePointer = MemoryUtil.memAddress(this.buffer);
        }

        @Override
        public void pushChunkDrawParams(float x, float y, float z) {
            if (this.count++ >= this.capacity) {
                this.growBuffer();
            }

            memPutFloat(this.writePointer    , x);
            memPutFloat(this.writePointer + 4, y);
            memPutFloat(this.writePointer + 8, z);

            this.writePointer += this.stride;
        }

        @Override
        protected void growBuffer() {
            super.growBuffer();

            long offset = this.writePointer - this.basePointer;

            this.basePointer = memAddress(this.buffer);
            this.writePointer = this.basePointer + offset;
        }

        @Override
        public void reset() {
            super.reset();

            this.writePointer = this.basePointer;
        }
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
