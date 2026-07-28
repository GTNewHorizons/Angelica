package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deleting a bound buffer must not strand an id in LWJGL2's StateTracker */
@GLCompatTest
public class GLSM_DeletedBufferBinding_GLTest {

    private static int makeBoundBuffer(int target) {
        final int buffer = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(target, buffer);
        GLStateManager.glBufferData(target, 64L, GL15.GL_STREAM_DRAW);
        return buffer;
    }

    private static void assertUnboundAfterDelete(int target, int binding, int buffer) {
        GLStateManager.glDeleteBuffers(buffer);
        assertEquals(0, GL11.glGetInteger(binding), "GL unbinds a deleted buffer; the cache must not be the only place that knows");
    }

    @Test
    void deletedPixelUnpackBufferDoesNotBlockTexImage2D() {
        final int pbo = makeBoundBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
        assertUnboundAfterDelete(GL21.GL_PIXEL_UNPACK_BUFFER, GL21.GL_PIXEL_UNPACK_BUFFER_BINDING, pbo);

        final int tex = GLStateManager.glGenTextures();
        try {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(4));
        } finally {
            GLStateManager.glDeleteTextures(tex);
        }
    }

    @Test
    void deletedPixelPackBufferDoesNotBlockReadPixels() {
        final int pbo = makeBoundBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        assertUnboundAfterDelete(GL21.GL_PIXEL_PACK_BUFFER, GL21.GL_PIXEL_PACK_BUFFER_BINDING, pbo);

        GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(4));
    }

    @Test
    void deletedArrayBufferDoesNotBlockClientVertexPointer() {
        final int vbo = makeBoundBuffer(GL15.GL_ARRAY_BUFFER);
        assertUnboundAfterDelete(GL15.GL_ARRAY_BUFFER, GL15.GL_ARRAY_BUFFER_BINDING, vbo);

        final int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(0);
        try {
            GL11.glVertexPointer(3, 0, BufferUtils.createFloatBuffer(9));
        } finally {
            GL30.glBindVertexArray(prevVao);
        }
    }

    @Test
    void deletedElementArrayBufferDoesNotBlockClientDrawElements() {
        final int ebo = makeBoundBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER);
        assertUnboundAfterDelete(GL15.GL_ELEMENT_ARRAY_BUFFER, GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING, ebo);

        GL11.glDrawElements(GL11.GL_TRIANGLES, BufferUtils.createIntBuffer(3));
    }
}
