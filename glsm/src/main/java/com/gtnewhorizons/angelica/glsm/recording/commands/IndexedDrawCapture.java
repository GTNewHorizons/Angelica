package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.QuadConverter;
import com.gtnewhorizons.angelica.glsm.recording.AttribSnapshot;
import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetLong;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutLong;


/** {@link #create} returns null on any recoverable validation failure; callers must not record the placeholder. */
public final class IndexedDrawCapture {
    public final AttribLayoutKey layoutKey;
    public final int drawMode;
    public final int vertexCount;
    public final int indexCount;
    public final ByteBuffer vertexData;
    public final ByteBuffer indexData;
    public final int tightStride;
    public final BatchedIndexedDrawCmd placeholder;
    private boolean freed;

    public IndexedDrawCapture(AttribLayoutKey layoutKey, int drawMode, int vertexCount, int indexCount,
                              ByteBuffer vertexData, ByteBuffer indexData, int tightStride,
                              BatchedIndexedDrawCmd placeholder) {
        this.layoutKey = layoutKey;
        this.drawMode = drawMode;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.vertexData = vertexData;
        this.indexData = indexData;
        this.tightStride = tightStride;
        this.placeholder = placeholder;
    }

    public void freeBuffers() {
        if (freed) return;
        freed = true;
        if (vertexData != null) MemoryUtilities.memFree(vertexData);
        if (indexData != null) MemoryUtilities.memFree(indexData);
    }

    public boolean isFreed() {
        return freed;
    }

    private static final Set<String> WARN_ONCE = ConcurrentHashMap.newKeySet();

    private static void warnOnce(String key, String fmt, Object... args) {
        if (WARN_ONCE.add(key)) GLStateManager.LOGGER.warn(fmt, args);
    }

    public static IndexedDrawCapture create(int mode, int indicesCount, int srcIndexType, long indicesOffset, int eboId) {
        if (indicesCount == 0) return null;
        if (eboId == 0) {
            warnOnce("no-ebo", "[IndexedDrawCapture] glDrawElements in display list with no EBO bound — skipping (mode={} count={} offset={})", mode, indicesCount, indicesOffset);
            return null;
        }
        if (mode == GL11.GL_QUADS && (indicesCount % 4) != 0) {
            warnOnce("quads-misaligned", "[IndexedDrawCapture] GL_QUADS with indicesCount={} not a multiple of 4 — skipping", indicesCount);
            return null;
        }
        final int srcIndexSize = VertexAttribState.Attrib.glTypeSizeBytes(srcIndexType);
        if (srcIndexSize <= 0) return null;

        final int prevEBO = GLStateManager.getBoundEBO();
        final int prevVBO = GLStateManager.getBoundVBO();
        ByteBuffer eboSrc = null;
        AttribSnapshot snap = null;
        ByteBuffer vertexData = null;
        ByteBuffer indexData = null;
        boolean success = false;
        try {
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            final int eboBufferSize = GLStateManager.glGetBufferParameteri(GL15.GL_ELEMENT_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
            if (eboBufferSize <= 0) {
                warnOnce("ebo-size", "[IndexedDrawCapture] GL_BUFFER_SIZE={} for EBO {}", eboBufferSize, eboId);
                return null;
            }
            final long eboReadSize = (long) indicesCount * srcIndexSize;
            if (indicesOffset < 0 || indicesOffset + eboReadSize > eboBufferSize) {
                warnOnce("ebo-range", "[IndexedDrawCapture] EBO range {}+{} exceeds buffer size {} — skipping", indicesOffset, eboReadSize, eboBufferSize);
                return null;
            }
            eboSrc = MemoryUtilities.memAlloc((int) eboReadSize);
            GLStateManager.glGetBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesOffset, eboSrc);
            final long eboSrcAddr = MemoryUtilities.memAddress0(eboSrc);

            final long minMax = QuadConverter.scanMinMaxIndex(eboSrcAddr, srcIndexType, indicesCount);
            if (minMax == -1L) {
                warnOnce("min-max", "[IndexedDrawCapture] failed to determine min/max vertex for indexType={}", srcIndexType);
                return null;
            }
            final int minVtx = (int) (minMax & 0xFFFFFFFFL);
            final int maxVtx = (int) (minMax >>> 32);
            final int vertexCount = maxVtx - minVtx + 1;

            snap = AttribSnapshot.snapshot(minVtx, vertexCount);

            int enabledCount = 0;
            for (int i = 0; i < VertexAttribState.MAX_ATTRIBS; i++) {
                if (snap.get(i) != null) enabledCount++;
            }
            if (enabledCount == 0) {
                warnOnce("no-attribs", "[IndexedDrawCapture] no enabled attributes — skipping");
                return null;
            }
            final int[] locations = new int[enabledCount];
            final int[] sizes = new int[enabledCount];
            final int[] types = new int[enabledCount];
            final boolean[] normalized = new boolean[enabledCount];
            int k = 0;
            int tightStride = 0;
            int expectedSrcOffset = -1;
            ByteBuffer sharedSrcBuffer = null;
            long sharedSrcBase = 0;
            int sharedSrcStride = -1;
            boolean tightSrc = true;
            for (int i = 0; i < VertexAttribState.MAX_ATTRIBS; i++) {
                final AttribSnapshot.AttribDesc d = snap.get(i);
                if (d == null) continue;
                locations[k] = i;
                sizes[k] = d.size();
                types[k] = d.type();
                normalized[k] = d.normalized();
                final int attribBytes = d.size() * d.typeSizeBytes();
                if (tightSrc) {
                    final int relOffset = (int) (d.offset() - d.readBufferBaseOffset());
                    if (k == 0) {
                        sharedSrcBuffer = d.readBuffer();
                        sharedSrcStride = d.effectiveStride();
                        sharedSrcBase = MemoryUtilities.memAddress0(sharedSrcBuffer) + relOffset + (long) minVtx * sharedSrcStride;
                        expectedSrcOffset = relOffset + attribBytes;
                    } else if (d.readBuffer() != sharedSrcBuffer || d.effectiveStride() != sharedSrcStride || relOffset != expectedSrcOffset) {
                        tightSrc = false;
                    } else {
                        expectedSrcOffset += attribBytes;
                    }
                }
                tightStride += attribBytes;
                k++;
            }
            if (tightSrc && sharedSrcStride != tightStride) tightSrc = false;

            vertexData = MemoryUtilities.memAlloc(vertexCount * tightStride);
            final long dstAddr = MemoryUtilities.memAddress0(vertexData);
            if (tightSrc) {
                MemoryUtilities.memCopy(sharedSrcBase, dstAddr, (long) vertexCount * tightStride);
            } else {
                int attribOutBase = 0;
                for (int a = 0; a < enabledCount; a++) {
                    final AttribSnapshot.AttribDesc d = snap.get(locations[a]);
                    final int attribBytes = d.size() * d.typeSizeBytes();
                    final int srcStride = d.effectiveStride();
                    final long srcStart = MemoryUtilities.memAddress0(d.readBuffer()) + (d.offset() - d.readBufferBaseOffset()) + (long) minVtx * srcStride;
                    final long dstStart = dstAddr + attribOutBase;
                    copyStrided(srcStart, srcStride, dstStart, tightStride, attribBytes, vertexCount);
                    attribOutBase += attribBytes;
                }
            }

            final int bakedDrawMode;
            final int bakedIndexCount;
            if (mode == GL11.GL_QUADS) {
                final int quadCount = indicesCount / 4;
                bakedIndexCount = quadCount * 6;
                indexData = MemoryUtilities.memAlloc(bakedIndexCount * 4);
                QuadConverter.triangulateQuads(eboSrcAddr, srcIndexType, MemoryUtilities.memAddress0(indexData), GL11.GL_UNSIGNED_INT, quadCount, minVtx);
                bakedDrawMode = GL11.GL_TRIANGLES;
            } else {
                bakedIndexCount = indicesCount;
                indexData = MemoryUtilities.memAlloc(bakedIndexCount * 4);
                QuadConverter.widenIndices(eboSrcAddr, srcIndexType, MemoryUtilities.memAddress0(indexData), GL11.GL_UNSIGNED_INT, indicesCount, minVtx);
                bakedDrawMode = mode;
            }

            final AttribLayoutKey layoutKey = new AttribLayoutKey(locations, sizes, types, normalized);
            final BatchedIndexedDrawCmd placeholder = new BatchedIndexedDrawCmd();

            final IndexedDrawCapture out = new IndexedDrawCapture(layoutKey, bakedDrawMode, vertexCount, bakedIndexCount, vertexData, indexData, tightStride, placeholder);
            success = true;
            return out;
        } finally {
            if (eboSrc != null) MemoryUtilities.memFree(eboSrc);
            if (snap != null) snap.free();
            if (!success) {
                if (vertexData != null) MemoryUtilities.memFree(vertexData);
                if (indexData != null) MemoryUtilities.memFree(indexData);
            }
            GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        }
    }

    /**
     * Copy {@code attribBytes} per vertex from {@code src} (stride {@code srcStride}) to
     * {@code dst} (stride {@code dstStride}) for {@code vertexCount} vertices.
     *
     * <p>Specializes on the common attribute sizes (4 / 8 / 12 / 16) so the hot path is
     * one or two {@code memPut} calls per vertex instead of a JNI-ish {@code memCopy}
     * dispatch. Falls through to {@code memCopy} for exotic sizes.
     */
    private static void copyStrided(long src, int srcStride, long dst, int dstStride, int attribBytes, int vertexCount) {
        switch (attribBytes) {
            case 4 -> {
                for (int v = 0; v < vertexCount; v++) {
                    memPutInt(dst, memGetInt(src));
                    src += srcStride; dst += dstStride;
                }
            }
            case 8 -> {
                for (int v = 0; v < vertexCount; v++) {
                    memPutLong(dst, memGetLong(src));
                    src += srcStride; dst += dstStride;
                }
            }
            case 12 -> {
                for (int v = 0; v < vertexCount; v++) {
                    memPutLong(dst, memGetLong(src));
                    memPutInt(dst + 8, memGetInt(src + 8));
                    src += srcStride; dst += dstStride;
                }
            }
            case 16 -> {
                for (int v = 0; v < vertexCount; v++) {
                    memPutLong(dst, memGetLong(src));
                    memPutLong(dst + 8, memGetLong(src + 8));
                    src += srcStride; dst += dstStride;
                }
            }
            default -> {
                for (int v = 0; v < vertexCount; v++) {
                    MemoryUtilities.memCopy(src, dst, attribBytes);
                    src += srcStride; dst += dstStride;
                }
            }
        }
    }
}
