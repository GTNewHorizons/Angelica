package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import net.coderbot.batchedentityrendering.impl.AngelicaBufferSource;
import net.coderbot.batchedentityrendering.impl.BatchVertexFormats;
import net.coderbot.batchedentityrendering.impl.BufferSegment;
import net.coderbot.batchedentityrendering.impl.BufferSourceProbe;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainedTesrGroupsTest {

    private static final class TestLayer extends RenderLayer {
        TestLayer() {
            super("test", BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL, GL11.GL_QUADS, 256, () -> {}, () -> {});
        }
    }

    @Test
    void retainedGroupAccumulatesInsteadOfStreaming() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 5, null);

        assertTrue(groups.hasDraws(layer));
        assertEquals(0, groups.streamedInstances);
        final List<RenderLayer> order = BufferSourceProbe.prepare(source);
        assertTrue(order.contains(layer), "declareUse must register the layer");
        assertNull(BufferSourceProbe.segmentsFor(source, layer), "retained instances must not produce dynamic segments");
    }

    @Test
    void streamingGroupStreamsIntoDynamicSegments() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        assertEquals(1, groups.streamPromotions);

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 5, null);
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(4f, 5f, 6f), 0, -1, 5, null);

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
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TemplateBuffer template = template();
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        long streamedFrames = 0;
        for (int frame = 0; frame < RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, TesrMaterial.CURRENT_STATE, fixed, 0, -1, 5, null);
            if (groups.hasDraws(layer)) break;
            streamedFrames++;
        }

        assertTrue(groups.hasDraws(layer), "identical frames must demote the group back to retained");
        assertTrue(streamedFrames >= RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES, "demotion must wait for the stability window, streamed " + streamedFrames);
    }

    @Test
    void movingTransformsStayStreaming() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TemplateBuffer template = template();

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        for (int frame = 0; frame < RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f().translation(frame, 0f, 0f), 0, -1, 5, null);
            assertFalse(groups.hasDraws(layer), "moving group must keep streaming at frame " + frame);
        }
    }

    @Test
    void idleGapResetsVolatility() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();

        groups.forceStreaming(layer, TesrMaterial.CURRENT_STATE, 5);
        for (long i = 0; i <= RetainedTesrGroups.IDLE_RESET_FRAMES + 1; i++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
        }
        groups.queue(template(), layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 5, null);

        assertTrue(groups.hasDraws(layer), "idle reset must demote the group back to retained");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void translucentStreamingGroupNeverDemotes() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial translucent = TesrMaterial.builder().translucent().build();
        final TemplateBuffer template = template();
        final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

        groups.forceStreaming(layer, translucent, 5);
        final int frames = RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4;
        for (int frame = 0; frame < frames; frame++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
            groups.queue(template, layer, translucent, fixed, 0, -1, 5, null);
            assertFalse(groups.hasDraws(layer), "translucent group must keep streaming at frame " + frame);
        }
        assertEquals(frames, groups.streamedInstances);
    }

    @Test
    void streamMaterialStreamsFromFirstQueue() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, stream, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 5, null);

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
            final AngelicaBufferSource source = new AngelicaBufferSource();
            final RetainedTesrGroups groups = new RetainedTesrGroups(source);
            final TestLayer layer = new TestLayer();
            final TemplateBuffer template = template();
            final Matrix4f fixed = new Matrix4f().translation(1f, 2f, 3f);

            final int frames = RetainedTesrGroups.DEMOTE_AFTER_STABLE_FRAMES + 4;
            for (int frame = 0; frame < frames; frame++) {
                groups.beginPass(new Matrix4f(), 0, 0, 0);
                groups.queue(template, layer, material, fixed, 0, -1, 5, null);
                assertFalse(groups.hasDraws(layer), material.transparency() + " stream group must keep streaming at frame " + frame);
            }
            assertEquals(frames, groups.streamedInstances);
        }
    }

    @Test
    void idleGapKeepsStreamGroupStreaming() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 5, null);
        for (long i = 0; i <= RetainedTesrGroups.IDLE_RESET_FRAMES + 1; i++) {
            groups.beginPass(new Matrix4f(), 0, 0, 0);
        }
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 5, null);

        assertFalse(groups.hasDraws(layer), "idle reset must not move a stream group to retained");
        assertEquals(2, groups.streamedInstances);
    }

    @Test
    void streamGroupRoutesToInstanceArraysWhenInstancedCapable() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer());
        groups.queue(template(), layer, stream, new Matrix4f().translation(1f, 2f, 3f), 0, -1, 5, null);

        assertTrue(groups.hasDraws(layer), "instanced-capable stream group accumulates for the hook draw");
        assertEquals(0, groups.streamedInstances, "no segment streaming when instanced");
        BufferSourceProbe.prepare(source);
        assertNull(BufferSourceProbe.segmentsFor(source, layer));
    }

    @Test
    void streamGroupTexMatrixFallsBackToSegmentStream() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();

        groups.beginPass(new Matrix4f(), 0, 0, 0, new InstancedTemplateRenderer());
        groups.queue(template(), layer, stream, new Matrix4f(), 0, -1, 5, new Matrix4f().translation(0.5f, 0.5f, 0f));

        assertFalse(groups.hasDraws(layer), "texMatrix instance must not enter the instance arrays");
        assertEquals(1, groups.streamedInstances);
    }

    @Test
    void streamGroupDrawModeMismatchFallsBackToRetained() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TesrMaterial stream = TesrMaterial.builder().translucent().stream().build();
        final int[] data = new int[3 * VERTEX_SIZE];
        final TemplateBuffer triangles = new TemplateBuffer(data, 3, GL11.GL_TRIANGLES);

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(triangles, layer, stream, new Matrix4f(), 0, -1, 5, null);

        assertTrue(groups.hasDraws(layer), "mismatched drawMode must fall back to retained accumulation");
        assertEquals(0, groups.streamedInstances);
    }

    @Test
    void sweepEvictsIdleGroupsAndKeepsLive() {
        final long[] clock = {1_000L};
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source, () -> clock[0]);
        final TestLayer layer = new TestLayer();
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 1, null);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 2, null);
        assertEquals(2, groups.groupCount());

        clock[0] += RetainedTesrGroups.GROUP_TTL_MS + 1;
        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 1, null);
        groups.sweep(clock[0]);

        assertEquals(1, groups.groupCount(), "idle group must be evicted, live group kept");
        assertTrue(groups.hasDraws(layer), "live group still draws this frame");

        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 2, null);
        assertEquals(2, groups.groupCount(), "evicted id must be re-creatable");
    }

    @Test
    void clearEmptiesAllGroups() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RetainedTesrGroups groups = new RetainedTesrGroups(source);
        final TestLayer layer = new TestLayer();
        final TemplateBuffer template = template();

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 1, null);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 2, null);
        groups.clear();

        assertEquals(0, groups.groupCount());
        assertFalse(groups.hasDraws(layer));

        groups.beginPass(new Matrix4f(), 0, 0, 0);
        groups.queue(template, layer, TesrMaterial.CURRENT_STATE, new Matrix4f(), 0, -1, 1, null);
        assertEquals(1, groups.groupCount());
        assertTrue(groups.hasDraws(layer));
    }

    private static TemplateBuffer template() {
        final int[] data = new int[4 * VERTEX_SIZE];
        for (int v = 0; v < 4; v++) {
            final int base = v * VERTEX_SIZE;
            data[base + X_INDEX] = Float.floatToRawIntBits(v);
            data[base + COLOR_INDEX] = 0xFFFFFFFF;
        }
        return new TemplateBuffer(data, 4, GL11.GL_QUADS);
    }
}
