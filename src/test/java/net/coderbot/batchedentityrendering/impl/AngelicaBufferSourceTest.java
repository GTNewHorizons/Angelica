package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import net.coderbot.batchedentityrendering.impl.SegmentedBufferBuilderTest.TestLayer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelicaBufferSourceTest {

    private static final ModelQuad QUAD = SegmentedBufferBuilderTest.quad();

    @Test
    void sameLayerKeepsBuilderAffinity() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);
        final SegmentedBufferBuilder first = source.getBuffer(layer, 1);
        first.addQuad(QUAD);
        final SegmentedBufferBuilder second = source.getBuffer(layer, 2);
        assertSame(first, second);
    }

    @Test
    void prepareGroupsSegmentsByLayerAndSortsOpaqueById() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final TestLayer opaque = new TestLayer("opaque", TransparencyType.OPAQUE);
        final TestLayer translucent = new TestLayer("translucent", TransparencyType.GENERAL_TRANSPARENT);

        source.getBuffer(opaque, 7).addQuad(QUAD);
        source.getBuffer(translucent, 9).addQuad(QUAD);
        source.getBuffer(opaque, 3).addQuad(QUAD);
        source.getBuffer(translucent, 2).addQuad(QUAD);

        final List<RenderLayer> order = source.prepare();
        assertEquals(2, order.size());

        assertSame(opaque, order.get(0));
        assertSame(translucent, order.get(1));

        final List<BufferSegment> opaqueSegments = source.segmentsFor(opaque);
        assertNotNull(opaqueSegments);
        assertEquals(2, opaqueSegments.size());
        assertEquals(3, opaqueSegments.get(0).getBlockEntityId());
        assertEquals(7, opaqueSegments.get(1).getBlockEntityId());

        final List<BufferSegment> translucentSegments = source.segmentsFor(translucent);
        assertEquals(2, translucentSegments.size());
        assertEquals(9, translucentSegments.get(0).getBlockEntityId());
        assertEquals(2, translucentSegments.get(1).getBlockEntityId());
    }

    @Test
    void affinityEvictionLosesNoSegments() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final int layers = 40;
        for (int i = 0; i < layers; i++) {
            source.getBuffer(new TestLayer("layer" + i, TransparencyType.OPAQUE), i).addQuad(QUAD);
        }
        final List<RenderLayer> order = source.prepare();
        assertEquals(layers, order.size());
        int segments = 0;
        for (RenderLayer layer : order) {
            segments += source.segmentsFor(layer).size();
        }
        assertEquals(layers, segments);
    }

    @Test
    void endBatchWithTypeCompactsOrder() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final TestLayer opaqueA = new TestLayer("oa", TransparencyType.OPAQUE);
        final TestLayer translucent = new TestLayer("t", TransparencyType.GENERAL_TRANSPARENT);
        final TestLayer opaqueB = new TestLayer("ob", TransparencyType.OPAQUE);
        source.declareUse(opaqueA);
        source.declareUse(translucent);
        source.declareUse(opaqueB);

        source.endBatchWithType(TransparencyType.OPAQUE, null);

        final List<RenderLayer> remaining = source.prepare();
        assertEquals(1, remaining.size());
        assertSame(translucent, remaining.get(0));
    }

    @Test
    void declaredLayersAppearInOrderWithoutSegments() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final TestLayer retainedOnly = new TestLayer("retained", TransparencyType.OPAQUE);
        source.declareUse(retainedOnly);
        final List<RenderLayer> order = source.prepare();
        assertEquals(1, order.size());
        assertSame(retainedOnly, order.get(0));
        assertTrue(source.isEmpty());
    }
}
