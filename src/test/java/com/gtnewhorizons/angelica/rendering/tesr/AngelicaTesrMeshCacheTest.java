package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache.MeshBackend;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
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
    void cleanFrameReplaysWithoutRecapture() {
        cache.renderCached(key, this::oneBucket);
        cache.renderCached(key, sink -> { throw new AssertionError("builder must not run on a clean frame"); });

        assertEquals(1, backend.beginCaptures);
        assertEquals(1, cache.version(key));
        assertEquals(2, replay.calls.size());
    }

    @Test
    void replayIsWrappedInOneScope() {
        cache.renderCached(key, sink -> {
            sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_A);
            sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, TEX_B);
        });

        assertEquals(List.of("begin", "queue", "queue", "end"), replay.events);
    }

    enum RetireTrigger { INVALIDATE, DIRTY_RECAPTURE, EVICT, CLEAR }

    @ParameterizedTest
    @EnumSource(RetireTrigger.class)
    void droppedTemplatesAreRetiredOnce(RetireTrigger trigger) {
        final TesrMeshProvider provider = new TesrMeshProvider() {
            @Override
            public Object angelica$meshKey(TileEntity te) {
                return key;
            }

            @Override
            public void angelica$build(TesrMeshSink sink, TileEntity te) {
                oneBucket(sink);
            }
        };
        cache.renderCached(key, false, provider, null);
        final TemplateBuffer captured = replay.calls.get(0).template;
        assertTrue(replay.retired.isEmpty());

        switch (trigger) {
            case INVALIDATE -> cache.invalidate(key);
            case DIRTY_RECAPTURE -> cache.renderCached(key, true, provider, null);
            case EVICT -> {
                now[0] = 1_000L + AngelicaTesrMeshCache.LRU_TIMEOUT_MS + 1;
                cache.tick();
            }
            case CLEAR -> cache.clear();
        }
        assertEquals(List.of(captured), replay.retired);

        if (trigger != RetireTrigger.DIRTY_RECAPTURE) {
            assertFalse(cache.hasEntry(key));
            cache.renderCached(key, false, provider, null);
            assertEquals(2, backend.beginCaptures);
        }

        cache.clear();
        assertEquals(1, Collections.frequency(replay.retired, captured), "a dropped template must not be retired again");
    }

    @Test
    void lruEvicts() {
        final long timeout = AngelicaTesrMeshCache.LRU_TIMEOUT_MS;
        cache.renderCached(key, this::oneBucket);

        now[0] = 1_000L + timeout;
        cache.renderCached(key, this::oneBucket);

        now[0] = 1_000L + timeout + 1;
        cache.tick();
        assertTrue(cache.hasEntry(key), "a hit refreshes the entry");

        now[0] = 1_000L + 2 * timeout + 1;
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
        final List<String> events = new ArrayList<>();
        final List<TemplateBuffer> retired = new ArrayList<>();

        @Override
        public void beginScope() {
            events.add("begin");
        }

        @Override
        public void endScope() {
            events.add("end");
        }

        @Override
        public void queue(TemplateBuffer template, ResourceLocation texture, TesrMaterial material) {
            calls.add(new Recorded(template, texture, material));
            events.add("queue");
        }

        @Override
        public void retire(TemplateBuffer template) {
            retired.add(template);
        }
    }
}
