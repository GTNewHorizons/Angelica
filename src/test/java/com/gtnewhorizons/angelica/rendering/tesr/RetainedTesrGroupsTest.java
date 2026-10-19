package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.BufferSegment;
import net.coderbot.batchedentityrendering.impl.BufferSourceProbe;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.CapturedStateScope;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.DEPTH_EQUAL_OVERLAY;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.SCOPE;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.STREAM;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.byLayer;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.cubes;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.groupsOn;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.layerFor;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.onlyGroup;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.template;
import static com.gtnewhorizons.angelica.rendering.tesr.RetainedTesrTestSupport.testLayer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainedTesrGroupsTest {

    private static final TesrShader NOOP_SHADER = TesrShaders.register("angelica:test_groups_plain", () -> {}, () -> {});

    private final InstanceRing ring = new InstanceRing();
    private AngelicaBufferSource source;
    private RetainedTesrGroups groups;
    private RenderLayer layer;
    private final long[] clock = {1_000L};

    @BeforeEach
    void freshGroups() {
        source = new AngelicaBufferSource();
        groups = new RetainedTesrGroups(source, () -> clock[0]);
        layer = testLayer();
    }

    @Test
    void fallbackKeepsHighMaterialIdsAndTheUnmappedSentinel() {
        for (int id : new int[] { -1, 0, 32767, 32768, 45020, 50020, 50072, 65534 }) {
            final long info = InstancedAttribs.packEntityInfo(id, id, id);
            assertEquals(id, RetainedTesrGroups.entityFromInfo(info));
            assertEquals(id, RetainedTesrGroups.blockEntityFromInfo(info));
            assertEquals(id, RetainedTesrGroups.itemFromInfo(info));
        }
    }

    @Test
    void retainedGroupsSeparateTheFullIdTupleAndTintAndReuseStableKeys() {
        final var state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            for (int frame = 0; frame < 2; frame++) {
                groups.beginPass(new Matrix4f(), 0, 0, 0);
                for (int entity : new int[] {11, 12}) {
                    for (int item : new int[] {13, 14}) {
                        state.setCurrentEntityAndItem(entity, item);
                        for (int block : new int[] {21, 22}) {
                            state.setCurrentBlockEntity(block);
                            for (float red : new float[] {0, 1}) {
                                state.setCurrentEntityColor(red, 0, 0, 0.5f);
                                groups.queue(SCOPE, template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, block, null);
                            }
                        }
                    }
                }
                assertEquals(16, groups.groupCount(), "only distinct ID/tint combinations allocate groups, including across frames");
            }
        }
    }

    @Test
    void instancedGroupsKeepFullIdsAndTintPerInstanceWithoutSplitting() {
        final var state = CapturedRenderingState.INSTANCE;
        try (CapturedStateScope ignored = new CapturedStateScope()) {
            groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring), new RetainedTesrTestSupport.CountingPipeline(true, true));
            for (int i = 0; i < 100; i++) {
                state.setCurrentEntityAndItem(i, i + 200);
                state.setCurrentBlockEntity(i + 100);
                state.setCurrentEntityColor(i / 100f, 0, 0, 1);
                final long info = InstancedAttribs.packEntityInfo(i, i + 100, i + 200);
                final int overlay = AngelicaBufferSource.packEntityColor(state.getCurrentEntityColor());
                groups.queue(SCOPE, template(), layer, STREAM, new Matrix4f(), 0, -1, overlay, info, i + 100, null);
            }
            final var group = onlyGroup(groups);
            assertEquals(100, group.templateColumns.size);
            assertEquals(1, group.templateColumns.runs.size());
            for (int i = 0; i < 100; i++) {
                assertEquals(InstancedAttribs.packEntityInfo(i, i + 100, i + 200), group.templateColumns.infos.getLong(i));
                assertEquals(AngelicaBufferSource.packAbgr(i / 100f, 0, 0, 1), group.templateColumns.overlays.getInt(i));
            }
        }
    }

    @FunctionalInterface
    private interface GroupSetup {
        void apply(RetainedTesrGroups groups, RenderLayer layer);
    }

    private static void idleGap(RetainedTesrGroups groups) {
        for (long i = 0; i <= RetainedTesrGroups.IDLE_RESET_FRAMES + 1; i++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
        }
    }

    static Stream<Arguments> retainedDraws() {
        return Stream.of(
            Arguments.of("fresh group", (GroupSetup) (g, l) -> g.beginPass(new Matrix4f(), 0, 0, 0)),
            Arguments.of("promoted group after an idle gap", (GroupSetup) (g, l) -> {
                g.forceStreaming(l, TesrMaterial.CURRENT_STATE, 5);
                idleGap(g);
            }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("retainedDraws")
    void retainedGroupDrawsWithoutSegments(String name, GroupSetup setup) {
        setup.apply(groups, layer);
        groups.queue(SCOPE, template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer));
        assertEquals(0, groups.streamedInstances);
        final List<RenderLayer> order = BufferSourceProbe.prepare(source);
        assertTrue(order.contains(layer), "declareUse must register the layer");
        assertNull(BufferSourceProbe.segmentsFor(source, layer), "retained instances must not produce dynamic segments");
    }

    private static final TesrMaterial TRANSLUCENT_STREAM = TesrMaterial.builder().translucent().stream().build();

    private static void queueAt(RetainedTesrGroups groups, RenderLayer layer, TesrMaterial material, float x, float y, float z) {
        groups.queue(SCOPE, template(), layer, material, new Matrix4f().translation(x, y, z), 0, -1, 0, 0L, 5, null);
    }

    static Stream<Arguments> streamingDraws() {
        return Stream.of(
            Arguments.of("forced promotion", (GroupSetup) (g, l) -> {
                g.forceStreaming(l, TesrMaterial.CURRENT_STATE, 5);
                g.beginPass(new Matrix4f(), 0, 0, 0);
                queueAt(g, l, TesrMaterial.CURRENT_STATE, 1f, 2f, 3f);
                queueAt(g, l, TesrMaterial.CURRENT_STATE, 4f, 5f, 6f);
            }, 2, 1),
            Arguments.of("stream material from the first queue", (GroupSetup) (g, l) -> {
                g.beginPass(new Matrix4f(), 0, 0, 0);
                queueAt(g, l, TRANSLUCENT_STREAM, 1f, 2f, 3f);
            }, 1, 0),
            Arguments.of("stream material after an idle gap", (GroupSetup) (g, l) -> {
                g.beginPass(new Matrix4f(), 0, 0, 0);
                queueAt(g, l, TRANSLUCENT_STREAM, 0f, 0f, 0f);
                idleGap(g);
                queueAt(g, l, TRANSLUCENT_STREAM, 0f, 0f, 0f);
            }, 2, 0),
            Arguments.of("cube parts without an instanced renderer", (GroupSetup) (g, l) -> {
                g.beginPass(new Matrix4f(), 0, 0, 0);
                g.queue(SCOPE, template(), cubes(2), 0.0625f, l, STREAM, new Matrix4f(), 0, -1, 0, 0L, 5, null, null);
            }, 1, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamingDraws")
    void streamingGroupStreamsIntoDynamicSegments(String name, GroupSetup setup, int instances, int promotions) {
        setup.apply(groups, layer);

        assertFalse(groups.hasDraws(layer), "volatile group must not report retained draws");
        assertEquals(instances, groups.streamedInstances);
        assertEquals(promotions, groups.streamPromotions);
        assertEquals(0, cubeInstances(groups));

        BufferSourceProbe.prepare(source);
        final List<BufferSegment> segments = BufferSourceProbe.segmentsFor(source, layer);
        assertEquals(1, segments.size());
        assertEquals(4 * instances, segments.get(0).getVertexCount());
        assertEquals(5, segments.get(0).getBlockEntityId());
    }

    private void probedFrame(TemplateBuffer template, TesrMaterial material, Matrix4f... instances) {
        clock[0] += RetainedTesrGroups.STABILITY_PROBE_MS;
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        for (final Matrix4f mv : instances) groups.queue(SCOPE, template, layer, material, mv, 0, -1, 0, 0L, 5, null);
    }

    private static IntFunction<Matrix4f[]> perFrame(IntFunction<Matrix4f[]> instances) {
        return instances;
    }

    static Stream<Arguments> probeSequences() {
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);
        final Matrix4f other = new Matrix4f().translation(4f, 5f, 6f);
        final float edge = 1f + 0.5f * TesrAnchorMath.TRANSFORM_TOLERANCE;
        final float jitter = TesrAnchorMath.TRANSFORM_TOLERANCE / 8f;
        final Matrix4f below = new Matrix4f().translation(edge - jitter, 2f, 3f);
        final Matrix4f above = new Matrix4f().translation(edge + jitter, 2f, 3f);
        return Stream.of(
            Arguments.of("stable opaque", TesrMaterial.CURRENT_STATE, perFrame(i -> new Matrix4f[] { fixed }), true),
            Arguments.of("reordered", TesrMaterial.CURRENT_STATE, perFrame(i -> i % 2 == 0 ? new Matrix4f[] { fixed, other } : new Matrix4f[] { other, fixed }), true),
            Arguments.of("jitter inside tolerance", TesrMaterial.CURRENT_STATE, perFrame(i -> new Matrix4f[] { i % 2 == 0 ? below : above }), true),
            Arguments.of("moving", TesrMaterial.CURRENT_STATE, perFrame(i -> new Matrix4f[] { new Matrix4f().translation(i, 0f, 0f) }), false),
            Arguments.of("instance count changes", TesrMaterial.CURRENT_STATE, perFrame(i -> i % 2 == 0 ? new Matrix4f[] { fixed, fixed } : new Matrix4f[] { fixed }), false),
            Arguments.of("rotating", TesrMaterial.CURRENT_STATE, perFrame(i -> new Matrix4f[] { new Matrix4f().rotateY(0.002f * i) }), false),
            Arguments.of("stream material", STREAM, perFrame(i -> new Matrix4f[] { fixed }), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("probeSequences")
    void probedInstancesDecideDemotion(String name, TesrMaterial material, IntFunction<Matrix4f[]> instances, boolean demotes) {
        final TemplateBuffer template = template();
        if (!material.isStream()) groups.forceStreaming(layer, material, 5);
        int frames = 0;
        while (frames < 8 && !groups.hasDraws(layer)) {
            probedFrame(template, material, instances.apply(frames++));
        }
        assertEquals(demotes, groups.hasDraws(layer));
        if (demotes) assertTrue(frames > RetainedTesrGroups.DEMOTE_AFTER_STABLE_PROBES + 1, "the first probe has nothing to match, took " + frames);
    }

    @Test
    void promotedGroupOnlyProbesOncePerInterval() {
        final TemplateBuffer template = template();
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        for (int frame = 0; frame < 64; frame++) {
            clock[0] += 1;
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, fixed, 0, -1, 0, 0L, 5, null);
            assertFalse(groups.hasDraws(layer), "64 ms cannot hold enough probes to demote, frame " + frame);
        }
    }

    private void queueBlock(long scope, TesrMaterial material, int id) {
        groups.queue(scope, template(), layer, material, new Matrix4f(), 0, -1, 0, 0L, id, null);
    }

    @Test
    void depthEqualOverlayCouplesOnlyWithinItsOwnNonZeroRenderScope() {
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        queueBlock(SCOPE, TesrMaterial.CURRENT_STATE, 5);
        queueBlock(SCOPE, DEPTH_EQUAL_OVERLAY, 5);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 6);
        queueBlock(0L, TesrMaterial.CURRENT_STATE, 7);
        queueBlock(0L, DEPTH_EQUAL_OVERLAY, 7);

        final ObjectArrayList<RetainedTesrGroups.Group> entries = groupsOn(groups, layer);
        assertEquals(5, entries.size());
        assertNotNull(entries.get(0).depthEqualSet, "the id-5 writer must belong to a path set");
        assertSame(entries.get(0).depthEqualSet, entries.get(1).depthEqualSet, "the overlay must couple with the writer from its own scope");
        assertNull(entries.get(2).depthEqualSet, "a writer from another scope must not be coupled");
        assertNull(entries.get(3).depthEqualSet, "scope 0 never couples");
        assertNull(entries.get(4).depthEqualSet, "scope 0 never couples");
    }

    private static final TesrMaterial STREAM_OVERLAY = TesrMaterial.builder().additiveAlpha().depthEqual().noDepthWrite().stream().build();
    private static final TesrMaterial BAKED_PLAIN_SHADER = TesrMaterial.builder().shader(NOOP_SHADER).build();

    private void instancedPair(InstancedTemplateRenderer renderer) {
        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
        queueBlock(SCOPE, TesrMaterial.CURRENT_STATE, 5);
        queueBlock(SCOPE, DEPTH_EQUAL_OVERLAY, 5);
    }

    @Test
    void promotedGroupJoiningAFreshDepthEqualSetKeepsItsPath() {
        final InstancedTemplateRenderer renderer = new InstancedTemplateRenderer(ring);
        instancedPair(renderer);
        groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 5);
        queueBlock(SCOPE + 1, DEPTH_EQUAL_OVERLAY, 5);

        final ObjectArrayList<RetainedTesrGroups.Group> pair = groupsOn(groups, layer);
        assertTrue(pair.get(0).streamPath, "joining a fresh set must not demote the promoted writer");
        assertTrue(pair.get(1).streamPath, "the overlay must follow its promoted writer");
    }

    @Test
    void bakedGroupAdoptsAnInstancedDepthEqualSetBeforeItsFirstInstance() {
        final InstancedTemplateRenderer renderer = new InstancedTemplateRenderer(ring);
        instancedPair(renderer);
        groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
        queueBlock(SCOPE + 1, DEPTH_EQUAL_OVERLAY, 5);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 6);

        final RetainedTesrGroups.Group joined = groupsOn(groups, layer).get(2);
        assertEquals(1, joined.templateColumns.runs.size(), "its first instance must already take the set's instanced path");
    }

    @Test
    void memberThatCannotInstanceDemotesTheDepthEqualSet() {
        final InstancedTemplateRenderer renderer = new InstancedTemplateRenderer(ring);
        instancedPair(renderer);
        groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
        queueBlock(SCOPE + 1, DEPTH_EQUAL_OVERLAY, 5);
        queueBlock(SCOPE + 1, BAKED_PLAIN_SHADER, 5);

        assertFalse(groupsOn(groups, layer).get(0).depthEqualSet.instanced, "a member whose shader has no instanced variant forces the set to baked");
    }

    @Test
    void atlasPartPulledOffTheInstancedPathBakesItsSlotUvs() {
        final EntityTextureAtlas.Slot slot = new EntityTextureAtlas.Slot(new EntityTextureAtlas.Page(new ResourceLocation("angelica", "test_page"), 0), 0, 0, 4, 2);
        final InstancedTemplateRenderer renderer = new InstancedTemplateRenderer(ring);
        for (int frame = 0; frame < 2; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
            groups.queue(SCOPE + frame, template(), cubes(1), 0.0625f, layer, STREAM, new Matrix4f(), 0, -1, 0, 0L, 5, null, slot);
            queueBlock(SCOPE + frame, DEPTH_EQUAL_OVERLAY, 5);
            queueBlock(SCOPE + frame, BAKED_PLAIN_SHADER, 5);
        }

        final RetainedTesrGroups.Group atlas = groupsOn(groups, layer).get(0);
        assertFalse(atlas.streamPath, "sanity: a member that cannot instance must pull the atlas writer off the instanced path");
        assertEquals(1, atlas.instTexMatrices.size(), "sanity: the part must take the retained branch");
        assertEquals(slot.uvMatrix, atlas.instTexMatrices.get(0), "a baked atlas part must sample its slot, not the whole atlas page");
    }

    @Test
    void mergedDepthEqualSetsKeepTheInstancedPath() {
        final InstancedTemplateRenderer renderer = new InstancedTemplateRenderer(ring);
        instancedPair(renderer);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 6);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 7);
        queueBlock(SCOPE + 1, DEPTH_EQUAL_OVERLAY, 6);
        groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
        queueBlock(SCOPE + 2, DEPTH_EQUAL_OVERLAY, 5);
        queueBlock(SCOPE + 2, DEPTH_EQUAL_OVERLAY, 6);

        final ObjectArrayList<RetainedTesrGroups.Group> entries = groupsOn(groups, layer);
        assertSame(entries.get(1).depthEqualSet, entries.get(4).depthEqualSet, "both overlays in one scope must merge their sets");
        assertTrue(entries.get(4).depthEqualSet.instanced, "the larger baked set must take the instanced path of the one it absorbs");
    }

    @Test
    void mergedDepthEqualSetsShareTheSurvivorsAnchor() {
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        queueBlock(SCOPE, TesrMaterial.CURRENT_STATE, 5);
        queueBlock(SCOPE, DEPTH_EQUAL_OVERLAY, 5);
        groups.beginPass(new Matrix4f(), 304, 0, 0);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 6);
        queueBlock(SCOPE + 1, TesrMaterial.CURRENT_STATE, 7);
        queueBlock(SCOPE + 1, DEPTH_EQUAL_OVERLAY, 6);
        final ObjectArrayList<RetainedTesrGroups.Group> entries = groupsOn(groups, layer);
        assertEquals(0, entries.get(0).anchor.x, "sanity: the first set anchored at the origin");
        assertEquals(304, entries.get(4).anchor.x, "sanity: the second set anchored at the moved camera");

        groups.beginPass(new Matrix4f(), 304, 0, 0);
        queueBlock(SCOPE + 2, DEPTH_EQUAL_OVERLAY, 5);
        queueBlock(SCOPE + 2, DEPTH_EQUAL_OVERLAY, 6);
        assertSame(entries.get(1).depthEqualSet, entries.get(4).depthEqualSet, "sanity: both overlays in one scope merge their sets");
        assertEquals(0, entries.get(1).anchor.x, "a member that already queued this frame keeps its anchor until the next frame");

        groups.beginPass(new Matrix4f(), 304, 0, 0);
        queueBlock(SCOPE + 3, TesrMaterial.CURRENT_STATE, 5);
        queueBlock(SCOPE + 3, DEPTH_EQUAL_OVERLAY, 5);
        assertEquals(304, entries.get(0).anchor.x, "an absorbed writer must take the larger set's anchor");
        assertEquals(304, entries.get(1).anchor.x, "an absorbed overlay must take the larger set's anchor");
    }

    @ParameterizedTest(name = "instancing {0}")
    @ValueSource(booleans = { true, false })
    void streamOverlayPinsDepthEqualSetInstancedOrBakesWithItsWriter(boolean available) {
        final InstancedTemplateRenderer renderer = available ? new InstancedTemplateRenderer(ring) : null;
        for (int frame = 0; frame < 2; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0, renderer);
            queueBlock(SCOPE + frame, TesrMaterial.CURRENT_STATE, 5);
            queueBlock(SCOPE + frame, STREAM_OVERLAY, 5);
        }

        final ObjectArrayList<RetainedTesrGroups.Group> pair = groupsOn(groups, layer);
        assertEquals(available, pair.get(0).streamPath, "the writer must take the pinned path");
        assertEquals(available, pair.get(1).streamPath, "the stream overlay must share its writer's path");
    }

    @ParameterizedTest(name = "stream material {0}")
    @ValueSource(booleans = { false, true })
    void streamingGroupRoutesToInstanceArraysWhenInstancedCapable(boolean streamMaterial) {
        final TesrMaterial material = streamMaterial ? TesrMaterial.builder().translucent().stream().build() : TesrMaterial.builder().additiveAlpha().build();
        final long info = InstancedAttribs.packEntityInfo(7, 5, 9);

        if (!streamMaterial) groups.forceStreaming(layer, material, 5);
        groups.beginPass(new Matrix4f(), 100.5, 64.25, -100.75, new InstancedTemplateRenderer(ring));
        groups.queue(SCOPE, template(), layer, material, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 0, info, 5, null);

        assertTrue(groups.hasDraws(layer), "a streaming group accumulates for the instanced hook draw");
        assertEquals(0, groups.streamedInstances, "no segment streaming when instanced");
        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(1, group.templateColumns.runs.size());
        assertEquals(info, group.templateColumns.infos.getLong(0));
        assertEquals(3f, group.templateColumns.matrices[14], "instanced columns hold the caller's modelview, not the anchor-relative one");
        BufferSourceProbe.prepare(source);
        assertNull(BufferSourceProbe.segmentsFor(source, layer));
    }

    @ParameterizedTest(name = "stream material {0}")
    @ValueSource(booleans = { false, true })
    void drawModeMismatchFallsBackToRetained(boolean streamMaterial) {
        final TesrMaterial material = streamMaterial ? TesrMaterial.builder().translucent().stream().build() : TesrMaterial.CURRENT_STATE;
        final TemplateBuffer triangles = new TemplateBuffer(new int[3 * VERTEX_SIZE], 3, GL11.GL_TRIANGLES);

        if (!streamMaterial) groups.forceStreaming(layer, material, 5);
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, triangles, layer, material, new Matrix4f(), 0, -1, 0, 0L, 5, null);

        assertTrue(groups.hasDraws(layer), "a template the layer cannot segment-stream falls back to retained accumulation");
        assertEquals(0, groups.streamedInstances);
        assertEquals(streamMaterial, onlyGroup(groups).streamPath, "a promoted group returns to baked, a stream group keeps streaming");
    }

    @Test
    void streamGroupTexMatrixOpensARun() {
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();
        final Matrix4f texMatrix = new Matrix4f().translation(0.5f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(SCOPE, template(), layer, stream, new Matrix4f(), 0, -1, 0, 0L, 5, texMatrix);
        texMatrix.translation(9f, 9f, 9f);

        assertTrue(groups.hasDraws(layer), "a texture matrix now opens an instanced run instead of falling back to streaming");
        assertEquals(0, groups.streamedInstances);
        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(1, group.templateColumns.runs.size());
        assertEquals(new Matrix4f().translation(0.5f, 0.5f, 0f), group.templateColumns.runs.get(0).matrix, "the run holds a copy, not the caller's matrix");
    }

    @Test
    void sweepEvictsIdleGroupsAndKeepsLive() {
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        assertEquals(2, groups.groupCount());

        clock[0] += RetainedTesrGroups.GROUP_TTL_MS + 1;
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.sweep(clock[0]);

        assertEquals(1, groups.groupCount(), "idle group must be evicted, live group kept");
        assertTrue(groups.hasDraws(layer), "live group still draws this frame");

        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        assertEquals(2, groups.groupCount(), "evicted id must be re-creatable");
    }

    @Test
    void clearEmptiesAllGroups() {
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 2, null);
        groups.clear();

        assertEquals(0, groups.groupCount());
        assertFalse(groups.hasDraws(layer));

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        assertEquals(1, groups.groupCount());
        assertTrue(groups.hasDraws(layer));
    }

    private static int cubeInstances(RetainedTesrGroups groups) {
        int total = 0;
        for (final ObjectArrayList<RetainedTesrGroups.Group> list : byLayer(groups).values()) {
            for (int i = 0; i < list.size(); i++) {
                final ObjectArrayList<RetainedTesrGroups.TexRun> runs = list.get(i).cubeColumns.runs;
                for (int r = 0; r < runs.size(); r++) total += runs.get(r).instances;
            }
        }
        return total;
    }

    @Test
    void cubePartsKeepPerPartRecordsAndTheirTemplateWithoutAccumulatingAcrossFrames() {
        final TemplateBuffer template = template();

        for (int frame = 0; frame < 2; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring), new RetainedTesrTestSupport.CountingPipeline(false, false));
            groups.queue(SCOPE, template, cubes(3), 0.0625f, layer, STREAM, new Matrix4f().translation(1f, 2f, 3f), 7, -1, 0, 0L, 5, null, null);
            groups.queue(SCOPE, template, cubes(2), 0.0625f, layer, STREAM, new Matrix4f(), 7, -1, 0, 0L, 5, null, null);
        }

        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(2, group.cubeColumns.size, "one record per part, not per cube");
        assertEquals(5, cubeInstances(groups), "the previous frame's instances must not accumulate");
        assertEquals(0, group.templateColumns.size, "cube parts stay out of the template columns");
        assertEquals(2, group.cubeColumns.templates.size(), "a pipeline can fall back to the per-part template");
        assertSame(template, group.cubeColumns.templates.get(0));
    }

    @Test
    void overlayRunsDoNotMergeAcrossEmitters() {
        final TemplateBuffer template = template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(SCOPE, template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);
        groups.queue(SCOPE, template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 2, matrixA);
        groups.queue(SCOPE, template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 3, matrixB);
        groups.queue(SCOPE, template, layer, EntityMaterials.OVERLAY, new Matrix4f(), 0, -1, 0, 0L, 4, matrixA);

        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(3, group.templateColumns.runs.size(), "OVERLAY is depth-equal but writes depth, so it must not merge like GLINT");
    }

    @Test
    void pooledRunsDropStaleChains() {
        final TemplateBuffer template = template();
        final Matrix4f matrixA = new Matrix4f().translation(0.5f, 0f, 0f);
        final Matrix4f matrixB = new Matrix4f().translation(0f, 0.5f, 0f);

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(SCOPE, template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);
        groups.queue(SCOPE, template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 2, matrixB);
        groups.queue(SCOPE, template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 3, matrixA);
        final RetainedTesrGroups.Group frame1 = onlyGroup(groups);
        assertEquals(2, frame1.templateColumns.runs.size());
        assertNotNull(frame1.templateColumns.runs.get(0).next, "sanity: frame 1 must build a chain");

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring));
        groups.queue(SCOPE, template, layer, EntityMaterials.GLINT, new Matrix4f(), 0, -1, 0, 0L, 1, matrixA);

        final RetainedTesrGroups.Group frame2 = onlyGroup(groups);
        assertEquals(1, frame2.templateColumns.runs.size(), "only one distinct matrix was queued this frame");
        final RetainedTesrGroups.TexRun head = frame2.templateColumns.runs.get(0);
        assertNull(head.next, "a pooled run reused from a previous chain must drop its stale next pointer");
        assertSame(head, head.last);
    }

    @Test
    void sweptGroupIsNotResurrectedByTheMemo() {
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);
        assertEquals(1, groups.groupCount());

        clock[0] += RetainedTesrGroups.GROUP_TTL_MS + 1;
        groups.sweep(clock[0]);
        assertEquals(0, groups.groupCount(), "the idle group must be evicted");

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(SCOPE, template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 0, 0L, 1, null);

        assertEquals(1, groups.groupCount(), "a fresh group must be created, not the evicted one resurrected by the last-group memo");
        assertTrue(groups.hasDraws(layer), "the new group must be reachable through the layer map for drawing");
    }

    private static final TesrMaterial PLAIN_SHADER = TesrMaterial.builder().stream().shader(NOOP_SHADER).build();

    private int instancedAfterQueue(TesrMaterial material, TesrInstancingPipeline pipeline) {
        final RetainedTesrGroups fresh = new RetainedTesrGroups(source);
        fresh.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring), pipeline);
        fresh.queue(SCOPE, template(), layerFor(material), material, new Matrix4f(), 0, -1, 0, 0L, 5, null);
        final RetainedTesrGroups.Group group = onlyGroup(fresh);
        return group.templateColumns.runs.isEmpty() ? 0 : group.templateColumns.size;
    }

    @Test
    void runnableShaderMaterialIsInstancedOnlyUnderAPack() {
        assertEquals(0, instancedAfterQueue(PLAIN_SHADER, null), "its program cannot read the per-instance attributes");
        assertEquals(1, instancedAfterQueue(PLAIN_SHADER, new RetainedTesrTestSupport.CountingPipeline(true, true)), "the pack draws it with its own instanced variant");

        final RenderLayer shaderLayer = layerFor(PLAIN_SHADER);
        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer(ring), new RetainedTesrTestSupport.CountingPipeline(true, true));
        groups.queue(SCOPE, template(), cubes(3), 0.0625f, shaderLayer, PLAIN_SHADER, new Matrix4f(), 0, -1, 0, 0L, 5, null, null);

        final RetainedTesrGroups.Group group = onlyGroup(groups);
        assertEquals(0, cubeInstances(groups), "a shader material never takes the cube path");
        assertEquals(1, group.templateColumns.size);
        assertEquals(1, group.templateColumns.runs.size());
    }

    @Test
    void glintCopiesSampleTheirOwnTextureRatherThanTheBaseAtlasRegion() {
        final EntityTextureAtlas.Page page = new EntityTextureAtlas.Page(new ResourceLocation("angelica", "test_page"), 0);
        final EntityTextureAtlas.Slot slot = new EntityTextureAtlas.Slot(page, 0, 0, 4, 2);
        final RetainedTesrGroups.InstanceColumns base = new RetainedTesrGroups.InstanceColumns();
        base.addCubes(null, cubes(1), 0.0625f, new Matrix4f(), 0, -1, 0, 0L, slot);
        final RetainedTesrGroups.InstanceColumns glint = new RetainedTesrGroups.InstanceColumns();
        glint.appendRange(base, 0, 1, 0, -1, 0);

        assertSame(slot, base.atlasSlot(0));
        assertNull(glint.atlasSlot(0));
    }
}
