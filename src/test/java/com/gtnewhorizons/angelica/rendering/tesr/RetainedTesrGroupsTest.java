package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import net.coderbot.batchedentityrendering.impl.BufferSegment;
import net.coderbot.batchedentityrendering.impl.BufferSourceProbe;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainedTesrGroupsTest {

    private final InstanceRing ring = new InstanceRing();
    private AngelicaBufferSource source;
    private RetainedTesrGroups groups;
    private TestLayer layer;

    static final class TestLayer extends RenderLayer {
        TestLayer() {
            super("test", BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL, GL11.GL_QUADS, 256, () -> {}, () -> {});
        }
    }

    @BeforeEach
    void freshGroups() {
        source = new AngelicaBufferSource();
        groups = new RetainedTesrGroups(source);
        layer = new TestLayer();
    }

    @Test
    void retainedGroupAccumulatesInsteadOfStreaming() {

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer));
        assertEquals(0, groups.streamedInstances);
        final List<RenderLayer> order = BufferSourceProbe.prepare(source);
        assertTrue(order.contains(layer), "declareUse must register the layer");
        assertNull(BufferSourceProbe.segmentsFor(source, layer), "retained instances must not produce dynamic segments");
    }

    @Test
    void streamingGroupStreamsIntoDynamicSegments() {

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        assertEquals(1, groups.streamPromotions);

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 0, 0L, 5, null);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(4f, 5f, 6f), 0, -1, 0, 0L, 5, null);

        assertFalse(groups.hasDraws(layer), "volatile group must not report retained draws");
        assertEquals(2, groups.streamedInstances);

        BufferSourceProbe.prepare(source);
        final List<BufferSegment> segments = BufferSourceProbe.segmentsFor(source, layer);
        assertEquals(1, segments.size());
        assertEquals(8, segments.get(0).getVertexCount(), "both instances stream into one segment");
        assertEquals(5, segments.get(0).getBlockEntityId());
    }

    @Test
    void stableTransformsDemoteStreamingGroup() {
        final TemplateBuffer template = template();
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        long streamedFrames = 0;
        for (int frame = 0; frame < RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, TesrMaterial.CURRENT_STATE, fixed, 0, -1, 0, 0L, 5, null);
            if (groups.hasDraws(layer)) break;
            streamedFrames++;
        }

        assertTrue(groups.hasDraws(layer), "identical frames must demote the group back to retained");
        assertTrue(streamedFrames >= RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES, "demotion must wait for the stability window, streamed " + streamedFrames);
    }

    @Test
    void movingTransformsStayStreaming() {
        final TemplateBuffer template = template();

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        for (int frame = 0; frame < RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(frame, 0f, 0f), 0, -1, 0, 0L, 5, null);
            assertFalse(groups.hasDraws(layer), "moving group must keep streaming at frame " + frame);
        }
    }

    @Test
    void idleGapResetsVolatility() {

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        for (long i = 0; i <= RetainedTesrGroups.IDLE_RESET_FRAMES + 1; i++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
        }
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer), "idle reset must demote the group back to retained");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void translucentStreamingGroupNeverDemotes() {
        final TesrMaterial translucent = TesrMaterial.builder().translucent().build();
        final TemplateBuffer template = template();
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

        groups.forceStreaming(layer, translucent, 5);
        final int frames = RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4;
        for (int frame = 0; frame < frames; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, translucent, fixed, 0, -1, 0, 0L, 5, null);
            assertFalse(groups.hasDraws(layer), "translucent group must keep streaming at frame " + frame);
        }
        assertEquals(frames, groups.streamedInstances);
    }

    @Test
    void streamMaterialStreamsFromFirstQueue() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, stream, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 0, 0L, 5, null);

        assertFalse(groups.hasDraws(layer), "stream group must not report retained draws");
        assertEquals(1, groups.streamedInstances);
        assertEquals(0, groups.streamPromotions, "streaming from birth is not a promotion");

        BufferSourceProbe.prepare(source);
        final List<BufferSegment> segments = BufferSourceProbe.segmentsFor(source, layer);
        assertEquals(1, segments.size());
        assertEquals(5, segments.get(0).getBlockEntityId());
    }

    @Test
    void streamGroupNeverDemotesUnderStableTransforms() {
        for (final TesrMaterial material : new TesrMaterial[] {
            TesrMaterial.builder().stream().build(),
            TesrMaterial.builder().translucent().stream().build()}) {
            freshGroups();
            final TemplateBuffer template = template();
            final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

            final int frames = RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4;
            for (int frame = 0; frame < frames; frame++) {
                groups.beginPass(new Matrix4f(), 0, 0, 0);
                groups.queue(template, layer, material, fixed, 0, -1, 0, 0L, 5, null);
                assertFalse(groups.hasDraws(layer), material.transparency() + " stream group must keep streaming at frame " + frame);
            }
            assertEquals(frames, groups.streamedInstances);
        }
    }

    @Test
    void idleGapKeepsStreamGroupStreaming() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        for (long i = 0; i <= RetainedTesrGroups.IDLE_RESET_FRAMES + 1; i++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
        }
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertFalse(groups.hasDraws(layer), "idle reset must not move a stream group to retained");
        assertEquals(2, groups.streamedInstances);
    }

    @Test
    void streamGroupRoutesToInstanceArraysWhenInstancedCapable() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template(), layer, stream, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer), "instanced-capable stream group accumulates for the hook draw");
        assertEquals(0, groups.streamedInstances, "no segment streaming when instanced");
        BufferSourceProbe.prepare(source);
        assertNull(BufferSourceProbe.segmentsFor(source, layer));
    }

    @Test
    void streamGroupTexMatrixOpensARun() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();
        final Matrix4f texMatrix = new Matrix4f().translation(0.5f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 0, 0L, 5, texMatrix);
        texMatrix.translation(9f, 9f, 9f);

        assertTrue(groups.hasDraws(layer), "a texture matrix now opens an instanced run instead of falling back to streaming");
        assertEquals(0, groups.streamedInstances);
        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(1, group.templateColumns.runs.size());
        assertEquals(new Matrix4f().translation(0.5f, 0.5f, 0f), group.templateColumns.runs.get(0).matrix, "the run holds a copy, not the caller's matrix");
    }

    @Test
    void streamGroupDrawModeMismatchFallsBackToRetained() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();
        final int[] data = new int[3 * VERTEX_SIZE];
        final TemplateBuffer triangles = new TemplateBuffer(data, 3, GL11.GL_TRIANGLES);

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(triangles, layer, stream, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer), "mismatched drawMode must fall back to retained accumulation");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void sweepEvictsIdleGroupsAndKeepsLive() {
        final long[] clock = {1_000L};
        groups = new RetainedTesrGroups(source, () -> clock[0]);
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        assertEquals(2, groups.groupCount());

        clock[0] += RetainedTesrGroups.GROUP_TTL_MS + 1;
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.sweep(clock[0]);

        assertEquals(1, groups.groupCount(), "idle group must be evicted, live group kept");
        assertTrue(groups.hasDraws(layer), "live group still draws this frame");

        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        assertEquals(2, groups.groupCount(), "evicted id must be re-creatable");
    }

    @Test
    void clearEmptiesAllGroups() {
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        groups.clear();

        assertEquals(0, groups.groupCount());
        assertFalse(groups.hasDraws(layer));

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        assertEquals(1, groups.groupCount());
        assertTrue(groups.hasDraws(layer));
    }

    static TemplateBuffer template() {
        final int[] data = new int[4 * VERTEX_SIZE];
        for (int v = 0; v < 4; v++) {
            final int base = v * VERTEX_SIZE;
            data[base + X_INDEX] = Float.floatToRawIntBits(v);
            data[base + COLOR_INDEX] = 0xFFFFFFFF;
        }
        return new TemplateBuffer(data, 4, GL11.GL_QUADS);
    }

    static final TesrMaterial STREAM = TesrMaterial.builder().stream().build();

    static CubeParams[] cubes(int count) {
        final CubeParams[] params = new CubeParams[count];
        for (int i = 0; i < count; i++) {
            params[i] = CubeParams.of(64f, 32f, i % 2 == 1, i * 8, 0, -4f, -4f, -2f, 8, 8, 4, 0f);
            assertNotNull(params[i], "cube " + i + " must pack");
        }
        return params;
    }

    private static int cubeInstances(RetainedTesrGroups groups) {
        final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<RetainedTesrGroups.Group>> byLayer = Reflect.get(groups, "byLayer");
        int total = 0;
        for (final ObjectArrayList<RetainedTesrGroups.Group> list : byLayer.values()) {
            for (int i = 0; i < list.size(); i++) {
                final ObjectArrayList<RetainedTesrGroups.TexRun> runs = list.get(i).cubeColumns.runs;
                for (int r = 0; r < runs.size(); r++) total += runs.get(r).instances;
            }
        }
        return total;
    }

    static RetainedTesrGroups.Group onlyGroup(RetainedTesrGroups groups) {
        final Reference2ObjectOpenHashMap<RenderLayer, ObjectArrayList<RetainedTesrGroups.Group>> byLayer = Reflect.get(groups, "byLayer");
        assertEquals(1, byLayer.size());
        final ObjectArrayList<RetainedTesrGroups.Group> list = byLayer.values().iterator().next();
        assertEquals(1, list.size());
        return list.get(0);
    }

    @Test
    void cubePartsRouteToPerPartRecordsThatKeepTheirTemplate() {
        final TemplateBuffer template = template();
        final Matrix4f mv = new Matrix4f().translation(1f, 2f, 3f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template, cubes(3), 0.0625f, layer, STREAM, mv, 7, -1, 0, 0L, 5, null);
        groups.queue(template, cubes(2), 0.0625f, layer, STREAM, new Matrix4f(), 7, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer));
        assertEquals(0, groups.streamedInstances);
        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(0, group.templateColumns.size, "cube parts stay out of the template columns");
        assertEquals(2, group.cubeColumns.size, "one record per part, not per cube");
        assertEquals(5, cubeInstances(groups));
        assertTrue(group.cubeColumns.templates.isEmpty(), "the FFP cube path has no fallback that needs the template");

        final RetainedTesrGroups piped = new RetainedTesrGroups(source);
        piped.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring), new CountingPipeline(false, false));
        piped.queue(template, cubes(3), 0.0625f, layer, STREAM, mv, 7, -1, 0, 0L, 5, null);
        piped.queue(template, cubes(2), 0.0625f, layer, STREAM, new Matrix4f(), 7, -1, 0, 0L, 5, null);

        final RetainedTesrGroups.Group pipedGroup = onlyGroup(piped);
        assertEquals(2, pipedGroup.cubeColumns.templates.size(), "a pipeline can fall back to the per-part template");
        assertSame(template, pipedGroup.cubeColumns.templates.get(0));
    }

    static final class CountingPipeline implements TesrInstancingPipeline {

        private final boolean matrixVariant;
        private final boolean cubeVariant;
        int rebinds;
        int matrixBinds;
        int cubeBinds;

        CountingPipeline(boolean matrixVariant, boolean cubeVariant) {
            this.matrixVariant = matrixVariant;
            this.cubeVariant = cubeVariant;
        }

        @Override
        public void rebindCurrentPass() {
            rebinds++;
        }

        @Override
        public boolean hasInstancedVariant(Instancing kind) {
            return switch (kind) {
                case TEMPLATE -> matrixVariant;
                case CUBE -> cubeVariant;
                default -> false;
            };
        }

        @Override
        public void bindInstancedVariant(Instancing kind) {
            switch (kind) {
                case TEMPLATE -> matrixBinds++;
                case CUBE -> cubeBinds++;
                default -> throw new AssertionError(kind);
            }
        }
    }

    @Test
    void cubePartsFallBackToTemplatesWithoutAnInstancedRenderer() {

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), cubes(2), 0.0625f, layer, STREAM, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertEquals(0, cubeInstances(groups));
        assertEquals(1, groups.streamedInstances);
    }

    @Test
    void cubeInstancesClearBetweenFrames() {
        final CubeParams[] params = cubes(2);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template(), params, 0.0625f, layer, STREAM, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template(), params, 0.0625f, layer, STREAM, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertEquals(2, cubeInstances(groups), "the previous frame's instances must not accumulate");
    }

    @Test
    void groupIdOnlySplitsPerEntityWhenAPackIsLoaded() {
        assertEquals(ModelPartBatcher.groupId(true, true, -1, 9), RetainedTesrGroups.entityFromInfo(InstancedAttribs.packEntityInfo(-1, 9, 0)));
    }

    @Test
    void overlayRunsDoNotMergeAcrossEmitters() {
        final TemplateBuffer template = template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);
        groups.queue(template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 2, matrixA);
        groups.queue(template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 3, matrixB);
        groups.queue(template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 4, matrixA);

        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(3, group.templateColumns.runs.size(), "OVERLAY is depth-equal but writes depth, so it must not merge like GLINT");
    }

    @Test
    void pooledRunsDropStaleChains() {
        final TemplateBuffer template = template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);
        groups.queue(template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 2, matrixB);
        groups.queue(template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 3, matrixA);
        final RetainedTesrGroups.Group frame1 = onlyGroup(groups);
        assertEquals(2, frame1.templateColumns.runs.size());
        assertNotNull(frame1.templateColumns.runs.get(0).next, "sanity: frame 1 must build a chain");

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);

        final RetainedTesrGroups.Group frame2 = onlyGroup(groups);
        assertEquals(1, frame2.templateColumns.runs.size(), "only one distinct matrix was queued this frame");
        final RetainedTesrGroups.TexRun head = frame2.templateColumns.runs.get(0);
        assertNull(head.next, "a pooled run reused from a previous chain must drop its stale next pointer");
        assertSame(head, head.last);
    }

    @Test
    void sweptGroupIsNotResurrectedByTheMemo() {
        final long[] clock = {1_000L};
        groups = new RetainedTesrGroups(source, () -> clock[0]);
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        assertEquals(1, groups.groupCount());

        clock[0] += RetainedTesrGroups.GROUP_TTL_MS + 1;
        groups.sweep(clock[0]);
        assertEquals(0, groups.groupCount(), "the idle group must be evicted");

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);

        assertEquals(1, groups.groupCount(), "a fresh group must be created, not the evicted one resurrected by the last-group memo");
        assertTrue(groups.hasDraws(layer), "the new group must be reachable through the layer map for drawing");
    }

    @Test
    void alternatingMaterialsRouteToTheirOwnGroups() {
        final TemplateBuffer template = template();
        final TesrMaterial materialA = TesrMaterial.builder().stream().build();
        final TesrMaterial materialB = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, materialA, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        groups.queue(template, layer, materialB, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        groups.queue(template, layer, materialA, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        groups.queue(template, layer, materialB, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertEquals(2, groups.groupCount(), "the last-group memo must compare material, not just layer and id, on every queue call");
    }
}
