package com.gtnewhorizons.angelica.glsm.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Streaming buffer using the classic orphan pattern.
 */
public final class OrphanStreamingBuffer {

    private int bufferId;
    private int capacity;

    public OrphanStreamingBuffer() {
        this.bufferId = RENDER_BACKEND.genBuffers();
    }

    public void upload(ByteBuffer data) {
        RENDER_BACKEND.bindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(data, capacity);
        RENDER_BACKEND.bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    void upload(StreamingUploader.UploadStrategy strategy, ByteBuffer data) {
        RENDER_BACKEND.bindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        capacity = StreamingUploader.upload(strategy, data, capacity);
        RENDER_BACKEND.bindBuffer(GL15.GL_ARRAY_BUFFER, 0);
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
