package com.gtnewhorizons.angelica.glsm.streaming;

import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

/**
 * Streaming buffer using the classic orphan pattern
 */
public class OrphanStreamingBuffer implements StreamingBuffer {

    private int bufferId;
    private int capacity;

    public OrphanStreamingBuffer() {
        this.bufferId = GL15.glGenBuffers();
    }

    @Override
    public int upload(ByteBuffer data, int vertexStride) {
        final int requiredBytes = data.remaining();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        if (requiredBytes > capacity) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
            capacity = requiredBytes;
        } else {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, capacity, GL15.GL_STREAM_DRAW);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, data);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return 0;
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
        // No-op
    }

    @Override
    public void destroy() {
        if (bufferId != 0) {
            GL15.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }
}
