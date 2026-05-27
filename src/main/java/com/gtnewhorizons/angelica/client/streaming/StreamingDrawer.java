package com.gtnewhorizons.angelica.client.streaming;

import org.lwjgl.opengl.GL15;

public abstract class StreamingDrawer {

    protected int vboId;
    protected int capacity;

    public StreamingDrawer(int capacity) {
        this.vboId = GL15.glGenBuffers();
        this.capacity = capacity;
    }

    public static StreamingDrawer create(int capacity) {
        return new PersistentStreamingDrawer(capacity); //TODO check caps
    }

    public final int getVBO() {
        return vboId;
    }

    public abstract long writeSection(int needed);

    public abstract int finishUploading();

    public void delete() {
        GL15.glDeleteBuffers(vboId);
    }
}
