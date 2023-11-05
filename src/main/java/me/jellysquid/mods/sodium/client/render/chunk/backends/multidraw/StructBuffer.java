package me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw;

import me.jellysquid.mods.sodium.client.gl.util.MemoryUtilHelper;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public abstract class StructBuffer {
    protected ByteBuffer buffer;

    protected final int stride;

    protected StructBuffer(int bytes, int stride) {
        this.buffer = MemoryUtil.memAlloc(bytes * stride);
        this.stride = stride;
    }

    public ByteBuffer getBuffer() {
        return this.buffer;
    }

    public void delete() {
    	MemoryUtilHelper.memFree(this.buffer);
    }

    public long getBufferAddress() {
        return MemoryUtil.memAddress(this.buffer);
    }
}
