package com.gtnewhorizons.angelica.glsm.streaming;

import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

/**
 * Streaming buffer using the classic orphan pattern.
 */
public final class OrphanStreamingBuffer {

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

    void upload(StreamingUploader.UploadStrategy strategy, ByteBuffer data) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(strategy, data, capacity);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
