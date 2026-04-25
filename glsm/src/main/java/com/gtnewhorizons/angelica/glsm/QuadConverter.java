package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
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
            eboId = RENDER_BACKEND.genBuffers();
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

        RENDER_BACKEND.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        RENDER_BACKEND.bufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_STATIC_DRAW);

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
        final int prevEbo = GLStateManager.getBoundEBO();
        ensureCapacity(first / 4 + quadCount);
        RENDER_BACKEND.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        // Index offset: first vertex / 4 quads * 6 indices * 4 bytes per int
        final long indexOffset = (long) (first / 4) * 6 * 4;
        RENDER_BACKEND.drawElements(GL11.GL_TRIANGLES, quadCount * 6, INDEX_TYPE, indexOffset);
        RENDER_BACKEND.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
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
            scratchEboId = RENDER_BACKEND.genBuffers();
        }

        RENDER_BACKEND.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, scratchEboId);

        if (needed > scratchEboCapacity) {
            // Power-of-2 growth — allocate full capacity, upload actual data
            int newCap = Math.max(4096, scratchEboCapacity);
            while (newCap < needed) newCap *= 2;
            RENDER_BACKEND.bufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, newCap, GL15.GL_STREAM_DRAW);
            scratchEboCapacity = newCap;
        }
        RENDER_BACKEND.bufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, dst);

        RENDER_BACKEND.drawElements(GL11.GL_TRIANGLES, triIndexCount, indexType, 0L);

        RENDER_BACKEND.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEbo);
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

        final int bytesPerIndex = VertexAttribState.Attrib.glTypeSizeBytes(type);

        final ByteBuffer src = memAlloc(indexCount * bytesPerIndex);
        RENDER_BACKEND.getBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, offset, src);

        final ByteBuffer dst = memAlloc(triIndexCount * 4);
        triangulateQuads(memAddress0(src), type, memAddress0(dst), GL11.GL_UNSIGNED_INT, quadCount);

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
        final long srcAddr = memAddress0(indices) + (long) indices.position() * 4;
        triangulateQuads(srcAddr, GL11.GL_UNSIGNED_INT, memAddress0(dst), GL11.GL_UNSIGNED_INT, quadCount);

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
        final long srcAddr = memAddress0(indices) + (long) indices.position() * 2;
        triangulateQuads(srcAddr, GL11.GL_UNSIGNED_SHORT, memAddress0(dst), GL11.GL_UNSIGNED_SHORT, quadCount);

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
        triangulateQuads(srcAddr, type, memAddress0(dst), GL11.GL_UNSIGNED_INT, quadCount);

        uploadAndDraw(dst, triIndexCount, GL11.GL_UNSIGNED_INT, 4);
    }

    /**
     * Clean up the shared EBO.
     */
    public static void destroy() {
        if (eboId != 0) {
            GLStateManager.glDeleteBuffers(eboId);
            eboId = 0;
            maxQuads = 0;
        }
        if (scratchEboId != 0) {
            GLStateManager.glDeleteBuffers(scratchEboId);
            scratchEboId = 0;
            scratchEboCapacity = 0;
        }
    }

    /**
     * Read one index at {@code srcAddr + i * sizeof(srcType)} and return its
     * zero-extended unsigned value.
     */
    private static int readIndex(long srcAddr, int srcType, int i) {
        return switch (srcType) {
            case GL11.GL_UNSIGNED_BYTE  -> memGetByte(srcAddr + i)          & 0xFF;
            case GL11.GL_UNSIGNED_SHORT -> memGetShort(srcAddr + i * 2L)    & 0xFFFF;
            case GL11.GL_UNSIGNED_INT   -> memGetInt(srcAddr + i * 4L);
            default -> throw new IllegalArgumentException("unsupported index type 0x" + Integer.toHexString(srcType));
        };
    }

    /**
     * Write one index at {@code dstAddr + i * sizeof(dstType)}.
     */
    private static void writeIndex(long dstAddr, int dstType, int i, int value) {
        switch (dstType) {
            case GL11.GL_UNSIGNED_SHORT -> memPutShort(dstAddr + i * 2L, (short) value);
            case GL11.GL_UNSIGNED_INT   -> memPutInt(dstAddr + i * 4L, value);
            default -> throw new IllegalArgumentException("unsupported index type 0x" + Integer.toHexString(dstType));
        }
    }

    /**
     * Read {@code quadCount} quads from {@code srcAddr} and emit 6 triangle indices
     * per quad at {@code dstAddr} using {@code GL_LAST_VERTEX_CONVENTION}:
     * {@code (a,b,d), (b,c,d)}. v3 is the provoking vertex for both triangles;
     * diagonal runs b→d. Matches Mesa's {@code do_quad} LAST path.
     *
     * <p>Caller allocates dst of size {@code quadCount * 6 * sizeof(dstType)}.
     *
     * <p>Supported src types: {@code GL_UNSIGNED_BYTE} / {@code GL_UNSIGNED_SHORT}
     * / {@code GL_UNSIGNED_INT}. Supported dst types: {@code GL_UNSIGNED_SHORT} /
     * {@code GL_UNSIGNED_INT}. Widening only.
     */
    public static void triangulateQuads(long srcAddr, int srcType, long dstAddr, int dstType, int quadCount) {
        triangulateQuads(srcAddr, srcType, dstAddr, dstType, quadCount, 0);
    }

    /**
     * Same as {@link #triangulateQuads(long, int, long, int, int)} but subtracts
     * {@code minVtx} from every emitted index, rebasing to a 0-based buffer in
     * one pass.
     */
    public static void triangulateQuads(long srcAddr, int srcType, long dstAddr, int dstType, int quadCount, int minVtx) {
        for (int i = 0; i < quadCount; i++) {
            final int a = readIndex(srcAddr, srcType, i * 4)     - minVtx;
            final int b = readIndex(srcAddr, srcType, i * 4 + 1) - minVtx;
            final int c = readIndex(srcAddr, srcType, i * 4 + 2) - minVtx;
            final int d = readIndex(srcAddr, srcType, i * 4 + 3) - minVtx;
            final int o = i * 6;
            writeIndex(dstAddr, dstType, o,     a);
            writeIndex(dstAddr, dstType, o + 1, b);
            writeIndex(dstAddr, dstType, o + 2, d);
            writeIndex(dstAddr, dstType, o + 3, b);
            writeIndex(dstAddr, dstType, o + 4, c);
            writeIndex(dstAddr, dstType, o + 5, d);
        }
    }

    /**
     * Copy {@code count} indices from {@code srcAddr} to {@code dstAddr}, converting
     * from {@code srcType} to {@code dstType}.
     *
     * <p>Supported src types: {@code GL_UNSIGNED_BYTE} / {@code GL_UNSIGNED_SHORT} /
     * {@code GL_UNSIGNED_INT}. Supported dst types: {@code GL_UNSIGNED_SHORT} /
     * {@code GL_UNSIGNED_INT}. Widening only; narrowing is rejected by
     * {@link #writeIndex(long, int, int, int) writeIndex}.
     */
    public static void widenIndices(long srcAddr, int srcType, long dstAddr, int dstType, int count) {
        widenIndices(srcAddr, srcType, dstAddr, dstType, count, 0);
    }

    /**
     * Same as {@link #widenIndices(long, int, long, int, int)} but subtracts
     * {@code minVtx} from every written index in one pass.
     */
    public static void widenIndices(long srcAddr, int srcType, long dstAddr, int dstType, int count, int minVtx) {
        for (int i = 0; i < count; i++) {
            writeIndex(dstAddr, dstType, i, readIndex(srcAddr, srcType, i) - minVtx);
        }
    }

    /**
     * Scan {@code count} indices at {@code srcAddr} of {@code srcType}. Returns
     * {@code (max << 32) | (min & 0xFFFFFFFFL)} or {@code -1L} for an unsupported
     * type or empty range.
     */
    public static long scanMinMaxIndex(long srcAddr, int srcType, int count) {
        if (count <= 0) return -1L;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        switch (srcType) {
            case GL11.GL_UNSIGNED_BYTE -> {
                for (int i = 0; i < count; i++) {
                    final int v = memGetByte(srcAddr + i) & 0xFF;
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
            case GL11.GL_UNSIGNED_SHORT -> {
                for (int i = 0; i < count; i++) {
                    final int v = memGetShort(srcAddr + i * 2L) & 0xFFFF;
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
            }
            case GL11.GL_UNSIGNED_INT -> {
                for (int i = 0; i < count; i++) {
                    final long v = memGetInt(srcAddr + i * 4L) & 0xFFFFFFFFL;
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                // readIndex / writeIndex / rebase all route UINT through signed int.
                // Reject the > 2^31 slice so the caller bails rather than silently
                // emitting negative indices. MC never hits this in practice.
                if (max > Integer.MAX_VALUE) return -1L;
            }
            default -> {
                return -1L;
            }
        }
        return (max << 32) | (min & 0xFFFFFFFFL);
    }
}
