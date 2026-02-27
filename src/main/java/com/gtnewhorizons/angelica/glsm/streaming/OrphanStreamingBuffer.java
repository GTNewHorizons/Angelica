package com.gtnewhorizons.angelica.glsm.streaming;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * Streaming buffer using the classic orphan pattern with sub-allocation.
 * <p>
 * Supports two upload strategies:
 * <ul>
 *   <li>{@link UploadStrategy#BUFFER_SUB_DATA} — glBufferData orphan + glBufferSubData (default)</li>
 *   <li>{@link UploadStrategy#MAP_BUFFER_RANGE} — glMapBufferRange with INVALIDATE hints </li>
 * </ul>
 */
public class OrphanStreamingBuffer implements StreamingBuffer {

    public enum UploadStrategy {
        BUFFER_SUB_DATA,
        MAP_BUFFER_RANGE
    }

    private static final int MAP_WRITE_INVALIDATE_BUFFER = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT;
    private static final int MAP_WRITE_INVALIDATE_RANGE = GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_RANGE_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT;

    private int bufferId;
    private int capacity;
    private int writePos;
    private boolean needsOrphan = true;
    private final UploadStrategy strategy;

    public OrphanStreamingBuffer() {
        this(UploadStrategy.BUFFER_SUB_DATA);
    }

    public OrphanStreamingBuffer(UploadStrategy strategy) {
        this.strategy = strategy;
        this.bufferId = GL15.glGenBuffers();
    }

    @Override
    public int upload(ByteBuffer data, int vertexStride) {
        final int dataSize = data.remaining();

        final int alignUnit = vertexStride * 4;
        int alignedPos = ((writePos + alignUnit - 1) / alignUnit) * alignUnit;

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);

        boolean freshAllocation = false;
        if (alignedPos + dataSize > capacity) {
            // Doesn't fit — grow if needed, allocate new storage, reset to offset 0
            growIfNeeded(dataSize);
            allocateBuffer();
            alignedPos = 0;
            freshAllocation = true;
        } else if (needsOrphan) {
            // First upload of the frame — need fresh storage
            if (strategy == UploadStrategy.BUFFER_SUB_DATA) {
                // SubData strategy: orphan via glBufferData to get new backing store
                allocateBuffer();
            }
        }
        if (strategy == UploadStrategy.BUFFER_SUB_DATA) {
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, alignedPos, data);
        } else {
            // MapRange: choose flags based on whether this is the first upload of the frame
            final int flags;
            if (freshAllocation) {
                // Fresh allocation — nothing to invalidate, just write
                flags = MAP_WRITE_INVALIDATE_RANGE;
            } else if (needsOrphan) {
                // First upload of frame on existing buffer — invalidate entire buffer
                flags = MAP_WRITE_INVALIDATE_BUFFER;
            } else {
                // Subsequent upload — invalidate only our range
                flags = MAP_WRITE_INVALIDATE_RANGE;
            }
            final ByteBuffer mapped = LWJGL.glMapBufferRange(GL15.GL_ARRAY_BUFFER, alignedPos, dataSize, flags);
            memCopy(memAddress0(data), memAddress0(mapped), dataSize);
            GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        needsOrphan = false;
        writePos = alignedPos + dataSize;
        return alignedPos / vertexStride;
    }

    private static final int MIN_CAPACITY = 64 * 1024;

    private void growIfNeeded(int needed) {
        if (needed > capacity) {
            int newCapacity = Math.max(capacity, MIN_CAPACITY);
            while (newCapacity < needed) {
                newCapacity *= 2;
            }
            capacity = newCapacity;
        }
    }

    private void allocateBuffer() {
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STREAM_DRAW);
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public int getBufferId() {
        return bufferId;
    }

    @Override
    public void postDraw() {
        writePos = 0;
        needsOrphan = true;
    }

    @Override
    public void destroy() {
        if (bufferId != 0) {
            GL15.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }

    int getWritePos() {
        return writePos;
    }

    UploadStrategy getStrategy() {
        return strategy;
    }
}
