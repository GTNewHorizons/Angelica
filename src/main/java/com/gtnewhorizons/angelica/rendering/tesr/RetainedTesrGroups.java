package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.CombinedGlint;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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

    static final int PROMOTE_AFTER_REBAKES = 3;
    static final long PROMOTE_WINDOW_MS = 500;
    static final int PROMOTE_MAX_TEMPLATES = 8;
    static final long STABILITY_PROBE_MS = 250;
    static final int DEMOTE_AFTER_STABLE_PROBES = 2;
    static final long IDLE_RESET_FRAMES = 100;
    static final long GROUP_TTL_MS = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;
    static final int MERGE_SCAN = 4;

    private static final Tracy.ZoneId Z_TESR_REBAKE = Tracy.zoneId("tesrRebake", Tracy.COLOR_CLIENT);

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
    private final TemplateBuffer[] budgetSeen = new TemplateBuffer[PROMOTE_MAX_TEMPLATES];
    private double camX, camY, camZ;
    private long frameMark;
    GlintCapture glintCapture;

    long rebakes;
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
        final ObjectArrayList<EntityTextureAtlas.Slot> atlasSlots = new ObjectArrayList<>();
        boolean anyAtlasSlot;
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

        EntityTextureAtlas.Slot atlasSlot(int i) {
            return anyAtlasSlot ? atlasSlots.get(i) : null;
        }

        EntityTextureAtlas.Slot atlasSlot(TexRun run, int i) {
            return run.source != null ? null : atlasSlot(i);
        }

        void addCubes(TemplateBuffer templateOrNull, CubeParams[] cubeParams, float scale, Matrix4fc mv, int light, int color, int overlay, long info, EntityTextureAtlas.Slot atlasSlot) {
            cubes.add(cubeParams);
            atlasSlots.add(atlasSlot);
            anyAtlasSlot |= atlasSlot != null;
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
                atlasSlots.size(atlasSlots.size() + n);
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
            atlasSlots.clear();
            anyAtlasSlot = false;
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

    static final class Snapshot {
        final ObjectArrayList<TemplateBuffer> templates = new ObjectArrayList<>();
        final IntArrayList lights = new IntArrayList();
        final IntArrayList colors = new IntArrayList();
        float[] matrices = new float[16];
        float[] texMatrices = new float[TesrAnchorMath.TEX_MATRIX_TERMS];
        int size;
        long hashSum;

        boolean matches(int i, TemplateBuffer template, float[] matrix, int offset, int light, int color, Matrix4f texMatrix) {
            return i < size && templates.get(i) == template && lights.getInt(i) == light && colors.getInt(i) == color
                && TesrAnchorMath.sameTransform(matrices, i * 16, matrix, offset)
                && TesrAnchorMath.sameTexMatrix(texMatrices, i * TesrAnchorMath.TEX_MATRIX_TERMS, texMatrix);
        }

        private void ensure(int instances) {
            if (instances * 16 <= matrices.length) return;
            matrices = Arrays.copyOf(matrices, Math.max(instances * 16, matrices.length << 1));
            texMatrices = Arrays.copyOf(texMatrices, matrices.length / 16 * TesrAnchorMath.TEX_MATRIX_TERMS);
        }

        boolean exchange(int i, TemplateBuffer template, float[] matrix, int light, int color, Matrix4f texMatrix) {
            final boolean same = matches(i, template, matrix, 0, light, color, texMatrix);
            ensure(i + 1);
            System.arraycopy(matrix, 0, matrices, i * 16, 16);
            if (i < templates.size()) {
                templates.set(i, template);
                lights.set(i, light);
                colors.set(i, color);
            } else {
                templates.add(template);
                lights.add(light);
                colors.add(color);
            }
            TesrAnchorMath.storeTexMatrix(texMatrices, i * TesrAnchorMath.TEX_MATRIX_TERMS, texMatrix);
            return same;
        }

        void reset() {
            templates.clear();
            lights.clear();
            colors.clear();
            size = 0;
        }

        boolean sameTemplates(InstanceColumns cols) {
            if (cols.size != size) return false;
            for (int i = 0; i < size; i++) {
                if (templates.get(i) != cols.templates.get(i)) return false;
            }
            return true;
        }

        void copyFrom(InstanceColumns cols, ObjectArrayList<Matrix4f> instTexMatrices, long hashSum) {
            final int n = cols.size;
            ensure(n);
            System.arraycopy(cols.matrices, 0, matrices, 0, n * 16);
            templates.clear();
            templates.addAll(cols.templates);
            lights.clear();
            lights.addElements(0, cols.lights.elements(), 0, n);
            colors.clear();
            colors.addElements(0, cols.colors.elements(), 0, n);
            for (int i = 0; i < n; i++) {
                TesrAnchorMath.storeTexMatrix(texMatrices, i * TesrAnchorMath.TEX_MATRIX_TERMS, instTexMatrices.get(i));
            }
            size = n;
            this.hashSum = hashSum;
        }
    }

    static final class Probe {
        final Snapshot snapshot = new Snapshot();
        boolean probing;
        int count;
        long hashSum;
        boolean ordered;
        int lastCount = -1;
        long lastHashSum;
        int stableProbes;
        long lastMs;

        void start(long nowMs) {
            probing = true;
            lastMs = nowMs;
            count = 0;
            hashSum = 0;
            ordered = true;
        }

        void record(TemplateBuffer template, float[] matrix, int light, int color, Matrix4f texMatrix, long hash) {
            ordered &= snapshot.exchange(count++, template, matrix, light, color, texMatrix);
            hashSum += hash;
        }

        boolean finish() {
            final boolean stable = count == lastCount && (ordered || hashSum == lastHashSum);
            lastCount = count;
            lastHashSum = hashSum;
            snapshot.size = count;
            stableProbes = stable ? stableProbes + 1 : 0;
            return stableProbes >= DEMOTE_AFTER_STABLE_PROBES;
        }

        void reset() {
            stableProbes = 0;
            lastCount = -1;
            snapshot.reset();
            probing = false;
        }
    }

    static final class Anchor {
        long x, y, z;

        void set(Anchor other) {
            x = other.x;
            y = other.y;
            z = other.z;
        }

        boolean sameAs(Anchor other) {
            return x == other.x && y == other.y && z == other.z;
        }

        boolean stale(double camX, double camY, double camZ) {
            return TesrAnchorMath.shouldReanchor(camX, camY, camZ, x, y, z);
        }

        void moveTo(double camX, double camY, double camZ) {
            x = TesrAnchorMath.anchorCoord(camX);
            y = TesrAnchorMath.anchorCoord(camY);
            z = TesrAnchorMath.anchorCoord(camZ);
        }
    }

    static final class DepthEqualSet {
        final ObjectArrayList<Group> members = new ObjectArrayList<>();
        int depthEqualMembers;
        int streamMembers;
        boolean instanced;
        long effectiveFrame = -1;
        long checkedFrame = -1;
        final Anchor anchor = new Anchor();
        long anchorFrame = -1;

        void add(Group group) {
            members.add(group);
            if (group.material.isDepthEqual()) depthEqualMembers++;
            if (group.stream) streamMembers++;
            group.depthEqualSet = this;
        }

        void remove(Group group) {
            members.remove(group);
            if (group.material.isDepthEqual()) depthEqualMembers--;
            if (group.stream) streamMembers--;
        }

        void clearPath() {
            instanced = false;
            effectiveFrame = -1;
        }
    }

    static final class Group {
        final RenderLayer layer;
        final TesrMaterial material;
        final int blockEntityId;
        final boolean opaque;
        final boolean stream;
        final boolean mergeRuns;
        final boolean couplable;
        long coupledScope;
        Class<?> renderableClass;
        int rebakesWindow;
        final Anchor anchor = new Anchor();
        boolean anchored;
        long frameMark = -1;
        long lastUsedMs;
        final int entityColor;
        final int itemId;
        final DrawStateKey state;

        final InstanceColumns templateColumns = new InstanceColumns();
        final InstanceColumns cubeColumns = new InstanceColumns();
        final ObjectArrayList<Matrix4f> instTexMatrices = new ObjectArrayList<>();
        private final ObjectArrayList<Matrix4f> texMatrixPool = new ObjectArrayList<>();
        private int texMatrixPoolUsed;
        private final ObjectArrayList<TexRun> runPool = new ObjectArrayList<>();
        private int runPoolUsed;
        final Snapshot snapshot = new Snapshot();
        boolean dirty;
        DepthEqualSet depthEqualSet;
        final TesrShaderPrograms.Entry shaderEntry;

        int builtCount = -1;
        int builtVertexCount;
        boolean streamPath;
        long promoteWindowStartMs;
        int promoteWindowRebakes;
        boolean queuedThisFrame;
        Probe probe;
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

        Group(RenderLayer layer, TesrMaterial material, int blockEntityId, DrawStateKey state) {
            this.layer = layer;
            this.material = material;
            this.blockEntityId = blockEntityId;
            this.state = state;
            this.itemId = state.item;
            this.entityColor = state.color;
            this.opaque = material.transparency() == TesrMaterial.Transparency.OPAQUE;
            this.stream = material.isStream();
            this.mergeRuns = material.isDepthEqual() && material.isNoDepthWrite();
            this.streamPath = stream;
            this.couplable = material.isDepthEqual() || !material.isNoDepthWrite();
            this.shaderEntry = TesrShaderPrograms.entryFor(material);
        }
    }

    private final Reference2ObjectOpenHashMap<RenderLayer, Reference2ObjectOpenHashMap<TesrMaterial, Object2ObjectOpenHashMap<DrawStateKey, Group>>> groups = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<Group>> byLayer = new Reference2ObjectOpenHashMap<>();
    private long openScope;
    private DepthEqualSet openDepthEqualSet;
    private final ObjectArrayList<Group> openWriters = new ObjectArrayList<>();
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

    /** Mutable lookup avoids allocating a key for each queued model part. Stored keys are never mutated. */
    private static final class DrawStateKey {
        int entity, block, item, color;

        DrawStateKey set(int entity, int block, int item, int color) {
            this.entity = entity;
            this.block = block;
            this.item = item;
            this.color = color;
            return this;
        }

        boolean matches(int entity, int block, int item, int color) {
            return this.entity == entity && this.block == block && this.item == item && this.color == color;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof DrawStateKey key && matches(key.entity, key.block, key.item, key.color);
        }

        @Override
        public int hashCode() { return ((entity * 31 + block) * 31 + item) * 31 + color; }
    }

    private final DrawStateKey lookupState = new DrawStateKey();

    private Group groupFor(RenderLayer layer, TesrMaterial material, int id) {
        Group group = null;
        boolean keyed = false;
        boolean perInstance = false;
        for (int i = 0; i < RECENT_GROUPS; i++) {
            final Group recent = recentGroups[i];
            if (recent == null || recent.layer != layer || recent.material != material) continue;
            if (!keyed) {
                keyed = true;
                perInstance = recent.stream && canInstance(recent);
                keyLookup(perInstance, id);
            }
            if (recent.state.equals(lookupState)) {
                group = recent;
                break;
            }
        }
        if (group == null) {
            if (!keyed) {
                perInstance = streamsInstanced(material);
                keyLookup(perInstance, id);
            }
            Reference2ObjectOpenHashMap<TesrMaterial, Object2ObjectOpenHashMap<DrawStateKey, Group>> byMaterial = groups.get(layer);
            if (byMaterial == null) {
                byMaterial = new Reference2ObjectOpenHashMap<>();
                groups.put(layer, byMaterial);
            }
            Object2ObjectOpenHashMap<DrawStateKey, Group> byState = byMaterial.get(material);
            if (byState == null) {
                byState = new Object2ObjectOpenHashMap<>();
                byMaterial.put(material, byState);
            }
            group = byState.get(lookupState);
            if (group == null) {
                final DrawStateKey key = new DrawStateKey().set(lookupState.entity, lookupState.block, lookupState.item, lookupState.color);
                group = new Group(layer, material, perInstance ? 0 : id, key);
                group.lastUsedMs = nowMs;
                byState.put(key, group);
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

    private void keyLookup(boolean perInstance, int id) {
        if (perInstance) {
            lookupState.set(0, 0, 0, 0);
            return;
        }
        final CapturedRenderingState captured = CapturedRenderingState.INSTANCE;
        final boolean entityKind = source.effectiveIdKind() == AngelicaBufferSource.GroupIdKind.ENTITY;
        lookupState.set(entityKind ? id : captured.getCurrentRenderedEntity(), entityKind ? captured.getCurrentRenderedBlockEntity() : id,
            captured.getCurrentRenderedItem(), AngelicaBufferSource.packEntityColor(captured.getCurrentEntityColor()));
    }

    void forceStreaming(RenderLayer layer, TesrMaterial material, int blockEntityId) {
        promote(groupFor(layer, material, blockEntityId));
    }

    void streamVertices(RenderLayer layer, int id, int[] packed, int vertexCount, Matrix4fc mv) {
        if (glintCapture != null) glintCapture.invalidate();
        source.getBuffer(layer, id).addPackedVertices(packed, vertexCount, mv, scratchVec);
        streamedInstances++;
    }

    void queue(long scope, TemplateBuffer template, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, long entityInfo, int id, Matrix4f texMatrix) {
        queue(scope, template, null, 1.0f, layer, material, currentMV, packedLight, colorABGR, overlayABGR, entityInfo, id, texMatrix, null);
    }

    boolean instancesCubes(TesrMaterial material) {
        return streamsInstanced(material) && material.shader() == null && glintCapture == null;
    }

    void queue(long scope, TemplateBuffer template, CubeParams[] cubes, float scale, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, long entityInfo, int id, Matrix4f texMatrix, EntityTextureAtlas.Slot atlasSlot) {
        final Group group = groupFor(layer, material, id);
        touch(group, layer);
        if (group.couplable) linkDepthScope(group, scope);
        group.queuedThisFrame = true;
        if (group.streamPath) {
            final boolean instancedPath = drawsInstanced(group);
            final boolean segments = !instancedPath && template.drawMode == layer.getDrawMode();
            if (group.probe != null && group.probe.probing && (instancedPath || segments)) {
                probe(group, template, currentMV, packedLight, colorABGR, texMatrix);
            }
            final boolean capturingBase = glintCapture != null && glintCapture.capturesBase();
            if (instancedPath && cubes != null && material.shader() == null) {
                final TexRun run = group.openOrExtendRun(group.cubeColumns, texMatrix, false, capturingBase && group.opaque);
                group.cubeColumns.addCubes(deferred != null ? template : null, cubes, scale, currentMV, packedLight, colorABGR, overlayABGR, entityInfo, atlasSlot);
                run.last.end = group.cubeColumns.size;
                run.parts++;
                run.instances += cubes.length;
                if (glintCapture != null) glintCapture.record(group, group.cubeColumns, run, group.cubeColumns.size - 1, group.cubeColumns.size, cubes.length);
                return;
            }
            if (instancedPath) {
                final TexRun run = group.openOrExtendRun(group.templateColumns, texMatrix, false, capturingBase && group.opaque);
                group.templateColumns.add(template, currentMV, packedLight, colorABGR, overlayABGR, entityInfo);
                run.last.end = group.templateColumns.size;
                run.parts++;
                run.instances++;
                if (glintCapture != null) glintCapture.record(group, group.templateColumns, run, group.templateColumns.size - 1, group.templateColumns.size, 1);
                return;
            }
            if (group.stream && glintCapture != null) glintCapture.invalidate();
            if (segments) {
                source.getBuffer(layer, id).addTemplateInstance(template, currentMV, scratchVec, colorABGR, packedLight, texMatrix);
                streamedInstances++;
                return;
            }
            if (!group.stream) {
                final DepthEqualSet set = activeDepthEqualSet(group);
                if (set != null) {
                    switchSetNextFrame(set, false);
                } else {
                    resetPromotion(group);
                }
            }
        }
        toAnchorRelative(group, currentMV);
        final Matrix4f bakedTexMatrix = atlasSlot != null ? atlasSlot.uvMatrix : texMatrix;
        final InstanceColumns cols = group.templateColumns;
        final int index = cols.size;
        cols.add(template, scratchMat, packedLight, colorABGR, overlayABGR, entityInfo);
        group.addTexMatrix(bakedTexMatrix);
        if (!group.dirty && !group.snapshot.matches(index, template, cols.matrices, index * 16, packedLight, colorABGR, bakedTexMatrix)) {
            group.dirty = true;
        }
    }

    private void linkDepthScope(Group group, long scope) {
        if (scope == 0) return;
        if (scope != openScope) {
            openScope = scope;
            openDepthEqualSet = null;
            openWriters.clear();
        }
        if (group.coupledScope == scope) return;
        group.coupledScope = scope;
        if (openDepthEqualSet == null && !group.material.isDepthEqual()) {
            openWriters.add(group);
            return;
        }
        openDepthEqualSet = join(openDepthEqualSet, group);
        for (int i = 0, n = openWriters.size(); i < n; i++) {
            openDepthEqualSet = join(openDepthEqualSet, openWriters.get(i));
        }
        openWriters.clear();
        if (!group.queuedThisFrame) adopt(group, openDepthEqualSet.instanced);
    }

    private DepthEqualSet join(DepthEqualSet set, Group group) {
        final DepthEqualSet own = group.depthEqualSet;
        if (own == null) {
            if (set == null) {
                set = new DepthEqualSet();
                set.anchor.set(group.anchor);
            }
            set.add(group);
            joinAnchor(set, group);
            settle(set, set.instanced || promoted(group));
            return set;
        }
        if (set == null || set == own) return own;
        final DepthEqualSet survivor = set.members.size() >= own.members.size() ? set : own;
        final DepthEqualSet absorbed = survivor == set ? own : set;
        final boolean instanced = wantsInstanced(survivor) || wantsInstanced(absorbed);
        for (int i = 0, n = absorbed.members.size(); i < n; i++) {
            final Group member = absorbed.members.get(i);
            survivor.add(member);
            joinAnchor(survivor, member);
        }
        settle(survivor, instanced);
        absorbed.members.clear();
        absorbed.depthEqualMembers = 0;
        absorbed.streamMembers = 0;
        absorbed.clearPath();
        if (openDepthEqualSet == absorbed) openDepthEqualSet = survivor;
        return survivor;
    }

    private void joinAnchor(DepthEqualSet set, Group group) {
        if (group.frameMark != frameMark) return;
        if (!group.queuedThisFrame) {
            adoptAnchor(group, set);
        } else if (set.anchorFrame < 0) {
            set.anchor.set(group.anchor);
            set.anchorFrame = frameMark;
            for (int i = 0, n = set.members.size(); i < n; i++) {
                final Group member = set.members.get(i);
                if (member.frameMark == frameMark && !member.queuedThisFrame) adoptAnchor(member, set);
            }
        }
    }

    private static void adoptAnchor(Group group, DepthEqualSet set) {
        if (group.anchored && group.anchor.sameAs(set.anchor)) return;
        group.anchor.set(set.anchor);
        group.anchored = true;
        group.builtCount = -1;
    }

    private static boolean promoted(Group group) {
        return group.streamPath && !group.stream;
    }

    private static boolean wantsInstanced(DepthEqualSet set) {
        if (set.depthEqualMembers > 0) return set.instanced;
        for (int i = 0, n = set.members.size(); i < n; i++) {
            if (promoted(set.members.get(i))) return true;
        }
        return false;
    }

    private void settle(DepthEqualSet set, boolean wantInstanced) {
        switchSetNextFrame(set, (wantInstanced || set.streamMembers > 0) && allCanInstance(set));
    }

    private void refresh(DepthEqualSet set) {
        if (set.checkedFrame == frameMark) return;
        set.checkedFrame = frameMark;
        if (set.anchorFrame != frameMark && set.anchor.stale(camX, camY, camZ)) set.anchor.moveTo(camX, camY, camZ);
        set.anchorFrame = frameMark;
        if ((set.streamMembers > 0 || set.instanced) && set.instanced != allCanInstance(set)) {
            set.instanced = !set.instanced;
            set.effectiveFrame = frameMark - 1;
        }
    }

    private void adopt(Group group, boolean instanced) {
        if (group.streamPath == instanced) return;
        if (instanced) {
            promote(group);
        } else {
            group.streamPath = false;
            clearPromotionTracking(group);
        }
    }

    private void toAnchorRelative(Group group, Matrix4f currentMV) {
        scratchMat.set(baseMVInv).mul(currentMV);
        TesrAnchorMath.toAnchorRelative(scratchMat, camX, camY, camZ, group.anchor.x, group.anchor.y, group.anchor.z);
    }

    private void probe(Group group, TemplateBuffer template, Matrix4f currentMV, int packedLight, int colorABGR, Matrix4f texMatrix) {
        toAnchorRelative(group, currentMV);
        scratchMat.get(matScratch);
        group.probe.record(template, matScratch, packedLight, colorABGR, texMatrix, instanceHash(template, scratchMat, packedLight, colorABGR, texMatrix));
    }

    private static long instanceHash(TemplateBuffer template, Matrix4f anchorRelative, int packedLight, int colorABGR, Matrix4f texMatrix) {
        return TesrAnchorMath.instanceHash(System.identityHashCode(template), anchorRelative, packedLight, colorABGR) ^ TesrAnchorMath.texMatrixHash(texMatrix);
    }

    private long hashSum(Group group) {
        final InstanceColumns cols = group.templateColumns;
        long sum = 0;
        for (int i = 0, n = cols.size; i < n; i++) {
            scratchMat.set(cols.matrices, i * 16);
            sum += instanceHash(cols.templates.get(i), scratchMat, cols.lights.getInt(i), cols.colors.getInt(i), group.instTexMatrices.get(i));
        }
        return sum;
    }

    boolean canCopyInstances(TesrMaterial material) {
        return material.isStream() && streamsInstanced(material);
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

    void queueGlintLayers(TemplateBuffer template, RenderLayer layer, TesrMaterial material, Matrix4f currentMV, int packedLight, int colorABGR, int overlayABGR, long entityInfo, Matrix4f first, Matrix4f second) {
        final Group group = groupFor(layer, material, 0);
        touch(group, layer);
        final InstanceColumns dst = group.templateColumns;
        final int index = dst.size;
        final TexRun run = group.openOrExtendRun(dst, first, true, false);
        dst.add(template, currentMV, packedLight, colorABGR, overlayABGR, entityInfo);
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
            final DepthEqualSet set = activeDepthEqualSet(group);
            if (set != null) refresh(set);
            if (frameMark - group.frameMark > IDLE_RESET_FRAMES) {
                resetPromotion(group);
            } else if (promoted(group)) {
                final boolean pinned = set != null && set.streamMembers > 0;
                final Probe probe = group.probe;
                if (probe.probing && probe.finish()) {
                    if (set == null) {
                        resetPromotion(group);
                    } else if (!pinned && set.instanced && allStable(set)) {
                        switchSetNextFrame(set, false);
                    }
                }
                if (group.streamPath && !pinned && nowMs - probe.lastMs >= STABILITY_PROBE_MS) {
                    probe.start(nowMs);
                } else {
                    probe.probing = false;
                }
            }
            if (set != null) {
                if (frameMark > set.effectiveFrame) adopt(group, set.instanced);
            } else if (group.stream && !group.streamPath) {
                resetPromotion(group);
            }
            group.queuedThisFrame = false;
            group.frameMark = frameMark;
            group.lastUsedMs = nowMs;
            group.dirty = false;
            group.clearInstances();
            if (set != null) {
                adoptAnchor(group, set);
            } else if (!group.anchored || group.anchor.stale(camX, camY, camZ)) {
                group.anchor.moveTo(camX, camY, camZ);
                group.anchored = true;
                group.builtCount = -1;
            }
            source.declareUse(layer);
        }
    }

    private void switchSetNextFrame(DepthEqualSet set, boolean instanced) {
        if (set.instanced == instanced) return;
        set.instanced = instanced;
        set.effectiveFrame = frameMark;
    }

    private static DepthEqualSet activeDepthEqualSet(Group group) {
        final DepthEqualSet set = group.depthEqualSet;
        return set != null && set.depthEqualMembers > 0 ? set : null;
    }

    private boolean allStable(DepthEqualSet set) {
        for (int i = 0, n = set.members.size(); i < n; i++) {
            final Group member = set.members.get(i);
            if (member.streamPath && frameMark - member.frameMark <= IDLE_RESET_FRAMES && (member.probe == null || member.probe.stableProbes < DEMOTE_AFTER_STABLE_PROBES)) return false;
        }
        return true;
    }

    private boolean allCanInstance(DepthEqualSet set) {
        for (int i = 0, n = set.members.size(); i < n; i++) {
            if (!canInstance(set.members.get(i))) return false;
        }
        return true;
    }

    private static void resetPromotion(Group group) {
        group.streamPath = group.stream;
        clearPromotionTracking(group);
    }

    private static void clearPromotionTracking(Group group) {
        group.promoteWindowRebakes = 0;
        if (group.probe != null) group.probe.reset();
        group.builtCount = -1;
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
        if (deferred != null && anyQueuedInstanced(list)) {
            deferred.rebindCurrentPass();
            matrixAvailable = deferred.hasInstancedVariant(Instancing.TEMPLATE);
            cubeAvailable = deferred.hasInstancedVariant(Instancing.CUBE);
        }
        final int need = drawUninstanced(list, matrixAvailable, cubeAvailable);
        restoreMV();
        if ((need & NEED_MATRIX) != 0) {
            boolean bound = true;
            if (deferred != null) {
                deferred.bindInstancedVariant(Instancing.TEMPLATE);
            } else {
                final TesrShaderPrograms.Entry layerShader = list.get(0).shaderEntry;
                bound = layerShader == null || TesrShaderPrograms.bindInstanced(layerShader);
            }
            if (bound) {
                drawMatrixVariant(list, cubeAvailable);
            } else {
                drawMatrixCpu(list, cubeAvailable);
                restoreMV();
            }
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

    private static boolean queuedInstanced(Group g) {
        return !g.templateColumns.runs.isEmpty() || !g.cubeColumns.runs.isEmpty();
    }

    private boolean anyQueuedInstanced(ObjectArrayList<Group> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (live(group) && queuedInstanced(group)) return true;
        }
        return false;
    }

    private int drawUninstanced(ObjectArrayList<Group> list, boolean matrixAvailable, boolean cubeAvailable) {
        int need = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group)) continue;
            if (!queuedInstanced(group)) {
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
                } else if (matrixAvailable && !group.cubeColumns.anyAtlasSlot) {
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
            if (!live(group) || !queuedInstanced(group)) continue;
            if (group.templateColumns.hasInstances()) {
                final boolean fixedFunction = deferred == null && GLStateManager.getActiveProgram() == 0;
                drawTemplateRuns(group.templateColumns, group.material.special() == TesrMaterial.SpecialRender.GLINT && fixedFunction, group.material == EntityMaterials.ITEM_GLINT);
            }
            if (!cubeAvailable && group.cubeColumns.hasInstances() && !group.cubeColumns.anyAtlasSlot) {
                drawTemplateRuns(group.cubeColumns, group.material == EntityMaterials.GLINT && deferred == null, false);
            }
        }
        restoreTexMatrix();
    }

    private void drawMatrixCpu(ObjectArrayList<Group> list, boolean cubeAvailable) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group) || !queuedInstanced(group)) continue;
            if (group.templateColumns.hasInstances()) drawInstancesCpu(group, group.templateColumns);
            if (!cubeAvailable && group.cubeColumns.hasInstances()) drawInstancesCpu(group, group.cubeColumns);
        }
    }

    private void drawCubeVariant(ObjectArrayList<Group> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            final Group group = list.get(i);
            if (!live(group) || !queuedInstanced(group) || !group.cubeColumns.hasInstances()) continue;
            drawCubeInstances(group);
        }
        restoreTexMatrix();
    }

    private boolean drawsInstanced(Group group) {
        return group.streamPath && canInstance(group);
    }

    private boolean streamsInstanced(TesrMaterial material) {
        return material.isStream() && instancedActive && instancedShaderReady(TesrShaderPrograms.entryFor(material));
    }

    private boolean canInstance(Group group) {
        return instancedActive && instancedShaderReady(group.shaderEntry);
    }

    private boolean instancedShaderReady(TesrShaderPrograms.Entry entry) {
        return entry == null || deferred != null || TesrShaderPrograms.instancedAvailable(entry);
    }

    private void drawRetained(Group group) {
        final InstanceColumns cols = group.templateColumns;
        final Snapshot snapshot = group.snapshot;
        boolean instanceChanged = false;
        if (group.builtCount < 0 || !group.mesh.isUploaded()) {
            rebake(group, hashSum(group));
        } else if (group.dirty || group.builtCount != cols.size) {
            final long sum = hashSum(group);
            final boolean sameShape = snapshot.sameTemplates(cols);
            final boolean reorderedOnly = sameShape && sum == snapshot.hashSum && group.material.transparency() != TesrMaterial.Transparency.TRANSLUCENT;
            if (reorderedOnly) {
                snapshot.copyFrom(cols, group.instTexMatrices, sum);
            } else {
                instanceChanged = sameShape && sum != snapshot.hashSum;
                rebake(group, sum);
            }
        }
        draw(group);
        if (instanceChanged && !group.stream && !group.streamPath && rebakesTooOften(group) && withinTemplateBudget(cols.templates)) {
            final DepthEqualSet set = activeDepthEqualSet(group);
            if (set != null) {
                if (set.streamMembers == 0 && allCanInstance(set)) switchSetNextFrame(set, true);
            } else if (canInstance(group) || group.opaque && templatesMatchLayerDrawMode(group)) {
                promote(group);
            }
        }
    }

    private boolean withinTemplateBudget(ObjectArrayList<TemplateBuffer> templates) {
        final TemplateBuffer[] seen = budgetSeen;
        int distinct = 0;
        boolean within = true;
        outer:
        for (int i = 0, n = templates.size(); i < n; i++) {
            final TemplateBuffer template = templates.get(i);
            for (int j = 0; j < distinct; j++) {
                if (seen[j] == template) continue outer;
            }
            if (distinct == PROMOTE_MAX_TEMPLATES) {
                within = false;
                break;
            }
            seen[distinct++] = template;
        }
        Arrays.fill(seen, 0, distinct, null);
        return within;
    }

    private static boolean templatesMatchLayerDrawMode(Group group) {
        final ObjectArrayList<TemplateBuffer> templates = group.templateColumns.templates;
        final int drawMode = group.layer.getDrawMode();
        for (int i = 0, n = templates.size(); i < n; i++) {
            if (templates.get(i).drawMode != drawMode) return false;
        }
        return true;
    }

    private boolean rebakesTooOften(Group group) {
        if (group.promoteWindowRebakes == 0 || nowMs - group.promoteWindowStartMs > PROMOTE_WINDOW_MS) {
            group.promoteWindowStartMs = nowMs;
            group.promoteWindowRebakes = 1;
            return false;
        }
        return ++group.promoteWindowRebakes >= PROMOTE_AFTER_REBAKES;
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
        return InstancedAttribs.unpackId(info, 0);
    }

    static int blockEntityFromInfo(long info) {
        return InstancedAttribs.unpackId(info, 16);
    }

    static int itemFromInfo(long info) {
        return InstancedAttribs.unpackId(info, 32);
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
                    final long info = data.infos.getLong(sub);
                    final int overlay = data.overlays.getInt(sub);
                    int subEnd = sub;
                    while (subEnd < runEnd && data.infos.getLong(subEnd) == info && data.overlays.getInt(subEnd) == overlay) {
                        subEnd++;
                    }
                    source.applyIdsAndRebind(entityFromInfo(info), blockEntityFromInfo(info), itemFromInfo(info));
                    AngelicaBufferSource.setEntityColor(overlay);
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
                            scratchMat.set(data.matrices, i * 16);
                            final int color = head.source != null ? head.color : data.colors.getInt(i);
                            final EntityTextureAtlas.Slot slot = data.atlasSlot(head, i);
                            ptr = VertexTransform.writeInstance(ptr, format, templates.get(i), scratchMat, scratchVec, color, data.lights.getInt(i), slot != null ? slot.uvMatrix : texMatrix);
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
        group.streamPath = true;
        if (!group.stream) streamPromotions++;
        if (group.probe == null) group.probe = new Probe();
        clearPromotionTracking(group);
        group.probe.lastMs = nowMs;
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
                final DepthEqualSet set = group.depthEqualSet;
                if (set != null) {
                    set.remove(group);
                    if (group.material.isDepthEqual() && set.depthEqualMembers == 0) set.clearPath();
                }
                final Reference2ObjectOpenHashMap<TesrMaterial, Object2ObjectOpenHashMap<DrawStateKey, Group>> byMaterial = groups.get(group.layer);
                if (byMaterial != null) {
                    final Object2ObjectOpenHashMap<DrawStateKey, Group> byId = byMaterial.get(group.material);
                    if (byId != null) {
                        byId.remove(group.state);
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
        openScope = 0;
        openDepthEqualSet = null;
        openWriters.clear();
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
                if (list.get(i).streamPath) count++;
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

    private void rebake(Group group, long hashSum) {
        rebakes++;
        group.rebakesWindow++;
        if (Tracy.ENABLED) {
            Tracy.beginZone(Z_TESR_REBAKE);
            Tracy.zoneText(attribution(group));
        }
        try {
            rebakeInner(group, hashSum);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    void collectRebakers(ObjectArrayList<Group> out) {
        for (final ObjectArrayList<Group> list : byLayer.values()) {
            for (int i = 0, n = list.size(); i < n; i++) {
                final Group group = list.get(i);
                if (group.rebakesWindow > 0) out.add(group);
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

    private void rebakeInner(Group group, long hashSum) {
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
            scratchMat.set(cols.matrices, i * 16);
            ptr = VertexTransform.writeInstance(ptr, format, cols.templates.get(i), scratchMat, scratchVec, cols.colors.getInt(i), cols.lights.getInt(i), group.instTexMatrices.get(i));
        }
        group.snapshot.copyFrom(cols, group.instTexMatrices, hashSum);
        group.scratch.position((int) (ptr - base));
        group.scratch.flip();
        group.builtVertexCount = totalVerts;
        group.mesh.upload(format, group.layer.getDrawMode(), group.scratch, group.builtVertexCount, true);
        group.meshBytes = bytes;
        group.builtCount = cols.size;
    }

    private void draw(Group group) {
        retainedDraws++;
        source.applyIdsAndRebind(group.state.entity, group.state.block, group.state.item);
        AngelicaBufferSource.setEntityColor(group.entityColor);
        drawMV.set(baseMV).translate((float) (group.anchor.x - camX), (float) (group.anchor.y - camY), (float) (group.anchor.z - camZ));
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
