package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vbo.VertexBuffer;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

/** Equivalent of modern's VertexBuffer: a mesh uploaded once and drawn many times */
public final class MeshBuffer {

    private VertexBuffer vbo;
    private int vao;

    public void upload(VertexFormat format, int drawMode, ByteBuffer data, int vertexCount) {
        upload(format, drawMode, data, vertexCount, false);
    }

    public void upload(VertexFormat format, int drawMode, ByteBuffer data, int vertexCount, boolean dynamic) {
        if (vbo == null || vbo.getDrawMode() != drawMode) {
            if (vbo != null) vbo.delete();
            vbo = new VertexBuffer(format, drawMode);
        }
        if (dynamic) {
            vbo.uploadDynamic(data, vertexCount);
        } else {
            vbo.uploadStream(data, vertexCount);
        }
    }

    public boolean isUploaded() {
        return vbo != null;
    }

    public void bind() {
        if (vao == 0) {
            vao = GLStateManager.glGenVertexArrays();
        }
        GLStateManager.glBindVertexArray(vao);
        vbo.setupState();
    }

    public void draw(int first, int count) {
        vbo.draw(first, count);
    }

    public void unbind() {
        vbo.cleanupState();
        GLStateManager.glBindVertexArray(0);
    }

    public void render() {
        bind();
        vbo.draw();
        unbind();
    }

    public void delete() {
        if (vbo != null) {
            vbo.delete();
            vbo = null;
        }
        if (vao != 0) {
            GLStateManager.glDeleteVertexArrays(vao);
            vao = 0;
        }
    }

    public static ByteBuffer ensureCapacity(ByteBuffer buffer, int bytes, boolean preserve) {
        if (buffer == null) {
            return BufferUtils.createByteBuffer(Integer.highestOneBit(Math.max(bytes - 1, 1)) << 1);
        }
        if (preserve) {
            if (buffer.remaining() >= bytes) return buffer;
            int newCapacity = buffer.capacity() * 2;
            while (newCapacity - buffer.position() < bytes) newCapacity *= 2;
            final ByteBuffer grown = BufferUtils.createByteBuffer(newCapacity);
            buffer.flip();
            grown.put(buffer);
            return grown;
        }
        if (buffer.capacity() >= bytes) return buffer;
        return BufferUtils.createByteBuffer(Integer.highestOneBit(Math.max(bytes - 1, 1)) << 1);
    }
}
