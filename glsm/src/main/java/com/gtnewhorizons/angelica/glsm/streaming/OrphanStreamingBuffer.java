package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

/**
 * Streaming buffer using the classic orphan pattern.
 */
public final class OrphanStreamingBuffer {

    private int bufferId;
    private int capacity;

    public OrphanStreamingBuffer() {
        this.bufferId = GLStateManager.glGenBuffers();
    }

    public void upload(ByteBuffer data) {
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(data, capacity);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    void upload(StreamingUploader.UploadStrategy strategy, ByteBuffer data) {
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(strategy, data, capacity);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBufferId() {
        return bufferId;
    }

    public void destroy() {
        if (bufferId != 0) {
            GLStateManager.glDeleteBuffers(bufferId);
            bufferId = 0;
        }
    }
}
