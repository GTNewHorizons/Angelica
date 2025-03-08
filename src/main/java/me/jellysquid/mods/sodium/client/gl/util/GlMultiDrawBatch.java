package me.jellysquid.mods.sodium.client.gl.util;

import static org.lwjgl.system.MemoryUtil.memAllocInt;
import static org.lwjgl.system.MemoryUtil.memFree;

import org.lwjgl.opengl.GL20;


import java.nio.IntBuffer;

/**
 * Provides a fixed-size queue for batching draw calls for vertex data in the same buffer. This internally
 * uses {@link GL20#glMultiDrawArrays(int, IntBuffer, IntBuffer)} and should be compatible on any relevant platform.
 */
public class GlMultiDrawBatch {
    private IntBuffer bufIndices;
    private IntBuffer bufLen;
    private int count;
    private boolean isBuilding;

    public GlMultiDrawBatch(int capacity) {
        this.bufIndices = memAllocInt(capacity);
        this.bufLen = memAllocInt(capacity);
    }

    public IntBuffer getIndicesBuffer() {
        return this.bufIndices;
    }

    public IntBuffer getLengthBuffer() {
        return this.bufLen;
    }

    public void begin() {
        this.bufIndices.clear();
        this.bufLen.clear();
        this.count = 0;

        this.isBuilding = true;
    }

    public void end() {
        this.bufIndices.limit(this.count);
        this.bufLen.limit(this.count);

        this.isBuilding = false;
    }

    public boolean isEmpty() {
        return this.count <= 0;
    }

    public void addChunkRender(int first, int count) {
        int i = this.count++;
        this.bufIndices.put(i, first);
        this.bufLen.put(i, count);
    }

    public boolean isBuilding() {
        return this.isBuilding;
    }

    public void delete() {
        memFree(this.bufIndices);
        memFree(this.bufLen);

        this.bufIndices = null;
        this.bufLen = null;
    }
}
