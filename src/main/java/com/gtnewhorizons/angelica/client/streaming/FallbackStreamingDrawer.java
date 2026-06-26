package com.gtnewhorizons.angelica.client.streaming;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

public final class FallbackStreamingDrawer extends StreamingDrawer {

    private int capacity;
    private int pendingDataSize;
    private int elementCount;
//    private ByteBuffer mappedBuffer;
//    private long mappedAddress;
    private ByteBuffer buffer;
    private long basePtr;

    private void ensureCapacity(int needed) {
        if (needed + pendingDataSize > capacity) {
            resize(capacity * 2);
        }
    }

    FallbackStreamingDrawer(int stride, int elementCapacity, VAOConsumer initVAO) {
        super(initVAO, stride);
        capacity   = elementCapacity * stride;
        final int totalCapacity = capacity;

        buffer = memAlloc(totalCapacity);
        basePtr = memAddress0(buffer);
    }

    @Override
    public void drawElementsInstanced(int mode, int indices_count, int type, long indices_buffer_offset) {
        if (this.vaoId == 0) {
            super.initVAO();
        }
        buffer.limit(pendingDataSize);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STREAM_DRAW);

        GL31.glDrawElementsInstanced(
            GL11.GL_TRIANGLES,
            6,
            GL11.GL_UNSIGNED_SHORT,
            0,
            elementCount
        );
        elementCount = 0;
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        pendingDataSize = 0;
    }

    @Override
    public long writeSection(int needed) {
        ensureCapacity(needed);
        long pointer = basePtr + pendingDataSize;
        pendingDataSize += needed;
        elementCount++;
        return pointer;
    }

    public void resize(int newSectionSize) {
        capacity = newSectionSize;
        buffer = memRealloc(buffer, newSectionSize);
        basePtr = memAddress0(buffer);
    }

    public void destroy() {
        GLStateManager.glDeleteBuffers(vboId);
        vboId          = 0;
    }

    @Override
    public boolean isEmpty() {
        return elementCount == 0;
    }
}
