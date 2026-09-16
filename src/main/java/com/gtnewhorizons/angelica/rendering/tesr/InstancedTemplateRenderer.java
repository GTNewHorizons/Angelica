package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.ffp.CubeInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.FfpExtendedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.UnitCubeMesh;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.InstanceColumns;
import com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrGroups.TexRun;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.NORMAL_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.TEX_X_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;

final class InstancedTemplateRenderer {

    static final long TEMPLATE_TTL_MS = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;

    private static final int TEMPLATE_STRIDE = VERTEX_SIZE * 4;
    private static final int TEMPLATE_FLAGS = VertexFlags.COLOR_BIT | VertexFlags.TEXTURE_BIT | VertexFlags.NORMAL_BIT;

    private static long bucketEpochSource;

    private static final class TemplateMesh {
        int vao;
        int vbo;
        int extVbo;
        int vertexCount;
        int drawMode;
        long lastUsedMs;
    }

    private static final class Bucket {
        TemplateMesh mesh;
        final IntArrayList indices = new IntArrayList();
    }

    private final Reference2ObjectOpenHashMap<TemplateBuffer, TemplateMesh> meshes = new Reference2ObjectOpenHashMap<>();
    private final ObjectArrayList<Bucket> bucketPool = new ObjectArrayList<>();
    private final ObjectArrayList<Bucket> liveBuckets = new ObjectArrayList<>();

    private final InstanceRing ring;
    private ByteBuffer staging;

    InstancedTemplateRenderer(InstanceRing ring) {
        this.ring = ring;
    }

    int drawTemplates(InstanceColumns cols, TexRun run, long nowMs) {
        final long epoch = ++bucketEpochSource;
        for (TexRun seg = run; seg != null; seg = seg.next) {
            for (int i = seg.start, end = seg.end; i < end; i++) {
                final TemplateBuffer template = cols.templates.get(i);
                final Bucket bucket;
                if (template.bucketEpoch == epoch) {
                    bucket = liveBuckets.get(template.bucketIndex);
                } else {
                    bucket = bucketPool.isEmpty() ? new Bucket() : bucketPool.pop();
                    bucket.mesh = meshFor(template, nowMs);
                    template.bucketEpoch = epoch;
                    template.bucketIndex = liveBuckets.size();
                    liveBuckets.add(bucket);
                }
                bucket.indices.add(i);
            }
        }

        staging = MeshBuffer.ensureCapacity(staging, run.parts * InstancedAttribs.STRIDE, false);
        final long base = memAddress0(staging);
        long ptr = base;
        for (int b = 0, n = liveBuckets.size(); b < n; b++) {
            final IntArrayList indices = liveBuckets.get(b).indices;
            for (int j = 0, m = indices.size(); j < m; j++) {
                final int i = indices.getInt(j);
                InstancedAttribs.writeHead(ptr, cols.matrices, i * 16, cols.colors.getInt(i), cols.overlays.getInt(i), cols.infos.getLong(i));
                InstancedAttribs.writeLightmap(ptr + InstancedAttribs.OFFSET_LIGHTMAP, cols.lights.getInt(i));
                ptr += InstancedAttribs.STRIDE;
            }
        }
        final long ringBase = uploadStaging(base, ptr, InstancedAttribs.STRIDE);

        int draws = 0;
        long recordOffset = ringBase;
        neutralizeExtendedAttribs();
        GLStateManager.ffpInstancing = Instancing.TEMPLATE;
        for (int b = 0, n = liveBuckets.size(); b < n; b++) {
            final Bucket bucket = liveBuckets.get(b);
            final TemplateMesh mesh = bucket.mesh;
            bindRingTo(mesh.vao, TEMPLATE_FLAGS);
            InstancedAttribs.pointTemplate(recordOffset);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLStateManager.glDrawArraysInstanced(mesh.drawMode, 0, mesh.vertexCount, bucket.indices.size());
            draws++;
            recordOffset += (long) bucket.indices.size() * InstancedAttribs.STRIDE;
            bucket.indices.clear();
            bucket.mesh = null;
            bucketPool.add(bucket);
        }
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
        liveBuckets.clear();
        return draws;
    }

    void drawCubes(InstanceColumns cols, TexRun run) {
        staging = MeshBuffer.ensureCapacity(staging, run.instances * CubeInstancedAttribs.STRIDE, false);
        final long base = memAddress0(staging);
        long ptr = base;
        for (TexRun seg = run; seg != null; seg = seg.next) {
            for (int i = seg.start, end = seg.end; i < end; i++) {
                final int off = i * 16;
                final CubeParams[] partCubes = cols.cubes.get(i);
                final float scale = cols.scales.getFloat(i);
                final int color = cols.colors.getInt(i);
                final int light = cols.lights.getInt(i);
                final int overlay = cols.overlays.getInt(i);
                final long info = cols.infos.getLong(i);
                for (int p = 0, n = partCubes.length; p < n; p++) {
                    final CubeParams cube = partCubes[p];
                    cube.writeRows(ptr, cols.matrices, off, scale);
                    InstancedAttribs.writeTail(ptr, color, overlay, info);
                    InstancedAttribs.writeLightmap(ptr + CubeInstancedAttribs.OFFSET_LIGHTMAP_SCALE, light);
                    cube.writeTexture(ptr);
                    ptr += CubeInstancedAttribs.STRIDE;
                }
            }
        }
        final long ringBase = uploadStaging(base, ptr, CubeInstancedAttribs.STRIDE);

        neutralizeExtendedAttribs();
        bindRingTo(UnitCubeMesh.vao(), UnitCubeMesh.VERTEX_FLAGS);
        CubeInstancedAttribs.pointInstanceAttribs(ringBase);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.ffpInstancing = Instancing.CUBE;
        GLStateManager.glDrawArraysInstanced(GL11.GL_QUADS, 0, UnitCubeMesh.VERTEX_COUNT, run.instances);
        GLStateManager.ffpInstancing = Instancing.NONE;
        GLStateManager.glBindVertexArray(0);
    }

    private long uploadStaging(long base, long end, int stride) {
        staging.position(0);
        staging.limit((int) (end - base));
        final long ringBase = ring.upload(staging, stride);
        staging.clear();
        return ringBase;
    }

    private void bindRingTo(int vao, int vertexFlags) {
        GLStateManager.glBindVertexArray(vao);
        VAOManager.setCurrentVertexFlags(vertexFlags);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, ring.bufferId());
    }

    private static void neutralizeExtendedAttribs() {
        final ImmediateExtendedAttribHandler extHandler = GLSMHooks.immediateExtendedHandler;
        if (extHandler != null && extHandler.wantsExtended()) {
            FfpExtendedAttribs.setNeutralCurrentValues();
        }
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

        mesh.extVbo = buildExt(template);
        if (mesh.extVbo != 0) {
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.extVbo);
            ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, ImmediateExtendedAttribHandler.EXT_STRIDE);
            GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        InstancedAttribs.enableHeadArrays();
        VAOManager.setCurrentVertexFlags(TEMPLATE_FLAGS);
        GLStateManager.glBindVertexArray(0);
        return mesh;
    }

    private static int buildExt(TemplateBuffer template) {
        final ImmediateExtendedAttribHandler handler = GLSMHooks.immediateExtendedHandler;
        if (handler == null || !handler.wantsExtendedCapture()) return 0;
        final int extPrim = ImmediateExtendedAttribHandler.extPrimVerts(template.drawMode, template.vertexCount);
        if (extPrim == 0) return 0;

        final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
        final ByteBuffer ext = memCalloc(template.vertexCount, extStride);
        handler.build(template.data, template.vertexCount, extPrim,
            ImmediateExtendedAttribHandler.RAW_NORMAL_INDEX, memAddress0(ext), extStride);

        final int extVbo = GLStateManager.glGenBuffers();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
        ext.position(0).limit(template.vertexCount * extStride);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, ext, GL15.GL_STATIC_DRAW);
        memFree(ext);
        return extVbo;
    }

    void endFrame() {
        ring.postDraw();
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
        liveBuckets.clear();
        bucketPool.clear();
        staging = null;
        UnitCubeMesh.delete();
    }

    private static void delete(TemplateMesh mesh) {
        GLStateManager.glDeleteBuffers(mesh.vbo);
        if (mesh.extVbo != 0) GLStateManager.glDeleteBuffers(mesh.extVbo);
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
