package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.util.function.LongSupplier;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

final class RetainedTesrGroups implements AngelicaBufferSource.LayerDrawHook {

    static final int PROMOTE_AFTER_REBUILDS = 3;
    static final int DEMOTE_AFTER_STABLE_FRAMES = 4;
    static final long IDLE_RESET_FRAMES = 100;
    static final long GROUP_TTL_MS = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;

    private static final Tracy.ZoneId Z_TESR_REBUILD = Tracy.zoneId("tesrRebuild", Tracy.COLOR_CLIENT);

    private final AngelicaBufferSource source;
    private final LongSupplier clock;
    private long nowMs;
    private final Matrix4f baseMV = new Matrix4f();
    private final Matrix4f baseMVInv = new Matrix4f();
    private final Matrix4f scratchMat = new Matrix4f();
    private final Vector3f scratchVec = new Vector3f();
    private final Matrix4f drawMV = new Matrix4f();
    private final Matrix4f savedMV = new Matrix4f();
    private boolean mvSaved;
    private final float[] matScratch = new float[16];
    private double camX, camY, camZ;
    private long frameMark;

    long rebuilds;
    long retainedDraws;
    long streamPromotions;
    long streamedInstances;
    long instancedDraws;
    long instancedInstances;

    private InstancedTemplateRenderer instanced;
    private boolean instancedActive;
    private DeferredWorldRenderingPipeline deferred;
    private final MeshBuffer fallbackMesh = new MeshBuffer();
    private ByteBuffer fallbackScratch;

    static final class Group {
        final RenderLayer layer;
        final TesrMaterial material;
        final int blockEntityId;
        final boolean opaque;
        final boolean stream;
        Class<?> renderableClass;
        int rebuildsWindow;
        long anchorX, anchorY, anchorZ;
        boolean anchored;
        long frameMark = -1;
        long lastUsedMs;
        int entityColor;

        final ObjectArrayList<TemplateBuffer> instTemplates = new ObjectArrayList<>();
        final FloatArrayList instMatrices = new FloatArrayList();
        final IntArrayList instLights = new IntArrayList();
        final IntArrayList instColors = new IntArrayList();
        final ObjectArrayList<Matrix4f> instTexMatrices = new ObjectArrayList<>();
        private final ObjectArrayList<Matrix4f> texMatrixPool = new ObjectArrayList<>();
        private int texMatrixPoolUsed;
        long hashAcc;
        int count;

        long builtHash;
        int builtCount = -1;
        int builtVertexCount;
        boolean streaming;
        int consecutiveRebuilds;
        long frameHash;
        long prevFrameHash;
        int stableFrames;
        final MeshBuffer mesh = new MeshBuffer();
        ByteBuffer scratch;
        int meshBytes;

        void addTexMatrix(Matrix4f src) {
            if (src == null) {
                instTexMatrices.add(null);
                return;
            }
            if (texMatrixPoolUsed == texMatrixPool.size()) {
                texMatrixPool.add(new Matrix4f());
            }
            instTexMatrices.add(texMatrixPool.get(texMatrixPoolUsed++).set(src));
        }

        void clearInstances() {
            instTemplates.clear();
            instMatrices.clear();
            instLights.clear();
            instColors.clear();
            instTexMatrices.clear();
            texMatrixPoolUsed = 0;
        }

        Group(RenderLayer layer, TesrMaterial material, int blockEntityId) {
            this.layer = layer;
            this.material = material;
            this.blockEntityId = blockEntityId;
            this.opaque = material.transparency() == TesrMaterial.Transparency.OPAQUE;
            this.stream = material.isStream();
            this.streaming = stream;
        }
    }

    private final Reference2ObjectOpenHashMap<RenderLayer, Reference2ObjectOpenHashMap<TesrMaterial, Int2ObjectOpenHashMap<Group>>> groups = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<Group>> byLayer = new Reference2ObjectOpenHashMap<>();

    RetainedTesrGroups(AngelicaBufferSource source) {
        this(source, System::currentTimeMillis);
    }

    RetainedTesrGroups(AngelicaBufferSource source, LongSupplier clock) {
        this.source = source;
        this.clock = clock;
    }

    void beginPass(Matrix4f base, double camX, double camY, double camZ) {
        beginPass(base, camX, camY, camZ, null, null);
    }

    void beginPass(Matrix4f base, double camX, double camY, double camZ, InstancedTemplateRenderer instancedRenderer) {
        beginPass(base, camX, camY, camZ, instancedRenderer, null);
    }

    void beginPass(Matrix4f base, double camX, double camY, double camZ, InstancedTemplateRenderer instancedRenderer, DeferredWorldRenderingPipeline deferred) {
        baseMV.set(base);
        baseMVInv.set(base).invert();
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.instanced = instancedRenderer;
        this.instancedActive = instancedRenderer != null;
        this.deferred = instancedRenderer != null ? deferred : null;
        frameMark++;
        nowMs = clock.getAsLong();
    }

    private Group groupFor(RenderLayer layer, TesrMaterial material, int blockEntityId) {
        Reference2ObjectOpenHashMap<TesrMaterial, Int2ObjectOpenHashMap<Group>> byMaterial = groups.get(layer);
        if (byMaterial == null) {
            byMaterial = new Reference2ObjectOpenHashMap<>();
            groups.put(layer, byMaterial);
        }
        Int2ObjectOpenHashMap<Group> byId = byMaterial.get(material);
        if (byId == null) {
            byId = new Int2ObjectOpenHashMap<>();
            byMaterial.put(material, byId);
        }
        Group group = byId.get(blockEntityId);
        if (group == null) {
            group = new Group(layer, material, blockEntityId);
            group.lastUsedMs = nowMs;
            byId.put(blockEntityId, group);
            byLayer.computeIfAbsent(layer, l -> new ObjectArrayList<>()).add(group);
        }
        if (group.renderableClass == null) {
            group.renderableClass = TesrAttribution.currentRenderable;
        }
        return group;
    }

    void forceStreaming(RenderLayer layer, TesrMaterial material, int blockEntityId) {
        promote(groupFor(layer, material, blockEntityId));
    }

    void queue(TemplateBuffer template, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int blockEntityId, Matrix4f texMatrix, boolean forceStream) {
        final Group group = groupFor(layer, material, blockEntityId);
        if (group.frameMark != frameMark) {
            if (frameMark - group.frameMark > IDLE_RESET_FRAMES) {
                group.streaming = group.stream;
                group.consecutiveRebuilds = 0;
                group.stableFrames = 0;
                group.builtCount = -1;
            } else if (group.streaming && group.opaque && !group.stream) {
                if (group.frameHash == group.prevFrameHash) {
                    if (++group.stableFrames >= DEMOTE_AFTER_STABLE_FRAMES) {
                        group.streaming = false;
                        group.consecutiveRebuilds = 0;
                        group.stableFrames = 0;
                        group.builtCount = -1;
                    }
                } else {
                    group.stableFrames = 0;
                }
                group.prevFrameHash = group.frameHash;
                group.frameHash = 0;
            }
            group.frameMark = frameMark;
            group.lastUsedMs = nowMs;
            group.entityColor = SegmentedBufferBuilder.packEntityColor(CapturedRenderingState.INSTANCE.getCurrentEntityColor());
            group.count = 0;
            group.hashAcc = 0;
            group.clearInstances();
            if (!group.anchored || TesrAnchorMath.shouldReanchor(camX, camY, camZ, group.anchorX, group.anchorY, group.anchorZ)) {
                group.anchorX = TesrAnchorMath.anchorCoord(camX);
                group.anchorY = TesrAnchorMath.anchorCoord(camY);
                group.anchorZ = TesrAnchorMath.anchorCoord(camZ);
                group.anchored = true;
                group.builtCount = -1;
            }
            source.declareUse(layer);
        }
        if (group.stream) {
            final boolean instanceable = !forceStream && instancedActive && (deferred == null || material.shader() == null);
            if (instanceable && (texMatrix == null || template.drawMode != layer.getDrawMode())) {
                group.count++;
                group.instTemplates.add(template);
                currentMV.get(matScratch);
                group.instMatrices.addElements(group.instMatrices.size(), matScratch);
                group.instLights.add(packedLight);
                group.instColors.add(colorABGR);
                return;
            }
            if (template.drawMode == layer.getDrawMode()) {
                source.getBuffer(layer, blockEntityId).addTemplateInstance(template, currentMV, scratchVec, colorABGR, packedLight, texMatrix);
                streamedInstances++;
                return;
            }
        }
        scratchMat.set(baseMVInv).mul(currentMV);
        TesrAnchorMath.toAnchorRelative(scratchMat, camX, camY, camZ, group.anchorX, group.anchorY, group.anchorZ);
        long hash = TesrAnchorMath.instanceHash(System.identityHashCode(template), scratchMat, packedLight, colorABGR);
        if (texMatrix != null) {
            hash += TesrAnchorMath.texMatrixHash(texMatrix);
        }
        if (group.streaming && template.drawMode == layer.getDrawMode()) {
            group.frameHash += hash;
            source.getBuffer(layer, blockEntityId).addTemplateInstance(template, currentMV, scratchVec, colorABGR, packedLight, texMatrix);
            streamedInstances++;
            return;
        }
        group.hashAcc += hash;
        group.count++;
        group.instTemplates.add(template);
        scratchMat.get(matScratch);
        group.instMatrices.addElements(group.instMatrices.size(), matScratch);
        group.instLights.add(packedLight);
        group.instColors.add(colorABGR);
        group.addTexMatrix(texMatrix);
    }

    @Override
    public boolean hasDraws(RenderLayer layer) {
        final ObjectArrayList<Group> list = byLayer.get(layer);
        if (list == null) return false;
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (group.frameMark == frameMark && group.count > 0) return true;
        }
        return false;
    }

    @Override
    public void drawLayer(RenderLayer layer) {
        final ObjectArrayList<Group> list = byLayer.get(layer);
        if (list == null) return;
        boolean variantResolved = false;
        boolean variantAvailable = false;
        boolean variantBound = false;
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (group.frameMark != frameMark || group.count == 0) continue;
            if (group.stream && instancedActive && (deferred == null || group.material.shader() == null)) {
                restoreMV();
                if (deferred == null) {
                    instancedDraws += instanced.drawGroup(group.instTemplates, group.instMatrices, group.instLights, group.instColors, group.count, nowMs);
                    instancedInstances += group.count;
                    continue;
                }
                if (!variantResolved) {
                    variantResolved = true;
                    variantAvailable = deferred.hasTesrInstancedVariant();
                }
                if (variantAvailable) {
                    applyGroupRenderState(group);
                    if (!variantBound) {
                        variantBound = true;
                        deferred.rebindCurrentPass();
                        deferred.bindTesrInstancedVariant();
                    }
                    instancedDraws += instanced.drawGroup(group.instTemplates, group.instMatrices, group.instLights, group.instColors, group.count, nowMs);
                    instancedInstances += group.count;
                } else {
                    drawGroupCpuFallback(group);
                }
                continue;
            }
            variantBound = false;
            if (group.builtCount != group.count || group.builtHash != group.hashAcc || !group.mesh.isUploaded()) {
                if (group.mesh.isUploaded()) {
                    group.consecutiveRebuilds++;
                }
                rebuild(group);
            } else {
                group.consecutiveRebuilds = 0;
            }
            draw(group);
            if (group.consecutiveRebuilds >= PROMOTE_AFTER_REBUILDS && !group.streaming && group.opaque) {
                promote(group);
            }
        }
        restoreMV();
        if (variantBound) {
            deferred.rebindCurrentPass();
        }
    }

    private void applyGroupRenderState(Group group) {
        AngelicaBufferSource.setEntityColor(group.entityColor);
        source.applyIdNoRebind(group.blockEntityId);
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    private void drawGroupCpuFallback(Group group) {
        AngelicaBufferSource.setEntityColor(group.entityColor);
        source.applyIdAndRebind(group.blockEntityId);
        saveMV();
        GLStateManager.setModelViewMatrix(IDENTITY);
        final VertexFormat format = group.layer.getVertexFormat();
        int start = 0;
        while (start < group.count) {
            final int drawMode = group.instTemplates.get(start).drawMode;
            int end = start;
            int totalVerts = 0;
            while (end < group.count && group.instTemplates.get(end).drawMode == drawMode) {
                totalVerts += group.instTemplates.get(end).vertexCount;
                end++;
            }
            fallbackScratch = MeshBuffer.ensureCapacity(fallbackScratch, format.getVertexSize() * totalVerts, false);
            fallbackScratch.clear();
            final long base = memAddress0(fallbackScratch);
            long ptr = base;
            for (int i = start; i < end; i++) {
                group.instMatrices.getElements(i * 16, matScratch, 0, 16);
                scratchMat.set(matScratch);
                ptr = VertexTransform.writeInstance(ptr, format, group.instTemplates.get(i), scratchMat, scratchVec, group.instColors.getInt(i), group.instLights.getInt(i), null);
            }
            fallbackScratch.position((int) (ptr - base));
            fallbackScratch.flip();
            fallbackMesh.upload(format, drawMode, fallbackScratch, totalVerts);
            fallbackMesh.render();
            streamedInstances += end - start;
            start = end;
        }
    }

    private void promote(Group group) {
        group.streaming = true;
        streamPromotions++;
        group.count = 0;
        group.hashAcc = 0;
        group.frameHash = 0;
        group.prevFrameHash = 0;
        group.stableFrames = 0;
        group.builtCount = -1;
        group.clearInstances();
        group.mesh.delete();
        group.meshBytes = 0;
    }

    void sweep(long now) {
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                final Group group = list.get(i);
                if (now - group.lastUsedMs <= GROUP_TTL_MS) continue;
                group.mesh.delete();
                list.remove(i);
                final Reference2ObjectOpenHashMap<TesrMaterial, Int2ObjectOpenHashMap<Group>> byMaterial = groups.get(group.layer);
                if (byMaterial != null) {
                    final Int2ObjectOpenHashMap<Group> byId = byMaterial.get(group.material);
                    if (byId != null) {
                        byId.remove(group.blockEntityId);
                    }
                }
            }
        }
    }

    void clear() {
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                list.get(i).mesh.delete();
            }
        }
        byLayer.clear();
        groups.clear();
        fallbackMesh.delete();
        fallbackScratch = null;
    }

    int groupCount() {
        int count = 0;
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            count += list.size();
        }
        return count;
    }

    int streamingGroupCount() {
        int count = 0;
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                if (list.get(i).streaming) count++;
            }
        }
        return count;
    }

    long retainedBytes() {
        long bytes = 0;
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                final Group group = list.get(i);
                bytes += group.meshBytes;
                if (group.scratch != null) bytes += group.scratch.capacity();
            }
        }
        return bytes;
    }

    private void rebuild(Group group) {
        rebuilds++;
        group.rebuildsWindow++;
        if (Tracy.ENABLED) {
            Tracy.beginZone(Z_TESR_REBUILD);
            Tracy.zoneText(attribution(group));
        }
        try {
            rebuildInner(group);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    void collectRebuilders(ObjectArrayList<Group> out) {
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                final Group group = list.get(i);
                if (group.rebuildsWindow > 0) out.add(group);
            }
        }
    }

    static String attribution(Group group) {
        final StringBuilder sb = new StringBuilder();
        if (group.renderableClass != null) {
            sb.append(group.renderableClass.getSimpleName());
        } else {
            sb.append("id").append(group.blockEntityId);
        }
        final ResourceLocation tex = group.layer.getTextureId();
        if (tex != null) {
            final String path = tex.getResourcePath();
            sb.append(' ').append(path, path.lastIndexOf('/') + 1, path.length());
        }
        sb.append('/').append(group.material.transparency());
        return sb.toString();
    }

    private void rebuildInner(Group group) {
        final VertexFormat format = group.layer.getVertexFormat();
        int totalVerts = 0;
        for (int i = 0, n = group.instTemplates.size(); i < n; i++) {
            totalVerts += group.instTemplates.get(i).vertexCount;
        }
        final int bytes = format.getVertexSize() * totalVerts;
        group.scratch = MeshBuffer.ensureCapacity(group.scratch, bytes, false);
        group.scratch.clear();
        final long base = memAddress0(group.scratch);
        long ptr = base;
        for (int i = 0, n = group.count; i < n; i++) {
            group.instMatrices.getElements(i * 16, matScratch, 0, 16);
            scratchMat.set(matScratch);
            final int light = group.instLights.getInt(i);
            final int color = group.instColors.getInt(i);
            final TemplateBuffer template = group.instTemplates.get(i);
            ptr = VertexTransform.writeInstance(ptr, format, template, scratchMat, scratchVec, color, light, group.instTexMatrices.get(i));
        }
        group.scratch.position((int) (ptr - base));
        group.scratch.flip();
        group.builtVertexCount = totalVerts;
        group.mesh.upload(format, group.layer.getDrawMode(), group.scratch, group.builtVertexCount, true);
        group.meshBytes = bytes;
        group.builtHash = group.hashAcc;
        group.builtCount = group.count;
    }

    private void draw(Group group) {
        retainedDraws++;
        AngelicaBufferSource.setEntityColor(group.entityColor);
        source.applyIdAndRebind(group.blockEntityId);
        drawMV.set(baseMV).translate((float) (group.anchorX - camX), (float) (group.anchorY - camY), (float) (group.anchorZ - camZ));
        saveMV();
        GLStateManager.setModelViewMatrix(drawMV);
        group.mesh.render();
    }

    private void saveMV() {
        if (!mvSaved) {
            mvSaved = true;
            savedMV.set(GLStateManager.getModelViewMatrix());
        }
    }

    private void restoreMV() {
        if (mvSaved) {
            mvSaved = false;
            GLStateManager.setModelViewMatrix(savedMV);
        }
    }
}
