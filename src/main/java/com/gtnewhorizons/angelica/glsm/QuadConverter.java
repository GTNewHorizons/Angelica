package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

/**
 * Shared quad-to-triangle EBO for core profile GL_QUADS emulation.
 *
 * <p>Maintains a single growable element buffer with pre-computed indices mapping
 * GL_QUADS vertex order to GL_TRIANGLES: {@code (0,1,3, 1,2,3)} per quad.
 * Uses {@code GL_LAST_VERTEX_CONVENTION} winding (Mesa {@code do_quad} LAST path)
 * so both emitted triangles share the quad's provoking vertex (v3).
 */
public final class QuadConverter {
    public static final int INDEX_TYPE = GL11.GL_UNSIGNED_INT;

    private static int eboId;
    private static int maxQuads;

    private QuadConverter() {}

    private static void ensureCapacity(int quadCount) {
        if (quadCount <= maxQuads) return;

        // Round up to power of 2 for amortized growth
        int newMaxQuads = Math.max(256, maxQuads);
        while (newMaxQuads < quadCount) {
            newMaxQuads *= 2;
        }

        if (eboId == 0) {
            eboId = GL15.glGenBuffers();
        }

        // Generate index data: 6 ints per quad
        final ByteBuffer indexData = memAlloc(newMaxQuads * 6 * 4);
        long ptr = memAddress0(indexData);
        for (int i = 0; i < newMaxQuads; i++) {
            final int base = i * 4;
            // GL_LAST_VERTEX_CONVENTION: both triangles share v3 as provoking vertex
            // Triangle 1: 0, 1, 3
            memPutInt(ptr, base);
            memPutInt(ptr + 4, base + 1);
            memPutInt(ptr + 8, base + 3);
            // Triangle 2: 1, 2, 3
            memPutInt(ptr + 12, base + 1);
            memPutInt(ptr + 16, base + 2);
            memPutInt(ptr + 20, base + 3);
            ptr += 24;
        }

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_STATIC_DRAW);

        memFree(indexData);
        maxQuads = newMaxQuads;
    }

    /** Get the EBO id. Populated after the first {@link #drawQuadsAsTriangles} call. */
    public static int getEboId() {
        return eboId;
    }

    /**
     * Convert a GL_QUADS glDrawArrays call to indexed GL_TRIANGLES.
     * Binds the shared EBO to the current VAO and issues glDrawElements.
     *
     * @param first       first vertex index (must be aligned to quad boundary, i.e. multiple of 4)
     * @param vertexCount number of vertices (must be multiple of 4)
     */
    public static void drawQuadsAsTriangles(int first, int vertexCount) {
        assert first % 4 == 0 : "QuadConverter: first (" + first + ") must be a multiple of 4";
        assert vertexCount % 4 == 0 : "QuadConverter: vertexCount (" + vertexCount + ") must be a multiple of 4";
        final int quadCount = vertexCount / 4;
        ensureCapacity(first / 4 + quadCount);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        // Index offset: first vertex / 4 quads * 6 indices * 4 bytes per int
        final long indexOffset = (long) (first / 4) * 6 * 4;
        GL11.glDrawElements(GL11.GL_TRIANGLES, quadCount * 6, INDEX_TYPE, indexOffset);
    }

    /**
     * Clean up the shared EBO.
     */
    public static void destroy() {
        if (eboId != 0) {
            GL15.glDeleteBuffers(eboId);
            eboId = 0;
            maxQuads = 0;
        }
    }
}
