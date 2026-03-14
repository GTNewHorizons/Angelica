package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.ffp.StreamingUploader;
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

 */
public class OrphanStreamingBuffer {

    private int bufferId;
    private int capacity;

    public OrphanStreamingBuffer() {
        this.bufferId = GL15.glGenBuffers();
    }

    public void upload(ByteBuffer data) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(data, capacity);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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

    public int getCapacity() {
        return capacity;
    }

    public int getBufferId() {
        return bufferId;
    }

    public void destroy() {
        if (bufferId != 0) {
            GL15.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }
}
