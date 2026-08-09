package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.ModelQuad;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.X_INDEX;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedBufferBuilderTest {

    static final class TestLayer extends RenderLayer implements BlendingStateHolder {
        private final TransparencyType transparencyType;

        TestLayer(String name, TransparencyType transparencyType) {
            super(name, BatchVertexFormats.POSITION_COLOR_TEXTURE_LIGHTF_NORMAL, GL11.GL_QUADS, 256, () -> {}, () -> {});
            this.transparencyType = transparencyType;
        }

        @Override
        public TransparencyType getTransparencyType() {
            return transparencyType;
        }
    }

    static ModelQuad quad() {
        final ModelQuad q = new ModelQuad();
        for (int i = 0; i < 4; i++) {
            q.setX(i, i);
            q.setY(i, i);
            q.setZ(i, i);
        }
        return q;
    }

    @Test
    void sameKeyContinuesSegment() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);
        builder.begin(layer, 5);
        builder.addQuad(quad());
        builder.begin(layer, 5);
        builder.addQuad(quad());
        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(1, segments.size());
        assertEquals(0, segments.get(0).getFirstVertex());
        assertEquals(8, segments.get(0).getVertexCount());
        assertEquals(5, segments.get(0).getBlockEntityId());
    }

    @Test
    void keyChangeSplitsSegments() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer a = new TestLayer("a", TransparencyType.OPAQUE);
        final TestLayer b = new TestLayer("b", TransparencyType.OPAQUE);
        builder.begin(a, 1);
        builder.addQuad(quad());
        builder.begin(a, 2);
        builder.addQuad(quad());
        builder.addQuad(quad());
        builder.begin(b, 2);
        builder.addQuad(quad());
        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(3, segments.size());
        assertEquals(0, segments.get(0).getFirstVertex());
        assertEquals(4, segments.get(0).getVertexCount());
        assertEquals(1, segments.get(0).getBlockEntityId());
        assertEquals(4, segments.get(1).getFirstVertex());
        assertEquals(8, segments.get(1).getVertexCount());
        assertEquals(2, segments.get(1).getBlockEntityId());
        assertEquals(0, segments.get(2).getFirstVertex());
        assertEquals(4, segments.get(2).getVertexCount());
        assertEquals(b, segments.get(2).getRenderType());
    }

    @Test
    void emptySegmentsAreDropped() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer a = new TestLayer("a", TransparencyType.OPAQUE);
        final TestLayer b = new TestLayer("b", TransparencyType.OPAQUE);
        builder.begin(a, 1);
        builder.begin(b, 1);
        builder.addQuad(quad());
        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(1, segments.size());
        assertEquals(b, segments.get(0).getRenderType());
    }

    @Test
    void bufferGrowsPastInitialCapacity() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);
        builder.begin(layer, 1);
        final ModelQuad q = quad();
        final int quads = 3000; // 3000 * 4 * 32B ~ 384KB > 256KB initial
        for (int i = 0; i < quads; i++) {
            builder.addQuad(q);
        }
        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(1, segments.size());
        assertEquals(quads * 4, segments.get(0).getVertexCount());
        assertEquals(quads * 4, builder.getVertexCount());
    }

    @Test
    void resetClearsFillState() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);
        builder.begin(layer, 1);
        builder.addQuad(quad());
        builder.reset();
        assertTrue(builder.isEmpty());
        assertEquals(0, builder.getSegments().size());
    }

    @Test
    void addTemplateInstanceTransformsAndExtendsSegment() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);

        builder.begin(layer, 3);
        builder.addTemplateInstance(template(), new Matrix4f().translation(10f, 20f, 30f), new Vector3f(), -1, 0, null);
        builder.addQuad(quad());

        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(1, segments.size());
        assertEquals(0, segments.get(0).getFirstVertex());
        assertEquals(8, segments.get(0).getVertexCount());
        assertEquals(3, segments.get(0).getBlockEntityId());

        final ByteBuffer buffer = segments.get(0).getOwner().buffer;
        final int stride = layer.getVertexFormat().getVertexSize();
        for (int v = 0; v < 4; v++) {
            assertEquals(10f + v, buffer.getFloat(v * stride), 1e-6f, "x of vertex " + v);
            assertEquals(20f, buffer.getFloat(v * stride + 4), 1e-6f, "y of vertex " + v);
            assertEquals(30f, buffer.getFloat(v * stride + 8), 1e-6f, "z of vertex " + v);
        }
    }

    @Test
    void reclaimFreesIdleLayerBuffers() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);
        final long t0 = 100_000L;

        builder.begin(layer, 1);
        builder.addQuad(quad());
        builder.resetAndReclaim(t0, 10_000L);
        assertEquals(1, builder.bufferCount(), "used buffer survives its own batch");

        builder.resetAndReclaim(t0 + 5_000L, 10_000L);
        assertEquals(1, builder.bufferCount(), "unused buffer survives within the idle window");

        builder.resetAndReclaim(t0 + 10_001L, 10_000L);
        assertEquals(0, builder.bufferCount(), "buffer idle past the window is freed");
    }

    @Test
    void allocatedBytesTracksBufferLifecycle() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer a = new TestLayer("a", TransparencyType.OPAQUE);
        final TestLayer b = new TestLayer("b", TransparencyType.OPAQUE);
        final long t0 = 100_000L;
        assertEquals(0L, builder.allocatedBytes());

        builder.begin(a, 1);
        builder.addQuad(quad());
        List<BufferSegment> segments = builder.getSegments();
        final SegmentedBufferBuilder.LayerBuffer aBuffer = segments.get(0).getOwner();
        final long aInitial = aBuffer.buffer.capacity();
        assertEquals(aInitial, builder.allocatedBytes(), "creation adds initial capacity");

        builder.begin(a, 1);
        final ModelQuad q = quad();
        for (int i = 0; i < 3000; i++) {
            builder.addQuad(q);
        }
        builder.begin(b, 1);
        builder.addQuad(q);
        segments = builder.getSegments();
        final SegmentedBufferBuilder.LayerBuffer bBuffer = segments.get(segments.size() - 1).getOwner();
        final long aGrown = aBuffer.buffer.capacity();
        assertTrue(aGrown > aInitial, "layer a buffer grew");
        assertEquals(aGrown + bBuffer.buffer.capacity(), builder.allocatedBytes(), "growth adds capacity delta");

        builder.resetAndReclaim(t0, 10_000L);
        assertEquals(aGrown + bBuffer.buffer.capacity(), builder.allocatedBytes(), "reclaim without eviction keeps bytes");

        builder.resetAndReclaim(t0 + 10_001L, 10_000L);
        assertEquals(0L, builder.allocatedBytes(), "eviction subtracts capacity");

        builder.begin(a, 1);
        builder.addQuad(q);
        assertEquals(aInitial, builder.allocatedBytes(), "recreation adds fresh capacity");
        builder.freeAll();
        assertEquals(0L, builder.allocatedBytes(), "freeAll zeroes");
    }

    @Test
    void evictionThenReuseKeepsStateConsistent() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer a = new TestLayer("a", TransparencyType.OPAQUE);
        final TestLayer b = new TestLayer("b", TransparencyType.OPAQUE);
        final long t0 = 100_000L;

        builder.begin(a, 1);
        builder.addQuad(quad());
        builder.begin(b, 1);
        builder.addQuad(quad());
        builder.resetAndReclaim(t0, 10_000L);
        assertEquals(2, builder.bufferCount());

        builder.resetAndReclaim(t0 + 10_001L, 10_000L);
        assertEquals(0, builder.bufferCount());
        assertEquals(0L, builder.allocatedBytes());
        assertTrue(builder.isEmpty());

        builder.begin(a, 2);
        builder.addQuad(quad());
        final List<BufferSegment> segments = builder.getSegments();
        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).getBlockEntityId());
        assertEquals(1, builder.bufferCount());
        assertTrue(builder.allocatedBytes() > 0);
    }

    @Test
    void freeAllReleasesBuffersAndStaysUsable() {
        final SegmentedBufferBuilder builder = new SegmentedBufferBuilder();
        final TestLayer layer = new TestLayer("a", TransparencyType.OPAQUE);

        builder.begin(layer, 1);
        builder.addQuad(quad());
        builder.freeAll();
        assertTrue(builder.isEmpty());
        assertEquals(0, builder.bufferCount());

        builder.begin(layer, 1);
        builder.addQuad(quad());
        assertEquals(1, builder.getSegments().size());
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
}
