package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

public final class IndexedDrawBatchBuilder {

    private final List<IndexedDrawCapture> captures = new ArrayList<>();

    public boolean isEmpty() {
        return captures.isEmpty();
    }

    public void add(IndexedDrawCapture capture) {
        captures.add(capture);
    }

    public List<IndexedDrawCapture> getCaptures() {
        return captures;
    }

    public List<IndexedDrawBatch> build() {
        final List<IndexedDrawBatch> out = new ArrayList<>();
        if (captures.isEmpty()) return out;

        final int prevVBO = GLSMBuffers.getBoundVBO();
        final int prevEBO = GLSMVertexInput.getBoundEBO();

        final List<GroupResult> results = new ArrayList<>();
        boolean success = false;
        try {
            final Object2ObjectLinkedOpenHashMap<AttribLayoutKey, List<IndexedDrawCapture>> groups = new Object2ObjectLinkedOpenHashMap<>();
            for (IndexedDrawCapture c : captures) {
                groups.computeIfAbsent(c.layoutKey, k -> new ArrayList<>()).add(c);
            }

            for (List<IndexedDrawCapture> group : groups.values()) {
                results.add(buildGroup(group));
            }
            // Placeholder fill is deferred until every group's GL work has succeeded.
            for (GroupResult r : results) r.commit();
            for (GroupResult r : results) out.add(r.batch);
            success = true;
        } finally {
            if (!success) {
                for (GroupResult r : results) r.batch.delete();
            }
            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevEBO);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        }

        return out;
    }

    private record GroupResult(IndexedDrawBatch batch, List<IndexedDrawCapture> captures,
                               int[] drawModes, int[] indexCounts, long[] indexOffsets) {
        void commit() {
            for (int i = 0; i < captures.size(); i++) {
                captures.get(i).placeholder.fill(batch, drawModes[i], indexCounts[i], GL11.GL_UNSIGNED_INT, indexOffsets[i]);
            }
        }
    }

    private static GroupResult buildGroup(List<IndexedDrawCapture> group) {
        long totalVertexBytes = 0;
        long totalIndexBytes = 0;
        for (IndexedDrawCapture c : group) {
            totalVertexBytes += (long) c.vertexCount * c.layoutKey.stride();
            totalIndexBytes += (long) c.indexCount * 4;   // always GL_UNSIGNED_INT
        }
        if (totalVertexBytes > Integer.MAX_VALUE || totalIndexBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("batch exceeds 2GiB — split needed");
        }

        int sharedVBO = 0;
        int sharedEBO = 0;
        ByteBuffer eboData = null;
        boolean success = false;

        final int n = group.size();
        final int[] drawModes = new int[n];
        final int[] indexCounts = new int[n];
        final long[] indexOffsets = new long[n];

        try {
            sharedVBO = GLSMBuffers.glGenBuffers();
            sharedEBO = GLSMBuffers.glGenBuffers();
            eboData = MemoryUtilities.memAlloc((int) totalIndexBytes);

            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, sharedVBO);
            GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, totalVertexBytes, GL15.GL_STATIC_DRAW);

            int vboBytePos = 0;
            int eboBytePos = 0;
            int vertexBase = 0;
            final long eboAddr = memAddress0(eboData);

            for (int g = 0; g < n; g++) {
                final IndexedDrawCapture c = group.get(g);
                final int vBytes = c.vertexCount * c.layoutKey.stride();
                GLSMBuffers.glBufferSubData(GL15.GL_ARRAY_BUFFER, vboBytePos, c.vertexData);

                final long srcIdxAddr = memAddress0(c.indexData);
                final int idxBytes = c.indexCount * 4;
                if (vertexBase == 0) {
                    // First capture (or a group that starts at 0) — indices are already
                    // rebased to [0..vertexCount) in IndexedDrawCapture, so a raw bulk copy
                    // is correct and strictly faster than the per-index loop.
                    memCopy(srcIdxAddr, eboAddr + eboBytePos, idxBytes);
                } else {
                    final long dstBase = eboAddr + eboBytePos;
                    for (int i = 0; i < c.indexCount; i++) {
                        memPutInt(dstBase + i * 4L, memGetInt(srcIdxAddr + i * 4L) + vertexBase);
                    }
                }

                drawModes[g] = c.drawMode;
                indexCounts[g] = c.indexCount;
                indexOffsets[g] = eboBytePos;

                vboBytePos += vBytes;
                eboBytePos += idxBytes;
                vertexBase += c.vertexCount;
            }

            eboData.position(0).limit((int) totalIndexBytes);

            GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEBO);
            GLSMBuffers.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, eboData, GL15.GL_STATIC_DRAW);

            success = true;
            return new GroupResult(new IndexedDrawBatch(group.get(0).layoutKey, sharedVBO, sharedEBO),
                group, drawModes, indexCounts, indexOffsets);
        } finally {
            if (eboData != null) MemoryUtilities.memFree(eboData);
            if (!success) {
                if (sharedVBO != 0) GLSMBuffers.glDeleteBuffers(sharedVBO);
                if (sharedEBO != 0) GLSMBuffers.glDeleteBuffers(sharedEBO);
            }
        }
    }
}
