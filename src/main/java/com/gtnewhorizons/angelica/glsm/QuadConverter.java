package com.gtnewhorizons.angelica.glsm;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetShort;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutShort;

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

    /** Scratch EBO for converted quad-element draws (drawQuadElementsAsTriangles). */
    private static int scratchEboId;
    private static int scratchEboCapacity; // in bytes

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
     * Upload converted triangle indices to the scratch EBO and draw.
     * Saves/restores the caller's EBO binding so subsequent indexed draws
     * (or glGetBufferSubData reads) still see the original EBO.
     *
     * @param dst            off-heap buffer with triangle index data (freed by this method)
     * @param triIndexCount  number of triangle indices
     * @param indexType       GL_UNSIGNED_INT or GL_UNSIGNED_SHORT
     * @param bytesPerIndex  bytes per index element
     */
    private static void uploadAndDraw(ByteBuffer dst, int triIndexCount, int indexType, int bytesPerIndex) {
        final int needed = triIndexCount * bytesPerIndex;
        final int prevEbo = GLStateManager.getBoundEBO();

        if (scratchEboId == 0) {
            scratchEboId = GL15.glGenBuffers();
        }

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, scratchEboId);

        if (needed > scratchEboCapacity) {
            // Power-of-2 growth — allocate full capacity, upload actual data
            int newCap = Math.max(4096, scratchEboCapacity);
            while (newCap < needed) newCap *= 2;
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, newCap, GL15.GL_STREAM_DRAW);
            scratchEboCapacity = newCap;
        }
        GL15.glBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, dst);

        GL11.glDrawElements(GL11.GL_TRIANGLES, triIndexCount, indexType, 0L);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
        memFree(dst);
    }

    /**
     * Convert an indexed GL_QUADS draw to GL_TRIANGLES by reading the source EBO,
     * building a triangle index buffer, and issuing the converted draw.
     *
     * @param indexCount number of quad indices (must be multiple of 4)
     * @param type       GL_UNSIGNED_INT, GL_UNSIGNED_SHORT, or GL_UNSIGNED_BYTE
     * @param offset     byte offset into the currently bound EBO
     */
    public static void drawQuadElementsAsTriangles(int indexCount, int type, long offset) {
        if (indexCount == 0) return;
        assert indexCount % 4 == 0 : "QuadConverter: indexCount must be multiple of 4";
        final int quadCount = indexCount / 4;
        final int triIndexCount = quadCount * 6;

        final int bytesPerIndex;
        switch (type) {
            case GL11.GL_UNSIGNED_INT: bytesPerIndex = 4; break;
            case GL11.GL_UNSIGNED_SHORT: bytesPerIndex = 2; break;
            case GL11.GL_UNSIGNED_BYTE: bytesPerIndex = 1; break;
            default: return;
        }

        // Read source indices from caller's EBO
        final ByteBuffer src = memAlloc(indexCount * bytesPerIndex);
        GL15.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, offset, src);
        final long srcAddr = memAddress0(src);

        // Allocate output as GL_UNSIGNED_INT
        final ByteBuffer dst = memAlloc(triIndexCount * 4);
        final long dstAddr = memAddress0(dst);

        for (int i = 0; i < quadCount; i++) {
            final int a, b, c, d;
            switch (type) {
                case GL11.GL_UNSIGNED_INT -> {
                    final long p = srcAddr + (long) i * 16;
                    a = memGetInt(p); b = memGetInt(p + 4); c = memGetInt(p + 8); d = memGetInt(p + 12);
                }
                case GL11.GL_UNSIGNED_SHORT -> {
                    final long p = srcAddr + (long) i * 8;
                    a = memGetShort(p) & 0xFFFF; b = memGetShort(p + 2) & 0xFFFF;
                    c = memGetShort(p + 4) & 0xFFFF; d = memGetShort(p + 6) & 0xFFFF;
                }
                default /* GL_UNSIGNED_BYTE */ -> {
                    final long p = srcAddr + (long) i * 4;
                    a = memGetByte(p) & 0xFF; b = memGetByte(p + 1) & 0xFF;
                    c = memGetByte(p + 2) & 0xFF; d = memGetByte(p + 3) & 0xFF;
                }
            }
            // GL_LAST_VERTEX_CONVENTION: (a,b,d), (b,c,d)
            final long o = (long) i * 24;
            memPutInt(dstAddr + o, a);
            memPutInt(dstAddr + o + 4, b);
            memPutInt(dstAddr + o + 8, d);
            memPutInt(dstAddr + o + 12, b);
            memPutInt(dstAddr + o + 16, c);
            memPutInt(dstAddr + o + 20, d);
        }

        memFree(src);
        uploadAndDraw(dst, triIndexCount, GL11.GL_UNSIGNED_INT, 4);
    }

    /**
     * Convert a client-side IntBuffer of quad indices to triangles.
     */
    public static void drawQuadElementsAsTriangles(IntBuffer indices) {
        final int indexCount = indices.remaining();
        if (indexCount == 0) return;
        assert indexCount % 4 == 0;
        final int quadCount = indexCount / 4;
        final int triIndexCount = quadCount * 6;

        final ByteBuffer dst = memAlloc(triIndexCount * 4);
        final long dstAddr = memAddress0(dst);
        final int basePos = indices.position();

        for (int i = 0; i < quadCount; i++) {
            final int base = basePos + i * 4;
            final int a = indices.get(base), b = indices.get(base + 1), c = indices.get(base + 2), d = indices.get(base + 3);
            final long o = (long) i * 24;
            memPutInt(dstAddr + o, a);     memPutInt(dstAddr + o + 4, b);  memPutInt(dstAddr + o + 8, d);
            memPutInt(dstAddr + o + 12, b); memPutInt(dstAddr + o + 16, c); memPutInt(dstAddr + o + 20, d);
        }

        uploadAndDraw(dst, triIndexCount, GL11.GL_UNSIGNED_INT, 4);
    }

    /**
     * Convert a client-side ShortBuffer of quad indices to triangles.
     */
    public static void drawQuadElementsAsTriangles(ShortBuffer indices) {
        final int indexCount = indices.remaining();
        if (indexCount == 0) return;
        assert indexCount % 4 == 0;
        final int quadCount = indexCount / 4;
        final int triIndexCount = quadCount * 6;

        final ByteBuffer dst = memAlloc(triIndexCount * 2);
        final long dstAddr = memAddress0(dst);
        final int basePos = indices.position();

        for (int i = 0; i < quadCount; i++) {
            final int base = basePos + i * 4;
            final short a = indices.get(base), b = indices.get(base + 1), c = indices.get(base + 2), d = indices.get(base + 3);
            final long o = (long) i * 12;
            memPutShort(dstAddr + o, a);     memPutShort(dstAddr + o + 2, b);  memPutShort(dstAddr + o + 4, d);
            memPutShort(dstAddr + o + 6, b); memPutShort(dstAddr + o + 8, c);  memPutShort(dstAddr + o + 10, d);
        }

        uploadAndDraw(dst, triIndexCount, GL11.GL_UNSIGNED_SHORT, 2);
    }

    /**
     * Convert a client-side ByteBuffer of quad indices (type-agnostic) to triangles.
     */
    public static void drawQuadElementsAsTriangles(int count, int type, ByteBuffer indices) {
        if (count == 0) return;
        assert count % 4 == 0;
        final int quadCount = count / 4;
        final int triIndexCount = quadCount * 6;
        final long srcAddr = memAddress0(indices) + indices.position();

        final ByteBuffer dst = memAlloc(triIndexCount * 4);
        final long dstAddr = memAddress0(dst);

        for (int i = 0; i < quadCount; i++) {
            final int a, b, c, d;
            switch (type) {
                case GL11.GL_UNSIGNED_INT -> {
                    final long p = srcAddr + (long) i * 16;
                    a = memGetInt(p); b = memGetInt(p + 4); c = memGetInt(p + 8); d = memGetInt(p + 12);
                }
                case GL11.GL_UNSIGNED_SHORT -> {
                    final long p = srcAddr + (long) i * 8;
                    a = memGetShort(p) & 0xFFFF; b = memGetShort(p + 2) & 0xFFFF;
                    c = memGetShort(p + 4) & 0xFFFF; d = memGetShort(p + 6) & 0xFFFF;
                }
                case GL11.GL_UNSIGNED_BYTE -> {
                    final long p = srcAddr + (long) i * 4;
                    a = memGetByte(p) & 0xFF; b = memGetByte(p + 1) & 0xFF;
                    c = memGetByte(p + 2) & 0xFF; d = memGetByte(p + 3) & 0xFF;
                }
                default -> { memFree(dst); return; }
            }
            final long o = (long) i * 24;
            memPutInt(dstAddr + o, a);     memPutInt(dstAddr + o + 4, b);  memPutInt(dstAddr + o + 8, d);
            memPutInt(dstAddr + o + 12, b); memPutInt(dstAddr + o + 16, c); memPutInt(dstAddr + o + 20, d);
        }

        uploadAndDraw(dst, triIndexCount, GL11.GL_UNSIGNED_INT, 4);
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
        if (scratchEboId != 0) {
            GL15.glDeleteBuffers(scratchEboId);
            scratchEboId = 0;
            scratchEboCapacity = 0;
        }
    }
}
