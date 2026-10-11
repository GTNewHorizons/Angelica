package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelicaBufferSourceTest {

    private static final ModelQuad QUAD = BufferSourceProbe.quad();

    @Test
    void sameLayerKeepsBuilderAffinity() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer layer = BufferSourceProbe.layer("a", TransparencyType.OPAQUE);
        final SegmentedBufferBuilder first = source.getBuffer(layer, 1);
        first.addQuad(QUAD);
        final SegmentedBufferBuilder second = source.getBuffer(layer, 2);
        assertSame(first, second);
    }

    @Test
    void prepareGroupsSegmentsByLayerAndSortsOpaqueById() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer opaque = BufferSourceProbe.layer("opaque", TransparencyType.OPAQUE);
        final RenderLayer translucent = BufferSourceProbe.layer("translucent", TransparencyType.GENERAL_TRANSPARENT);

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
            source.getBuffer(BufferSourceProbe.layer("layer" + i, TransparencyType.OPAQUE), i).addQuad(QUAD);
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
        final RenderLayer opaqueA = BufferSourceProbe.layer("oa", TransparencyType.OPAQUE);
        final RenderLayer translucent = BufferSourceProbe.layer("t", TransparencyType.GENERAL_TRANSPARENT);
        final RenderLayer opaqueB = BufferSourceProbe.layer("ob", TransparencyType.OPAQUE);
        source.declareUse(opaqueA);
        source.declareUse(translucent);
        source.declareUse(opaqueB);

        source.endBatchWithType(TransparencyType.OPAQUE, null);

        final List<RenderLayer> remaining = source.prepare();
        assertEquals(1, remaining.size());
        assertSame(translucent, remaining.get(0));
    }

    @Test
    void packAbgrPacksRedGreenBlueAlpha() {
        assertEquals(0xFF0000FF, AngelicaBufferSource.packEntityColor(new Vector4f(1f, 0f, 0f, 1f)));
        final int packed = AngelicaBufferSource.packEntityColor(new Vector4f(0f, 0.5f, 0f, 0f));
        assertEquals(0, packed & 0xFF0000FF, "red, blue and alpha bytes must be zero");
        assertTrue((packed & 0x0000FF00) != 0, "green byte must be nonzero");
    }

    @Test
    void declaredLayersAppearInOrderWithoutSegments() {
        final AngelicaBufferSource source = new AngelicaBufferSource();
        final RenderLayer retainedOnly = BufferSourceProbe.layer("retained", TransparencyType.OPAQUE);
        source.declareUse(retainedOnly);
        final List<RenderLayer> order = source.prepare();
        assertEquals(1, order.size());
        assertSame(retainedOnly, order.get(0));
        assertTrue(source.isEmpty());
    }
}
