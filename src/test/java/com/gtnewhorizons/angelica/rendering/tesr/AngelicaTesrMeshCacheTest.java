package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache.MeshBackend;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AngelicaTesrMeshCacheTest {

    private static final ResourceLocation TEX_A = new ResourceLocation("angelica", "test_a");
    private static final ResourceLocation TEX_B = new ResourceLocation("angelica", "test_b");

    private final long[] now = { 1_000L };
    private final LongSupplier clock = () -> now[0];
    private final FakeBackend backend = new FakeBackend();
    private final RecordingReplay replay = new RecordingReplay();
    private final AngelicaTesrMeshCache cache = new AngelicaTesrMeshCache(backend, clock, replay);
    private final Object key = new Object();

    @Test
    void firstFrameCapturesAndReplays() {
        cache.renderCached(key, sink -> sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_A));

        assertEquals(1, backend.beginCaptures);
        assertTrue(cache.hasEntry(key));
        assertEquals(1, cache.version(key));
        assertEquals(1, replay.calls.size());
        assertSame(TEX_A, replay.calls.get(0).texture);
    }

    @Test
    void cleanFrameReplaysWithoutRecapture() {
        cache.renderCached(key, this::oneBucket);
        cache.renderCached(key, sink -> { throw new AssertionError("builder must not run on a clean frame"); });

        assertEquals(1, backend.beginCaptures);
        assertEquals(1, cache.version(key));
        assertEquals(2, replay.calls.size());
    }

    @Test
    void invalidateForcesRecapture() {
        cache.renderCached(key, this::oneBucket);
        cache.invalidate(key);
        assertFalse(cache.hasEntry(key));

        cache.renderCached(key, this::oneBucket);
        assertEquals(2, backend.beginCaptures);
    }

    @Test
    void lruEvicts() {
        cache.renderCached(key, this::oneBucket);

        now[0] = 1_000L + AngelicaTesrMeshCache.LRU_TIMEOUT_MS; // boundary -> retained
        cache.tick();
        assertTrue(cache.hasEntry(key));

        now[0] = 1_000L + AngelicaTesrMeshCache.LRU_TIMEOUT_MS + 1; // past timeout -> evicted
        cache.tick();
        assertFalse(cache.hasEntry(key));
    }

    @Test
    void multiBucketOrderAndMaterialPassthrough() {
        final TesrMaterial material = TesrMaterial.builder().color(0.25f, 0.5f, 0.75f, 1f).build();
        cache.renderCached(key, sink -> {
            sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_A);
            sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_B, material);
        });

        assertEquals(2, replay.calls.size());
        assertSame(TEX_A, replay.calls.get(0).texture);
        assertSame(TesrMaterial.CURRENT_STATE, replay.calls.get(0).material);
        assertSame(TEX_B, replay.calls.get(1).texture);
        assertSame(material, replay.calls.get(1).material);
    }

    private void oneBucket(TesrMeshSink sink) {
        sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_A);
    }

    private static final class FakeBackend implements MeshBackend {
        int beginCaptures;

        @Override
        public Tessellator beginCapture(VertexFormat format) {
            beginCaptures++;
            return null;
        }

        @Override
        public TemplateBuffer endCaptureToTemplate() {
            return new TemplateBuffer(new int[8], 1, 7);
        }
    }

    private record Recorded(TemplateBuffer template, ResourceLocation texture, TesrMaterial material) {}

    private static final class RecordingReplay implements AngelicaTesrMeshCache.ReplaySink {
        final List<Recorded> calls = new ArrayList<>();

        @Override
        public void queue(TemplateBuffer template, ResourceLocation texture, TesrMaterial material) {
            calls.add(new Recorded(template, texture, material));
        }
    }
}
