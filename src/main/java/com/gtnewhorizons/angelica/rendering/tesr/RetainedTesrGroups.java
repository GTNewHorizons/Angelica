package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.CombinedGlint;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.LongSupplier;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;

final class RetainedTesrGroups implements AngelicaBufferSource.LayerDrawHook {

    static final int PROMOTE_AFTER_REBUILDS = 3;
    static final int DEMOTE_AFTER_STABLE_FRAMES = 4;
    static final long IDLE_RESET_FRAMES = 100;
    static final long GROUP_TTL_MS = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;
    static final int MERGE_SCAN = 4;

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
    private final Matrix4f savedTexMatrix = new Matrix4f();
    private boolean texApplied;
    private final float[] matScratch = new float[16];
    private double camX, camY, camZ;
    private long frameMark;
    GlintCapture glintCapture;

    long rebuilds;
    long retainedDraws;
    long streamPromotions;
    long streamedInstances;
    long instancedDraws;
    long instancedInstances;
    long cubeInstances;
    long redrawnInstances;
    long texMatrixRuns;

    private InstancedTemplateRenderer instanced;
    private boolean instancedActive;
    private TesrInstancingPipeline deferred;
    private final MeshBuffer fallbackMesh = new MeshBuffer();
    private ByteBuffer fallbackScratch;

    static final class TexRun {
        final Matrix4f matrix = new Matrix4f();
        boolean identity;
        int start, end;
        int parts, instances;
        TexRun next, last;
        TexRun reusedFrom;
        InstanceColumns source;
        int color;
        Matrix4fc matrixSource;
        boolean glintBase;
        TexRun sourceRun;
        long uploadOffset;
        long uploadPass = -1;
        int uploadRingEpoch, uploadBuffer;
        final ObjectArrayList<Object> uploadMeshes = new ObjectArrayList<>();
        final IntArrayList uploadCounts = new IntArrayList();
    }

    static final class InstanceColumns {
        final ObjectArrayList<TemplateBuffer> templates = new ObjectArrayList<>();
        final ObjectArrayList<CubeParams[]> cubes = new ObjectArrayList<>();
        final FloatArrayList scales = new FloatArrayList();
        final IntArrayList lights = new IntArrayList();
        final IntArrayList colors = new IntArrayList();
        final IntArrayList overlays = new IntArrayList();
        final LongArrayList infos = new LongArrayList();
        final ObjectArrayList<TexRun> runs = new ObjectArrayList<>();
        float[] matrices = new float[16 * 64];
        int size;
        int referencedParts;

        boolean hasInstances() {
            return size > 0 || referencedParts > 0;
        }

        void add(TemplateBuffer templateOrNull, Matrix4fc mv, int light, int color, int overlay, long info) {
            final int base = size * 16;
            if (base + 16 > matrices.length) {
                matrices = Arrays.copyOf(matrices, matrices.length << 1);
            }
            mv.get(matrices, base);
            if (templateOrNull != null) templates.add(templateOrNull);
            lights.add(light);
            colors.add(color);
            overlays.add(overlay);
            infos.add(info);
            size++;
        }

        void addCubes(TemplateBuffer templateOrNull, CubeParams[] cubeParams, float scale, Matrix4fc mv, int light, int color, int overlay, long info) {
            cubes.add(cubeParams);
            scales.add(scale);
            add(templateOrNull, mv, light, color, overlay, info);
        }

        void appendRange(InstanceColumns src, int start, int end, int light, int color, int overlay) {
            final int n = end - start;
            final int need = (size + n) * 16;
            if (need > matrices.length) {
                matrices = Arrays.copyOf(matrices, Math.max(need, matrices.length << 1));
            }
            System.arraycopy(src.matrices, start * 16, matrices, size * 16, n * 16);
            if (src.templates.size() == src.size) {
                for (int i = start; i < end; i++) templates.add(src.templates.get(i));
            }
            if (src.cubes.size() == src.size) {
                for (int i = start; i < end; i++) cubes.add(src.cubes.get(i));
                scales.addElements(scales.size(), src.scales.elements(), start, n);
            }
            fill(lights, n, light);
            fill(colors, n, color);
            fill(overlays, n, overlay);
            infos.size(infos.size() + n);
            size += n;
        }

        private static void fill(IntArrayList list, int n, int value) {
            final int from = list.size();
            list.size(from + n);
            Arrays.fill(list.elements(), from, from + n, value);
        }

        void clear() {
            templates.clear();
            cubes.clear();
            scales.clear();
            lights.clear();
            colors.clear();
            overlays.clear();
            infos.clear();
            runs.clear();
            size = 0;
            referencedParts = 0;
        }
    }

    static final class Group {
        final RenderLayer layer;
        final TesrMaterial material;
        final int blockEntityId;
        final boolean opaque;
        final boolean stream;
        final boolean mergeRuns;
        Class<?> renderableClass;
        int rebuildsWindow;
        long anchorX, anchorY, anchorZ;
        boolean anchored;
        long frameMark = -1;
        long lastUsedMs;
        int entityColor;

        final InstanceColumns templateColumns = new InstanceColumns();
        final InstanceColumns cubeColumns = new InstanceColumns();
        final ObjectArrayList<Matrix4f> instTexMatrices = new ObjectArrayList<>();
        private final ObjectArrayList<Matrix4f> texMatrixPool = new ObjectArrayList<>();
        private int texMatrixPoolUsed;
        private final ObjectArrayList<TexRun> runPool = new ObjectArrayList<>();
        private int runPoolUsed;
        long hashAcc;

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

        TexRun openOrExtendRun(InstanceColumns cols, Matrix4f texMatrix) {
            return openOrExtendRun(cols, texMatrix, false, false);
        }

        TexRun openOrExtendRun(InstanceColumns cols, Matrix4f texMatrix, boolean stableMatrix, boolean glintBase) {
            final ObjectArrayList<TexRun> list = cols.runs;
            final int part = cols.size;
            final boolean identity = texMatrix == null;
            final int size = list.size();
            int floor = Math.max(0, mergeRuns ? size - MERGE_SCAN : size - 1);
            for (int i = size - 1; i >= floor; i--) {
                final TexRun head = list.get(i);
                if (head.reusedFrom != null || head.source != null) continue;
                if (head.glintBase != glintBase) {
                    if (opaque) floor = Math.max(0, floor - 1);
                    continue;
                }
                if (head.identity != identity || (!identity && head.matrixSource != texMatrix && !head.matrix.equals(texMatrix))) continue;
                if (head.last.end != part) {
                    final TexRun segment = pooledRun(part);
                    head.last.next = segment;
                    head.last = segment;
                }
                return head;
            }
            final TexRun head = pooledRun(part);
            head.identity = identity;
            if (!identity) head.matrix.set(texMatrix);
            if (stableMatrix) head.matrixSource = texMatrix;
            head.glintBase = glintBase;
            list.add(head);
            return head;
        }

        TexRun referenceRun(InstanceColumns cols, InstanceColumns src, TexRun sourceRun, int color, Matrix4f texMatrix, boolean stableMatrix, int start) {
            final ObjectArrayList<TexRun> list = cols.runs;
            for (int i = list.size() - 1; i >= 0; i--) {
                final TexRun head = list.get(i);
                if (head.source != src || head.reusedFrom != null || head.color != color || head.matrixSource != texMatrix && !head.matrix.equals(texMatrix)) continue;
                if (head.last.end != start) {
                    final TexRun segment = pooledRun(start);
                    head.last.next = segment;
                    head.last = segment;
                }
                if (head.sourceRun != sourceRun) head.sourceRun = null;
                return head;
            }
            final TexRun head = pooledRun(start);
            head.identity = false;
            head.matrix.set(texMatrix);
            head.source = src;
            head.sourceRun = sourceRun;
            head.color = color;
            if (stableMatrix) head.matrixSource = texMatrix;
            list.add(head);
            return head;
        }

        void replayRange(InstanceColumns cols, TexRun original, int start, int end, int instances, Matrix4f matrix) {
            replayRange(cols, original, start, end, instances, matrix, false);
        }

        void replayRange(InstanceColumns cols, TexRun original, int start, int end, int instances, Matrix4f matrix, boolean stableMatrix) {
            TexRun head = null;
            for (int i = cols.runs.size() - 1; i >= 0; i--) {
                final TexRun candidate = cols.runs.get(i);
                if (candidate.reusedFrom == original && (candidate.matrixSource == matrix || candidate.matrix.equals(matrix))) {
                    head = candidate;
                    break;
                }
            }
            if (head == null) {
                head = pooledRun(start);
                head.matrix.set(matrix);
                head.identity = false;
                head.reusedFrom = original;
                head.source = original.source;
                head.color = original.color;
                if (stableMatrix) head.matrixSource = matrix;
                cols.runs.add(head);
            } else if (head.last.end != start) {
                final TexRun segment = pooledRun(start);
                head.last.next = segment;
                head.last = segment;
            }
            head.sourceRun = original.sourceRun;
            head.last.end = end;
            head.parts += end - start;
            head.instances += instances;
        }

        private TexRun pooledRun(int part) {
            if (runPoolUsed == runPool.size()) {
                runPool.add(new TexRun());
            }
            final TexRun run = runPool.get(runPoolUsed++);
            run.start = part;
            run.end = part;
            run.parts = 0;
            run.instances = 0;
            run.next = null;
            run.reusedFrom = null;
            run.source = null;
            run.matrixSource = null;
            run.glintBase = false;
            run.sourceRun = null;
            run.uploadPass = -1;
            run.last = run;
            return run;
        }

        void clearInstances() {
            templateColumns.clear();
            cubeColumns.clear();
            instTexMatrices.clear();
            texMatrixPoolUsed = 0;
            runPoolUsed = 0;
        }

        Group(RenderLayer layer, TesrMaterial material, int blockEntityId) {
            this.layer = layer;
            this.material = material;
            this.blockEntityId = blockEntityId;
            this.opaque = material.transparency() == TesrMaterial.Transparency.OPAQUE;
            this.stream = material.isStream();
            this.mergeRuns = material.isDepthEqual() && material.isNoDepthWrite();
            this.streaming = stream;
        }
    }

    private final Reference2ObjectOpenHashMap<RenderLayer, Reference2ObjectOpenHashMap<TesrMaterial, Int2ObjectOpenHashMap<Group>>> groups = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<Group>> byLayer = new Reference2ObjectOpenHashMap<>();
    private static final int RECENT_GROUPS = 4;
    private final Group[] recentGroups = new Group[RECENT_GROUPS];
    private int recentGroupNext;

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

    void beginPass(Matrix4f base, double camX, double camY, double camZ, InstancedTemplateRenderer instancedRenderer, TesrInstancingPipeline deferred) {
        if (glintCapture != null) glintCapture.invalidate();
        glintCapture = null;
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

    boolean captureStillValid(Group group) {
        return group.frameMark == frameMark;
    }

    long captureVersion() { return frameMark; }

    private Group groupFor(RenderLayer layer, TesrMaterial material, int id) {
        final int key = instanceableMaterial(material) ? 0 : id;
        Group group = null;
        for (int i = 0; i < RECENT_GROUPS; i++) {
            final Group recent = recentGroups[i];
            if (recent != null && recent.layer == layer && recent.material == material && recent.blockEntityId == key) {
                group = recent;
                break;
            }
        }
        if (group == null) {
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
            group = byId.get(key);
            if (group == null) {
                group = new Group(layer, material, key);
                group.lastUsedMs = nowMs;
                byId.put(key, group);
                byLayer.computeIfAbsent(layer, l -> new ObjectArrayList<>()).add(group);
            }
            recentGroups[recentGroupNext] = group;
            recentGroupNext = (recentGroupNext + 1) % RECENT_GROUPS;
        }
        if (group.renderableClass == null) {
            group.renderableClass = TesrAttribution.currentRenderable;
        }
        return group;
    }

    void forceStreaming(RenderLayer layer, TesrMaterial material, int blockEntityId) {
        promote(groupFor(layer, material, blockEntityId));
    }

    void queue(TemplateBuffer template, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, long entityInfo, int id, Matrix4f texMatrix) {
        queue(template, null, 1.0f, layer, material, currentMV, packedLight, colorABGR, overlayABGR, entityInfo, id, texMatrix);
    }

    void queue(TemplateBuffer template, CubeParams[] cubes, float scale, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, long entityInfo, int id, Matrix4f texMatrix) {
        final Group group = groupFor(layer, material, id);
        touch(group, layer);
        if (group.stream) {
            final boolean instanceable = instanceable(group);
            final boolean capturingBase = glintCapture != null && glintCapture.capturesBase();
            if (instanceable && cubes != null) {
                final TexRun run = group.openOrExtendRun(group.cubeColumns, texMatrix, false, capturingBase && group.opaque);
                group.cubeColumns.addCubes(deferred != null ? template : null, cubes, scale, currentMV, packedLight, colorABGR, overlayABGR, entityInfo);
                run.last.end = group.cubeColumns.size;
                run.parts++;
                run.instances += cubes.length;
                if (glintCapture != null) glintCapture.record(group, group.cubeColumns, run, group.cubeColumns.size - 1, group.cubeColumns.size, cubes.length);
                return;
            }
            if (instanceable) {
                final TexRun run = group.openOrExtendRun(group.templateColumns, texMatrix, false, capturingBase && group.opaque);
                group.templateColumns.add(template, currentMV, packedLight, colorABGR, overlayABGR, entityInfo);
                run.last.end = group.templateColumns.size;
                run.parts++;
                run.instances++;
                if (glintCapture != null) glintCapture.record(group, group.templateColumns, run, group.templateColumns.size - 1, group.templateColumns.size, 1);
                return;
            }
            if (glintCapture != null) glintCapture.invalidate();
            if (template.drawMode == layer.getDrawMode()) {
                source.getBuffer(layer, id).addTemplateInstance(template, currentMV, scratchVec, colorABGR, packedLight, texMatrix);
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
            source.getBuffer(layer, id).addTemplateInstance(template, currentMV, scratchVec, colorABGR, packedLight, texMatrix);
            streamedInstances++;
            return;
        }
        group.hashAcc += hash;
        group.templateColumns.add(template, scratchMat, packedLight, colorABGR, overlayABGR, entityInfo);
        group.addTexMatrix(texMatrix);
    }

    boolean canCopyInstances(TesrMaterial material) {
        return material.isStream() && instanceableMaterial(material);
    }

    void copyInstances(InstanceColumns src, int start, int end, RenderLayer layer, TesrMaterial material, int packedLight, int colorABGR, int overlayABGR, Matrix4f texMatrix) {
        final Group group = groupFor(layer, material, 0);
        touch(group, layer);
        final boolean cubes = !src.cubes.isEmpty();
        final InstanceColumns dst = cubes ? group.cubeColumns : group.templateColumns;
        final TexRun run = group.openOrExtendRun(dst, texMatrix);
        final int first = dst.size;
        dst.appendRange(src, start, end, packedLight, colorABGR, overlayABGR);
        int instances = end - start;
        if (cubes) {
            instances = 0;
            for (int i = start; i < end; i++) instances += src.cubes.get(i).length;
        }
        run.last.end = dst.size;
        run.parts += end - start;
        run.instances += instances;
        if (glintCapture != null) glintCapture.record(group, dst, run, first, dst.size, instances);
    }

    void referenceInstances(InstanceColumns src, int start, int end, RenderLayer layer, TesrMaterial material, int colorABGR, Matrix4f texMatrix) {
        final Group group = groupFor(layer, material, 0);
        touch(group, layer);
        final boolean cubes = !src.cubes.isEmpty();
        final InstanceColumns dst = cubes ? group.cubeColumns : group.templateColumns;
        final int instances = instanceCount(src, start, end, cubes);
        final TexRun run = group.referenceRun(dst, src, null, colorABGR, texMatrix, false, start);
        run.last.end = end;
        run.parts += end - start;
        run.instances += instances;
        dst.referencedParts += end - start;
        if (glintCapture != null) glintCapture.record(group, dst, run, start, end, instances);
    }

    void referenceGlintLayers(InstanceColumns src, TexRun sourceRun, int start, int end, RenderLayer layer, TesrMaterial material, int colorABGR, Matrix4f first, Matrix4f second) {
        final Group group = groupFor(layer, material, 0);
        touch(group, layer);
        final boolean cubes = !src.cubes.isEmpty();
        final InstanceColumns dst = cubes ? group.cubeColumns : group.templateColumns;
        final int instances = instanceCount(src, start, end, cubes);
        final TexRun run = group.referenceRun(dst, src, sourceRun, colorABGR, first, true, start);
        run.last.end = end;
        run.parts += end - start;
        run.instances += instances;
        group.replayRange(dst, run, start, end, instances, second, true);
        dst.referencedParts += end - start;
    }

    void queueGlintLayers(TemplateBuffer template, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, Matrix4f first, Matrix4f second) {
        final Group group = groupFor(layer, material, 0);
        touch(group, layer);
        final InstanceColumns dst = group.templateColumns;
        final int index = dst.size;
        final TexRun run = group.openOrExtendRun(dst, first, true, false);
        dst.add(template, currentMV, packedLight, colorABGR, overlayABGR, 0L);
        run.last.end = dst.size;
        run.parts++;
        run.instances++;
        group.replayRange(dst, run, index, index + 1, 1, second, true);
    }

    private static int instanceCount(InstanceColumns src, int start, int end, boolean cubes) {
        if (!cubes) return end - start;
        int instances = 0;
        for (int i = start; i < end; i++) instances += src.cubes.get(i).length;
        return instances;
    }

    private void touch(Group group, RenderLayer layer) {
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
            group.entityColor = AngelicaBufferSource.packEntityColor(CapturedRenderingState.INSTANCE.getCurrentEntityColor());
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
    }

    @Override
    public boolean hasDraws(RenderLayer layer) {
        final ObjectArrayList<Group> list = byLayer.get(layer);
        if (list == null) return false;
        for (int i = 0, n = list.size(); i < n; i++) {
            if (live(list.get(i))) return true;
        }
        return false;
    }

    @Override
    public void drawLayer(RenderLayer layer) {
        if (glintCapture != null) glintCapture.invalidate();
        final ObjectArrayList<Group> list = byLayer.get(layer);
        if (list == null) return;
        boolean matrixAvailable = true;
        boolean cubeAvailable = true;
        if (deferred != null && anyInstanceable(list)) {
            deferred.rebindCurrentPass();
            matrixAvailable = deferred.hasInstancedVariant(Instancing.TEMPLATE);
            cubeAvailable = deferred.hasInstancedVariant(Instancing.CUBE);
        }
        final int need = drawUninstanced(list, matrixAvailable, cubeAvailable);
        restoreMV();
        if ((need & NEED_MATRIX) != 0) {
            if (deferred != null) deferred.bindInstancedVariant(Instancing.TEMPLATE);
            drawMatrixVariant(list, cubeAvailable);
        }
        if ((need & NEED_CUBE) != 0) {
            if (deferred != null) deferred.bindInstancedVariant(Instancing.CUBE);
            drawCubeVariant(list);
        }
        if (need != 0 && deferred != null) {
            deferred.rebindCurrentPass();
        }
    }

    private static final int NEED_MATRIX = 1;
    private static final int NEED_CUBE = 2;

    private boolean live(Group g) {
        return g.frameMark == frameMark && (g.templateColumns.hasInstances() || g.cubeColumns.hasInstances());
    }

    private boolean anyInstanceable(ObjectArrayList<Group> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (live(group) && instanceable(group)) return true;
        }
        return false;
    }

    private int drawUninstanced(ObjectArrayList<Group> list, boolean matrixAvailable, boolean cubeAvailable) {
        int need = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group)) continue;
            if (!instanceable(group)) {
                drawRetained(group);
                continue;
            }
            if (group.templateColumns.hasInstances()) {
                if (matrixAvailable) {
                    need |= NEED_MATRIX;
                } else {
                    drawInstancesCpu(group, group.templateColumns);
                }
            }
            if (group.cubeColumns.hasInstances()) {
                if (cubeAvailable) {
                    need |= NEED_CUBE;
                } else if (matrixAvailable) {
                    need |= NEED_MATRIX;
                } else {
                    drawInstancesCpu(group, group.cubeColumns);
                }
            }
        }
        return need;
    }

    private void drawMatrixVariant(ObjectArrayList<Group> list, boolean cubeAvailable) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group) || !instanceable(group)) continue;
            if (group.templateColumns.hasInstances()) {
                final boolean fixedFunction = deferred == null && GLStateManager.getActiveProgram() == 0;
                drawTemplateRuns(group.templateColumns, group.material.special() == TesrMaterial.SpecialRender.GLINT && fixedFunction, group.material == EntityMaterials.ITEM_GLINT && fixedFunction);
            }
            if (!cubeAvailable && group.cubeColumns.hasInstances()) {
                drawTemplateRuns(group.cubeColumns, group.material == EntityMaterials.GLINT && deferred == null, false);
            }
        }
        restoreTexMatrix();
    }

    private void drawCubeVariant(ObjectArrayList<Group> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group) || !instanceable(group) || !group.cubeColumns.hasInstances()) continue;
            drawCubeInstances(group);
        }
        restoreTexMatrix();
    }

    private boolean instanceable(Group group) {
        return instanceableMaterial(group.material);
    }

    private boolean instanceableMaterial(TesrMaterial material) {
        return material.isStream() && instancedActive && (deferred == null || material.shader() == null);
    }

    private void drawRetained(Group group) {
        if (group.builtCount != group.templateColumns.size || group.builtHash != group.hashAcc || !group.mesh.isUploaded()) {
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

    private void drawTemplateRuns(InstanceColumns cols, boolean combineGlint, boolean itemStencil) {
        for (int r = 0, n = cols.runs.size(); r < n; r++) {
            final TexRun run = cols.runs.get(r);
            applyTexMatrix(run);
            final boolean combined = combineGlint && r + 1 < n && combineRuns(cols, run, cols.runs.get(r + 1));
            if (itemStencil) {
                GLStateManager.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
                GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
                GLStateManager.glStencilMask(1);
                GLStateManager.glClearStencil(0);
                GLStateManager.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                GLStateManager.glStencilFunc(GL11.GL_EQUAL, 0, 1);
                GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
            }
            try {
                if (canRedraw(run)) {
                    instancedDraws += instanced.redrawTemplates(run.sourceRun, run.color);
                    redrawnInstances += run.parts;
                } else {
                    instancedDraws += instanced.drawTemplates(cols, run, nowMs, run.glintBase);
                    if (run.glintBase) recordUpload(run);
                }
                instancedInstances += run.parts;
            } finally {
                if (itemStencil) GLStateManager.glPopAttrib();
                if (combined) CombinedGlint.end();
            }
            if (combined) r++;
        }
    }

    private void drawCubeInstances(Group group) {
        final InstanceColumns cols = group.cubeColumns;
        for (int r = 0, n = cols.runs.size(); r < n; r++) {
            final TexRun run = cols.runs.get(r);
            applyTexMatrix(run);
            final boolean combined = group.material == EntityMaterials.GLINT && deferred == null && r + 1 < n && combineRuns(cols, run, cols.runs.get(r + 1));
            try {
                if (canRedraw(run)) {
                    instanced.redrawCubes(run.sourceRun.uploadOffset, run.instances, run.color);
                    redrawnInstances += run.instances;
                } else {
                    run.uploadOffset = instanced.drawCubes(cols, run);
                    if (run.glintBase) recordUpload(run);
                }
                instancedDraws++;
                cubeInstances += run.instances;
            } finally {
                if (combined) CombinedGlint.end();
            }
            if (combined) r++;
        }
    }

    private boolean canRedraw(TexRun run) {
        final TexRun base = run.sourceRun;
        return base != null && base.uploadPass == frameMark && base.parts == run.parts && base.instances == run.instances && instanced.canRedraw(base.uploadRingEpoch, base.uploadBuffer);
    }

    private void recordUpload(TexRun run) {
        run.uploadPass = frameMark;
        run.uploadRingEpoch = instanced.ringEpoch();
        run.uploadBuffer = instanced.ringBuffer();
    }

    private static boolean combineRuns(InstanceColumns cols, TexRun first, TexRun second) {
        return matchingGlintGeometry(cols, first, second) && CombinedGlint.begin(second.identity ? IDENTITY : second.matrix);
    }

    static boolean matchingGlintGeometry(InstanceColumns cols, TexRun first, TexRun second) {
        return second.reusedFrom == first && first.parts == second.parts && first.instances == second.instances;
    }

    private void applyTexMatrix(TexRun run) {
        if (run.identity && !texApplied) return;
        if (!texApplied) {
            texApplied = true;
            savedTexMatrix.set(GLStateManager.getTextures().getTextureUnitMatrix(0));
        }
        GLStateManager.setTextureMatrix(0, run.identity ? IDENTITY : run.matrix);
        if (!run.identity) texMatrixRuns++;
    }

    private void restoreTexMatrix() {
        if (!texApplied) return;
        texApplied = false;
        GLStateManager.setTextureMatrix(0, savedTexMatrix);
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    static int entityFromInfo(long info) {
        return (short) info;
    }

    static int blockEntityFromInfo(long info) {
        return (short) (info >>> 16);
    }

    private int idFromInfo(long info) {
        return source.effectiveIdKind() == AngelicaBufferSource.GroupIdKind.ENTITY ? entityFromInfo(info) : blockEntityFromInfo(info);
    }

    private void drawInstancesCpu(Group group, InstanceColumns cols) {
        saveMV();
        GLStateManager.setModelViewMatrix(IDENTITY);
        final VertexFormat format = group.layer.getVertexFormat();
        for (int r = 0, rn = cols.runs.size(); r < rn; r++) {
            final TexRun head = cols.runs.get(r);
            final Matrix4f texMatrix = head.identity ? null : head.matrix;
            final InstanceColumns data = head.source != null ? head.source : cols;
            final ObjectArrayList<TemplateBuffer> templates = data.templates;
            for (TexRun seg = head; seg != null; seg = seg.next) {
                final int runEnd = seg.end;
                int sub = seg.start;
                while (sub < runEnd) {
                    final long info = head.source != null ? 0L : data.infos.getLong(sub);
                    final int overlay = data.overlays.getInt(sub);
                    int subEnd = sub;
                    while (subEnd < runEnd && (head.source != null || data.infos.getLong(subEnd) == info) && data.overlays.getInt(subEnd) == overlay) {
                        subEnd++;
                    }
                    AngelicaBufferSource.setEntityColor(overlay);
                    source.applyIdAndRebind(idFromInfo(info));
                    int start = sub;
                    while (start < subEnd) {
                        final int drawMode = templates.get(start).drawMode;
                        int end = start;
                        int totalVerts = 0;
                        while (end < subEnd && templates.get(end).drawMode == drawMode) {
                            totalVerts += templates.get(end).vertexCount;
                            end++;
                        }
                        fallbackScratch = MeshBuffer.ensureCapacity(fallbackScratch, format.getVertexSize() * totalVerts, false);
                        fallbackScratch.clear();
                        final long base = memAddress0(fallbackScratch);
                        long ptr = base;
                        for (int i = start; i < end; i++) {
                            System.arraycopy(data.matrices, i * 16, matScratch, 0, 16);
                            scratchMat.set(matScratch);
                            final int color = head.source != null ? head.color : data.colors.getInt(i);
                            ptr = VertexTransform.writeInstance(ptr, format, templates.get(i), scratchMat, scratchVec, color, data.lights.getInt(i), texMatrix);
                        }
                        fallbackScratch.position((int) (ptr - base));
                        fallbackScratch.flip();
                        fallbackMesh.upload(format, drawMode, fallbackScratch, totalVerts);
                        fallbackMesh.render();
                        streamedInstances += end - start;
                        start = end;
                    }
                    sub = subEnd;
                }
            }
        }
    }

    private void promote(Group group) {
        group.streaming = true;
        streamPromotions++;
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
        Arrays.fill(recentGroups, null);
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
        Arrays.fill(recentGroups, null);
        fallbackMesh.delete();
        fallbackScratch = null;
        texApplied = false;
        instanced = null;
        instancedActive = false;
        deferred = null;
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
        final InstanceColumns cols = group.templateColumns;
        int totalVerts = 0;
        for (int i = 0, n = cols.templates.size(); i < n; i++) {
            totalVerts += cols.templates.get(i).vertexCount;
        }
        final int bytes = format.getVertexSize() * totalVerts;
        group.scratch = MeshBuffer.ensureCapacity(group.scratch, bytes, false);
        group.scratch.clear();
        final long base = memAddress0(group.scratch);
        long ptr = base;
        for (int i = 0, n = cols.size; i < n; i++) {
            System.arraycopy(cols.matrices, i * 16, matScratch, 0, 16);
            scratchMat.set(matScratch);
            ptr = VertexTransform.writeInstance(ptr, format, cols.templates.get(i), scratchMat, scratchVec, cols.colors.getInt(i), cols.lights.getInt(i), group.instTexMatrices.get(i));
        }
        group.scratch.position((int) (ptr - base));
        group.scratch.flip();
        group.builtVertexCount = totalVerts;
        group.mesh.upload(format, group.layer.getDrawMode(), group.scratch, group.builtVertexCount, true);
        group.meshBytes = bytes;
        group.builtHash = group.hashAcc;
        group.builtCount = cols.size;
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
