package com.gtnewhorizons.angelica.glsm.streaming;

import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

public final class PersistentStreamingBuffer {

    public static final int DEFAULT_CAPACITY = 16 * 1024 * 1024;

    private final GlStreamingRing ring;

    private PersistentStreamingBuffer(GlStreamingRing ring) {
        this.ring = ring;
    }

    public static PersistentStreamingBuffer createOrNull(int capacity) {
        return createOrNull(capacity, GlStreamingRing.FORCE_ORPHAN_STREAMING);
    }

    public static PersistentStreamingBuffer createOrNull(int capacity, boolean forceOrphan) {
        final GlStreamingRing ring = GlStreamingRing.create(GL15.GL_ARRAY_BUFFER, capacity, forceOrphan);
        return ring == null ? null : new PersistentStreamingBuffer(ring);
    }


    public int upload(ByteBuffer data, int vertexStride) {
        final int size = data.remaining();
        final long offset = ring.reserve(size, vertexStride);
        if (offset < 0) return -1;
        memCopy(memAddress0(data) + data.position(), ring.address() + offset, size);
        return (int) (offset / vertexStride);
    }

    public int getBufferId() { return ring.bufferId(); }
    public int getCapacity() { return ring.capacity(); }
    public int getRemaining() { return ring.remaining(); }

    ByteBuffer getMappedBuffer() { return ring.mapped(); }

    public void postDraw() {
        ring.endFrame();
    }

    public void destroy() {
        ring.destroy();
    }
}
