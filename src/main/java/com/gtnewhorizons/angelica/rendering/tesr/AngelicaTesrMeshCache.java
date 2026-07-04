package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.rendering.tesr.TemplateBuffer;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import com.gtnewhorizons.angelica.rendering.tesr.VertexTransform;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

public final class AngelicaTesrMeshCache {

    public static final long LRU_TIMEOUT_MS = 60_000L;

    public static final AngelicaTesrMeshCache INSTANCE = new AngelicaTesrMeshCache(new GtnhMeshBackend(), System::currentTimeMillis);
    private static final Logger LOG = LogManager.getLogger("AngelicaTesrMeshCache");

    private final MeshBackend backend;
    private final LongSupplier clock;
    private final ReplaySink replaySink;
    private final Reference2ObjectOpenHashMap<Object, Entry> entries = new Reference2ObjectOpenHashMap<>();
    private boolean warnedEmptyCapture;


    public interface MeshBackend {
        Tessellator beginCapture(VertexFormat format);

        TemplateBuffer endCaptureToTemplate();
    }

    public interface ReplaySink {
        void queue(TemplateBuffer template, ResourceLocation texture, TesrMaterial material);
    }

    private static final class Bucket {
        final ResourceLocation texture;
        final TesrMaterial material;
        final TemplateBuffer template;

        Bucket(ResourceLocation texture, TesrMaterial material, TemplateBuffer template) {
            this.texture = texture;
            this.material = material;
            this.template = template;
        }
    }

    private static final class Entry {
        final List<Bucket> buckets = new ArrayList<>();
        long version;
        long lastUsedMs;
    }

    AngelicaTesrMeshCache(MeshBackend backend, LongSupplier clock) {
        this(backend, clock, (template, texture, material) -> TesrBatchRenderer.INSTANCE.queue(template, texture, material));
    }

    AngelicaTesrMeshCache(MeshBackend backend, LongSupplier clock, ReplaySink replaySink) {
        this.backend = backend;
        this.clock = clock;
        this.replaySink = replaySink;
    }

    public void renderCached(Object key, boolean dirty, TesrMeshProvider provider, TileEntity te) {
        Entry entry = entries.get(key);
        if (entry == null || dirty) {
            entry = capture(key, entry, sink -> provider.angelica$build(sink, te));
        }
        replay(entry);
        entry.lastUsedMs = clock.getAsLong();
    }

    public void renderCached(Object key, TesrMeshBuilder builder) {
        Entry entry = entries.get(key);
        if (entry == null) {
            entry = capture(key, null, builder);
        }
        replay(entry);
        entry.lastUsedMs = clock.getAsLong();
    }

    public void invalidate(Object key) {
        final Entry entry = entries.remove(key);
        if (entry != null) {
            entry.buckets.clear();
        }
    }

    private Entry capture(Object key, Entry existing, TesrMeshBuilder builder) {
        Entry entry = existing;
        if (entry == null) {
            entry = new Entry();
            entries.put(key, entry);
        } else {
            entry.buckets.clear();
        }
        final CaptureSink sink = new CaptureSink(entry);
        builder.angelica$build(sink);
        sink.close();
        if (entry.buckets.isEmpty() && sink.openedAnyBucket && !warnedEmptyCapture) {
            warnedEmptyCapture = true;
            LOG.warn("Capture produced 0 vertices across all buckets");
        }
        entry.version++;
        return entry;
    }

    private final class CaptureSink implements TesrMeshSink {
        private final Entry entry;
        private ResourceLocation openTexture;
        private TesrMaterial openMaterial;
        private boolean open;
        boolean openedAnyBucket;

        CaptureSink(Entry entry) {
            this.entry = entry;
        }

        @Override
        public Tessellator angelica$bucket(VertexFormat format, ResourceLocation texture, TesrMaterial material) {
            close();
            openTexture = texture;
            openMaterial = material != null ? material : TesrMaterial.CURRENT_STATE;
            open = true;
            openedAnyBucket = true;
            return backend.beginCapture(format);
        }

        void close() {
            if (open) {
                final TemplateBuffer template = backend.endCaptureToTemplate();
                if (template != null) {
                    entry.buckets.add(new Bucket(openTexture, openMaterial, template));
                }
                open = false;
                openTexture = null;
                openMaterial = null;
            }
        }
    }

    private void replay(Entry entry) {
        final List<Bucket> buckets = entry.buckets;
        for (int i = 0, n = buckets.size(); i < n; i++) {
            final Bucket b = buckets.get(i);
            replaySink.queue(b.template, b.texture, b.material);
        }
    }

    public static final class ReloadListener {
        @SubscribeEvent
        public void onTextureStitchPost(TextureStitchEvent.Post event) {
            INSTANCE.clear();
        }
    }

    public void tick() {
        evict(clock.getAsLong());
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    void evict(long nowMs) {
        if (entries.isEmpty()) return;
        final ObjectIterator<Reference2ObjectMap.Entry<Object, Entry>> it = entries.reference2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            final Entry entry = it.next().getValue();
            if (entry.lastUsedMs + LRU_TIMEOUT_MS < nowMs) {
                entry.buckets.clear();
                it.remove();
            }
        }
    }

    long version(Object key) {
        final Entry entry = entries.get(key);
        return entry == null ? -1 : entry.version;
    }

    boolean hasEntry(Object key) {
        return entries.containsKey(key);
    }

    static final class GtnhMeshBackend implements MeshBackend {
        private DirectTessellator direct;

        @Override
        public Tessellator beginCapture(VertexFormat format) {
            direct = TessellatorManager.startCapturingDirect(format);
            direct.startDrawingQuads();
            direct.draw();
            return direct;
        }

        @Override
        public TemplateBuffer endCaptureToTemplate() {
            final int vertexCount = direct.getVertexCount();
            if (vertexCount == 0) {
                TessellatorManager.stopCapturingDirect();
                direct = null;
                return null;
            }
            final int drawMode = direct.getDrawMode();
            final VertexFormat format = direct.getVertexFormat();
            final ByteBuffer copy = direct.allocateBufferCopy();
            final int[] data = VertexTransform.decode(memAddress0(copy), format, vertexCount);
            memFree(copy);
            TessellatorManager.stopCapturingDirect();
            direct = null;
            return new TemplateBuffer(data, vertexCount, drawMode);
        }
    }
}
