package com.gtnewhorizons.angelica.client.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public abstract class StreamingDrawer {

    protected int vboId;
    protected int vaoId;
    protected final VAOConsumer vaoConsumer;
    protected int dataSize;


    public static StreamingDrawer create(int stride, int elementCapacity, VAOConsumer vaoConsumer) {
        return new PersistentStreamingDrawer(stride, elementCapacity, vaoConsumer); //TODO check caps
    }

    StreamingDrawer(VAOConsumer vaoConsumer) {
        this.vboId = GL15.glGenBuffers();
        this.vaoConsumer = vaoConsumer;
    }

    public final void initVAO() {
        this.vaoId = GLStateManager.glGenVertexArrays();
        dataSize = vaoConsumer.initialize(vaoId, vboId);
    }

    public final int getVBO() {
        return vboId;
    }

    public final int getVAO() {
        return vaoId;
    }

    public abstract long writeSection(int needed);

    public abstract void drawElementsInstanced(
        int mode, int indices_count, int type, long indices_buffer_offset
    );

    public abstract boolean isEmpty();

    public final void delete() {
        GL15.glDeleteBuffers(vboId);
        GL30.glDeleteVertexArrays(vaoId);
    }

    public final void switchContext() {
        GL30.glDeleteVertexArrays(vaoId);
        vaoId = 0;
    }
}
