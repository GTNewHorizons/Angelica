package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.streaming.OrphanStreamingBuffer;
import com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;

final class InstancedTemplateRenderer {

    static final long TEMPLATE_TTL_MS = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;

    private static final int TEMPLATE_STRIDE = VERTEX_SIZE * 4;
    private static final int TEMPLATE_FLAGS = VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.NORMAL_BIT;

    private static final class TemplateMesh {
        int vao;
        int vbo;
        int vertexCount;
        int drawMode;
        long lastUsedMs;
    }

    private static final class Bucket {
        TemplateMesh mesh;
        final IntArrayList indices = new IntArrayList();
    }

    private final Reference2ObjectOpenHashMap<TemplateBuffer, TemplateMesh> meshes = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<TemplateBuffer, Bucket> buckets = new Reference2ObjectOpenHashMap<>();
    private final ObjectArrayList<Bucket> bucketPool = new ObjectArrayList<>();
    private final ObjectArrayList<Bucket> liveBuckets = new ObjectArrayList<>();
    private final float[] matScratch = new float[16];

    private PersistentStreamingBuffer persistentRing;
    private OrphanStreamingBuffer orphanRing;
    private boolean ringInitialized;
    private boolean ringUsedThisFrame;
    private int uploadBufferId;
    private ByteBuffer staging;

    int drawGroup(ObjectArrayList<TemplateBuffer> templates, FloatArrayList matrices, IntArrayList lights, IntArrayList colors, int count, long nowMs) {
        for (int i = 0; i < count; i++) {
            final TemplateBuffer template = templates.get(i);
            Bucket bucket = buckets.get(template);
            if (bucket == null) {
                bucket = bucketPool.isEmpty() ? new Bucket() : bucketPool.pop();
                bucket.mesh = meshFor(template, nowMs);
                buckets.put(template, bucket);
                liveBuckets.add(bucket);
            }
            bucket.indices.add(i);
        }

        staging = MeshBuffer.ensureCapacity(staging, count * InstancedAttribs.STRIDE, false);
        final long base = memAddress0(staging);
        long ptr = base;
        for (int b = 0, n = liveBuckets.size(); b < n; b++) {
            final IntArrayList indices = liveBuckets.get(b).indices;
            for (int j = 0, m = indices.size(); j < m; j++) {
                final int i = indices.getInt(j);
                matrices.getElements(i * 16, matScratch, 0, 16);
                for (int k = 0; k < 16; k++) {
                    memPutFloat(ptr + k * 4L, matScratch[k]);
                }
                memPutInt(ptr + InstancedAttribs.OFFSET_COLOR, colors.getInt(i));
                final int light = lights.getInt(i);
                memPutFloat(ptr + InstancedAttribs.OFFSET_LIGHTMAP, light & 0xFFFF);
                memPutFloat(ptr + InstancedAttribs.OFFSET_LIGHTMAP + 4, (light >>> 16) & 0xFFFF);
                ptr += InstancedAttribs.STRIDE;
            }
        }
        staging.position(0);
        staging.limit((int) (ptr - base));
        final long ringBase = uploadInstances(staging);
        staging.clear();

        int draws = 0;
        long recordOffset = ringBase;
        final int ringId = uploadBufferId;
        GLStateManager.instancedFfpDrawActive = true;
        for (int b = 0, n = liveBuckets.size(); b < n; b++) {
            final Bucket bucket = liveBuckets.get(b);
            final TemplateMesh mesh = bucket.mesh;
            GLStateManager.glBindVertexArray(mesh.vao);
            VAOManager.setCurrentVertexFlags(TEMPLATE_FLAGS);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, ringId);
            pointInstanceAttribs(recordOffset);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDrawArraysInstanced(mesh.drawMode, 0, mesh.vertexCount, bucket.indices.size());
            draws++;
            recordOffset += (long) bucket.indices.size() * InstancedAttribs.STRIDE;
            bucket.indices.clear();
            bucket.mesh = null;
            bucketPool.add(bucket);
        }
        GLStateManager.instancedFfpDrawActive = false;
        GLStateManager.glBindVertexArray(0);
        liveBuckets.clear();
        buckets.clear();
        return draws;
    }

    private static void pointInstanceAttribs(long base) {
        for (int c = 0; c < 4; c++) {
            GLStateManager.glVertexAttribPointer(InstancedAttribs.LOC_MATRIX_COL0 + c, 4, GL11.GL_FLOAT, false,
                InstancedAttribs.STRIDE, base + c * 16L);
        }
        GLStateManager.glVertexAttribPointer(InstancedAttribs.LOC_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true,
            InstancedAttribs.STRIDE, base + InstancedAttribs.OFFSET_COLOR);
        GLStateManager.glVertexAttribPointer(InstancedAttribs.LOC_LIGHTMAP, 2, GL11.GL_FLOAT, false,
            InstancedAttribs.STRIDE, base + InstancedAttribs.OFFSET_LIGHTMAP);
    }

    private long uploadInstances(ByteBuffer data) {
        if (!ringInitialized) {
            ringInitialized = true;
            persistentRing = PersistentStreamingBuffer.createOrNull(PersistentStreamingBuffer.DEFAULT_CAPACITY);
        }
        ringUsedThisFrame = true;
        if (persistentRing != null) {
            final int index = persistentRing.upload(data, InstancedAttribs.STRIDE);
            if (index >= 0) {
                uploadBufferId = persistentRing.getBufferId();
                return (long) index * InstancedAttribs.STRIDE;
            }
        }
        if (orphanRing == null) orphanRing = new OrphanStreamingBuffer();
        orphanRing.upload(data);
        uploadBufferId = orphanRing.getBufferId();
        return 0;
    }

    private TemplateMesh meshFor(TemplateBuffer template, long nowMs) {
        TemplateMesh mesh = meshes.get(template);
        if (mesh == null) {
            mesh = build(template);
            meshes.put(template, mesh);
        }
        mesh.lastUsedMs = nowMs;
        return mesh;
    }

    private static TemplateMesh build(TemplateBuffer template) {
        final TemplateMesh mesh = new TemplateMesh();
        mesh.vertexCount = template.vertexCount;
        mesh.drawMode = template.drawMode;
        mesh.vao = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(mesh.vao);
        mesh.vbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.vbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, template.data, GL15.GL_STATIC_DRAW);

        GLStateManager.glEnableVertexAttribArray(0);
        GLStateManager.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, TEMPLATE_STRIDE, 0L);
        GLStateManager.glEnableVertexAttribArray(1);
        GLStateManager.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, TEMPLATE_STRIDE, COLOR_INDEX * 4L);
        GLStateManager.glEnableVertexAttribArray(2);
        GLStateManager.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, TEMPLATE_STRIDE, TEX_X_INDEX * 4L);
        GLStateManager.glEnableVertexAttribArray(4);
        GLStateManager.glVertexAttribPointer(4, 3, GL11.GL_BYTE, true, TEMPLATE_STRIDE, NORMAL_INDEX * 4L);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        for (int loc = InstancedAttribs.LOC_MATRIX_COL0; loc <= InstancedAttribs.LOC_LIGHTMAP; loc++) {
            GLStateManager.glEnableVertexAttribArray(loc);
            GLStateManager.glVertexAttribDivisor(loc, 1);
        }
        VAOManager.setCurrentVertexFlags(TEMPLATE_FLAGS);
        GLStateManager.glBindVertexArray(0);
        return mesh;
    }

    void endFrame() {
        if (ringUsedThisFrame && persistentRing != null) {
            persistentRing.postDraw();
        }
        ringUsedThisFrame = false;
    }

    void sweep(long now) {
        if (meshes.isEmpty()) return;
        final ObjectIterator<TemplateMesh> it = meshes.values().iterator();
        while (it.hasNext()) {
            final TemplateMesh mesh = it.next();
            if (now - mesh.lastUsedMs <= TEMPLATE_TTL_MS) continue;
            delete(mesh);
            it.remove();
        }
    }

    void clear() {
        for (final TemplateMesh mesh : meshes.values()) {
            delete(mesh);
        }
        meshes.clear();
    }

    private static void delete(TemplateMesh mesh) {
        GLStateManager.glDeleteBuffers(mesh.vbo);
        GLStateManager.glDeleteVertexArrays(mesh.vao);
    }

    int meshCount() {
        return meshes.size();
    }

    long meshBytes() {
        long bytes = 0;
        for (final TemplateMesh mesh : meshes.values()) {
            bytes += (long) mesh.vertexCount * TEMPLATE_STRIDE;
        }
        return bytes;
    }
}
