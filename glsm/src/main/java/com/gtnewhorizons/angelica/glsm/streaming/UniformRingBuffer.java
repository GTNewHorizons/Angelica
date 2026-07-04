package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class UniformRingBuffer {

    private static final int OFFSET_ALIGNMENT = 256;

    private final int capacity;
    private final int blockSize;
    private final int stride;
    private final GlStreamingRing ring;
    private int orphanBufferId;

    private int writePos;
    private int orphanWraps;

    public UniformRingBuffer(int capacity, int blockSize) {
        this(capacity, blockSize, GlStreamingRing.FORCE_ORPHAN_STREAMING);
    }

    public UniformRingBuffer(int capacity, int blockSize, boolean forceOrphan) {
        this.capacity = capacity;
        this.blockSize = blockSize;
        this.stride = (blockSize + OFFSET_ALIGNMENT - 1) & -OFFSET_ALIGNMENT;
        this.ring = GlStreamingRing.create(GL31.GL_UNIFORM_BUFFER, capacity, forceOrphan);
        if (ring == null) {
            orphanBufferId = RENDER_BACKEND.genBuffers();
            RENDER_BACKEND.bindBuffer(GL31.GL_UNIFORM_BUFFER, orphanBufferId);
            RENDER_BACKEND.bufferData(GL31.GL_UNIFORM_BUFFER, capacity, GL15.GL_STREAM_DRAW);
            RENDER_BACKEND.bindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        }
    }

    public int writeBlock(ByteBuffer src) {
        final long srcAddress = memAddress0(src);
        if (ring != null) {
            final long offset = ring.reserve(stride, OFFSET_ALIGNMENT);
            if (offset < 0) {
                throw new IllegalStateException("uniform ring exhausted with nothing in flight (needed=" + stride + ")");
            }
            memCopy(srcAddress, ring.address() + offset, blockSize);
            return (int) offset;
        }
        return writeOrphan(srcAddress);
    }

    private int writeOrphan(long srcAddress) {
        RENDER_BACKEND.bindBuffer(GL31.GL_UNIFORM_BUFFER, orphanBufferId);
        int offset = writePos;
        if (offset + stride > capacity) {
            RENDER_BACKEND.bufferData(GL31.GL_UNIFORM_BUFFER, capacity, GL15.GL_STREAM_DRAW);
            offset = 0;
            orphanWraps++;
        }
        final long dst = RENDER_BACKEND.mapBufferRangeAddress(GL31.GL_UNIFORM_BUFFER, offset, blockSize, GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_RANGE_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT);
        if (dst == 0L) {
            throw new IllegalStateException("mapBufferRange failed on uniform ring (offset=" + offset + ")");
        }
        memCopy(srcAddress, dst, blockSize);
        RENDER_BACKEND.unmapBuffer(GL31.GL_UNIFORM_BUFFER);
        RENDER_BACKEND.bindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        writePos = offset + stride;
        return offset;
    }

    public void endFrame() {
        if (ring != null) {
            ring.endFrame();
        }
    }

    public int getBufferId() { return ring != null ? ring.bufferId() : orphanBufferId; }
    public int getBlockSize() { return blockSize; }
    public int getStride() { return stride; }
    public boolean isPersistent() { return ring != null; }
    public int getWraps() { return ring != null ? ring.wraps() : orphanWraps; }
    public int getFencesIssued() { return ring != null ? ring.fencesIssued() : 0; }

    public void destroy() {
        if (ring != null) {
            ring.destroy();
        } else if (orphanBufferId != 0) {
            GLStateManager.glDeleteBuffers(orphanBufferId);
            orphanBufferId = 0;
        }
    }
}
