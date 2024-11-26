package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import static com.gtnewhorizon.gtnhlib.client.lwjgl3.CompatMemoryUtil.memPutInt;

import com.gtnewhorizons.angelica.compat.mojang.CompatMathHelper;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import org.lwjgl.MemoryUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Provides a fixed-size buffer which can be used to batch chunk section draw calls.
 */
public abstract class ChunkDrawCallBatcher extends StructBuffer {
    protected final int capacity;

    protected boolean isBuilding;
    protected int count;

    protected int arrayLength;

    protected ChunkDrawCallBatcher(int capacity) {
        super(CompatMathHelper.smallestEncompassingPowerOfTwo(capacity), 16);

        this.capacity = capacity;
    }

    public static ChunkDrawCallBatcher create(int capacity) {
        return SodiumClientMod.isDirectMemoryAccessEnabled() ? new UnsafeChunkDrawCallBatcher(capacity) : new NioChunkDrawCallBatcher(capacity);
    }

    public void begin() {
        this.isBuilding = true;
        this.count = 0;
        this.arrayLength = 0;

        this.buffer.limit(this.buffer.capacity());
    }

    public void end() {
        this.isBuilding = false;

        this.arrayLength = this.count * this.stride;
        this.buffer.limit(this.arrayLength);
        this.buffer.position(0);
    }

    public boolean isBuilding() {
        return this.isBuilding;
    }

    public abstract void addIndirectDrawCall(int first, int count, int baseInstance, int instanceCount);

    public int getCount() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.count <= 0;
    }

    public static class UnsafeChunkDrawCallBatcher extends ChunkDrawCallBatcher {

        private final long basePointer;
        private long writePointer;

        public UnsafeChunkDrawCallBatcher(int capacity) {
            super(capacity);

            this.basePointer = MemoryUtil.getAddress(this.buffer);
        }

        @Override
        public void begin() {
            super.begin();

            this.writePointer = this.basePointer;
        }

        @Override
        public void addIndirectDrawCall(int first, int count, int baseInstance, int instanceCount) {
            if (this.count++ >= this.capacity) {
                throw new BufferUnderflowException();
            }

            memPutInt(this.writePointer     , count);         // Vertex Count
            memPutInt(this.writePointer +  4, instanceCount); // Instance Count
            memPutInt(this.writePointer +  8, first);         // Vertex Start
            memPutInt(this.writePointer + 12, baseInstance);  // Base Instance

            this.writePointer += this.stride;
        }
    }

    public static class NioChunkDrawCallBatcher extends ChunkDrawCallBatcher {
        private int writeOffset;

        public NioChunkDrawCallBatcher(int capacity) {
            super(capacity);
        }

        @Override
        public void begin() {
            super.begin();

            this.writeOffset = 0;
        }

        @Override
        public void addIndirectDrawCall(int first, int count, int baseInstance, int instanceCount) {
            ByteBuffer buf = this.buffer;
            buf.putInt(this.writeOffset     , count);             // Vertex Count
            buf.putInt(this.writeOffset +  4, instanceCount);     // Instance Count
            buf.putInt(this.writeOffset +  8, first);             // Vertex Start
            buf.putInt(this.writeOffset + 12, baseInstance);      // Base Instance

            this.writeOffset += this.stride;
            this.count++;
        }
    }

    public int getArrayLength() {
        return this.arrayLength;
    }

}
