package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.VertexAttribState;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class AttribSnapshot {

    public record AttribDesc(int location, int size, int type, boolean normalized, int stride, long offset, int sourceVboId, ByteBuffer readBuffer, long readBufferBaseOffset) {
        public int effectiveStride() {
            return stride != 0 ? stride : size * VertexAttribState.Attrib.glTypeSizeBytes(type);
        }

        public int typeSizeBytes() {
            return VertexAttribState.Attrib.glTypeSizeBytes(type);
        }
    }

    private final AttribDesc[] perLocation;
    private final List<ByteBuffer> allocatedBuffers;
    private boolean freed;

    private AttribSnapshot(AttribDesc[] perLocation, List<ByteBuffer> allocatedBuffers) {
        this.perLocation = perLocation;
        this.allocatedBuffers = allocatedBuffers;
    }

    public AttribDesc get(int location) {
        return (location >= 0 && location < perLocation.length) ? perLocation[location] : null;
    }

    public int maxLocations() {
        return perLocation.length;
    }

    public void free() {
        if (freed) return;
        freed = true;
        for (ByteBuffer buf : allocatedBuffers) {
            MemoryUtilities.memFree(buf);
        }
        allocatedBuffers.clear();
    }

    /**
     * Snapshot attribs over a tight vertex window {@code [firstVertex, firstVertex+vertexCount)}.
     *
     * <p>Hot path (single VBO backing every enabled attrib — the typical draw) runs without
     * allocating any hashmaps. A second distinct VBO promotes to the map-backed fallback.
     */
    public static AttribSnapshot snapshot(int firstVertex, int vertexCount) {
        final AttribDesc[] out = new AttribDesc[VertexAttribState.MAX_ATTRIBS];
        final List<ByteBuffer> allocated = new ArrayList<>();
        final int prevVBO = GLStateManager.getBoundVBO();

        // Pass 1 — unioned [start,end] per vboId. Hot path: one source VBO, zero allocations.
        int rangeSoleVboId = 0;
        long rangeSoleStart = Long.MAX_VALUE;
        long rangeSoleEnd = Long.MIN_VALUE;
        Int2ObjectOpenHashMap<long[]> vboRanges = null;

        final long lastVtx = (long) firstVertex + vertexCount - 1;
        for (int i = 0; i < VertexAttribState.MAX_ATTRIBS; i++) {
            final VertexAttribState.Attrib a = VertexAttribState.get(i);
            if (!a.enabled || a.vboId == 0) continue;
            final int stride = a.effectiveStride();
            final long typeBytes = (long) a.size * VertexAttribState.Attrib.glTypeSizeBytes(a.type);
            final long start = a.offset + (long) firstVertex * stride;
            final long end = a.offset + lastVtx * stride + typeBytes;

            if (vboRanges != null) {
                long[] r = vboRanges.get(a.vboId);
                if (r == null) {
                    vboRanges.put(a.vboId, new long[] { start, end });
                } else {
                    if (start < r[0]) r[0] = start;
                    if (end > r[1]) r[1] = end;
                }
            } else if (rangeSoleVboId == 0 || rangeSoleVboId == a.vboId) {
                rangeSoleVboId = a.vboId;
                if (start < rangeSoleStart) rangeSoleStart = start;
                if (end > rangeSoleEnd) rangeSoleEnd = end;
            } else {
                // Spill: second distinct vboId. Promote the accumulated range to the map.
                vboRanges = new Int2ObjectOpenHashMap<>();
                vboRanges.put(rangeSoleVboId, new long[] { rangeSoleStart, rangeSoleEnd });
                vboRanges.put(a.vboId, new long[] { start, end });
            }
        }

        // Pass 2 — readback cache. Same spill pattern.
        int readSoleVboId = 0;
        ByteBuffer readSoleBuf = null;
        long readSoleOffset = 0;
        Int2ObjectOpenHashMap<ByteBuffer> vboBuffers = null;
        Int2LongOpenHashMap vboReadOffsets = null;

        try {
            for (int i = 0; i < VertexAttribState.MAX_ATTRIBS; i++) {
                final VertexAttribState.Attrib a = VertexAttribState.get(i);
                if (!a.enabled) continue;

                if (a.vboId != 0) {
                    ByteBuffer buf;
                    long readOffset;
                    if (vboBuffers != null) {
                        buf = vboBuffers.get(a.vboId);
                        readOffset = buf != null ? vboReadOffsets.get(a.vboId) : 0;
                    } else if (readSoleVboId == a.vboId) {
                        buf = readSoleBuf;
                        readOffset = readSoleOffset;
                    } else {
                        buf = null;
                        readOffset = 0;
                    }

                    if (buf == null) {
                        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, a.vboId);
                        final int bufferSize = GLStateManager.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
                        if (bufferSize <= 0) {
                            GLStateManager.LOGGER.warn("[VBO Readback] GL_BUFFER_SIZE={} for VBO {}", bufferSize, a.vboId);
                            continue;
                        }

                        final long rStart;
                        final long rEnd;
                        if (vboRanges != null) {
                            final long[] range = vboRanges.get(a.vboId);
                            rStart = range[0];
                            rEnd = range[1];
                        } else {
                            rStart = rangeSoleStart;
                            rEnd = rangeSoleEnd;
                        }
                        final long ro = Math.max(0, rStart);
                        final int rs = (int) (Math.min(bufferSize, rEnd) - ro);
                        if (rs <= 0) continue;

                        buf = MemoryUtilities.memAlloc(rs);
                        GLStateManager.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, ro, buf);
                        allocated.add(buf);
                        readOffset = ro;

                        if (vboBuffers != null) {
                            vboBuffers.put(a.vboId, buf);
                            vboReadOffsets.put(a.vboId, ro);
                        } else if (readSoleVboId == 0) {
                            readSoleVboId = a.vboId;
                            readSoleBuf = buf;
                            readSoleOffset = ro;
                        } else {
                            vboBuffers = new Int2ObjectOpenHashMap<>();
                            vboReadOffsets = new Int2LongOpenHashMap();
                            vboBuffers.put(readSoleVboId, readSoleBuf);
                            vboReadOffsets.put(readSoleVboId, readSoleOffset);
                            vboBuffers.put(a.vboId, buf);
                            vboReadOffsets.put(a.vboId, ro);
                        }
                    }
                    out[i] = new AttribDesc(i, a.size, a.type, a.normalized, a.stride, a.offset, a.vboId, buf, readOffset);
                } else if (a.clientPointer != null) {
                    out[i] = new AttribDesc(i, a.size, a.type, a.normalized, a.stride, a.clientPointer.position(), 0, a.clientPointer, 0L);
                }
            }
        } finally {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        }

        return new AttribSnapshot(out, allocated);
    }
}
