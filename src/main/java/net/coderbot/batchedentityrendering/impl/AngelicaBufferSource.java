package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.batchedentityrendering.impl.ordering.SimpleRenderOrderManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Collects geometry into per-RenderLayer buffers during a pass and draws it all at endBatch, one state bracket per layer. Based on Iris's FullyBufferedMultiBufferSource */
public class AngelicaBufferSource implements Groupable {
    public static final int SAVED_STATE_BITS = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT;

    private static final int NUM_BUFFERS = 32;

    public interface LayerDrawHook {
        boolean hasDraws(RenderLayer layer);
        void drawLayer(RenderLayer layer);
    }

    private static final Comparator<BufferSegment> OPAQUE_ORDER = Comparator.comparingInt(BufferSegment::getBlockEntityId);

    private final SimpleRenderOrderManager renderOrderManager = new SimpleRenderOrderManager();
    private final SegmentedBufferBuilder[] builders = new SegmentedBufferBuilder[NUM_BUFFERS];
    private final Object2IntLinkedOpenHashMap<RenderLayer> affinities = new Object2IntLinkedOpenHashMap<>(NUM_BUFFERS);
    private final Map<RenderLayer, List<BufferSegment>> typeToSegment = new Object2ObjectOpenHashMap<>();

    private boolean prepared;
    private final List<RenderLayer> order = new ArrayList<>();
    private boolean stateSaved;
    private boolean anyIdSet;

    public AngelicaBufferSource() {
        for (int i = 0; i < builders.length; i++) {
            builders[i] = new SegmentedBufferBuilder();
        }
        affinities.defaultReturnValue(-1);
    }

    public SegmentedBufferBuilder getBuffer(RenderLayer type, int blockEntityId) {
        renderOrderManager.begin(type);
        int affinity = affinities.getAndMoveToLast(type);
        if (affinity == -1) {
            if (affinities.size() < builders.length) {
                affinity = affinities.size();
            } else {
                affinity = affinities.removeFirstInt();
            }
            affinities.put(type, affinity);
        }
        final SegmentedBufferBuilder builder = builders[affinity];
        builder.begin(type, blockEntityId);
        return builder;
    }

    public void declareUse(RenderLayer type) {
        renderOrderManager.begin(type);
    }

    public boolean isEmpty() {
        for (SegmentedBufferBuilder builder : builders) {
            if (!builder.isEmpty()) return false;
        }
        return true;
    }

    private void ensurePrepared() {
        if (prepared) return;
        prepared = true;
        typeToSegment.clear();
        for (SegmentedBufferBuilder builder : builders) {
            final List<BufferSegment> builderSegments = builder.getSegments();
            for (int i = 0, n = builderSegments.size(); i < n; i++) {
                final BufferSegment segment = builderSegments.get(i);
                typeToSegment.computeIfAbsent(segment.getRenderType(), t -> new ArrayList<>()).add(segment);
            }
        }
        order.clear();
        final List<RenderLayer> renderOrder = renderOrderManager.getRenderOrder();
        for (int i = 0, n = renderOrder.size(); i < n; i++) {
            order.add(renderOrder.get(i));
        }
        renderOrderManager.reset();
        for (int i = 0, n = order.size(); i < n; i++) {
            final RenderLayer layer = order.get(i);
            if (getTransparencyType(layer) != TransparencyType.OPAQUE) continue;
            final List<BufferSegment> segments = typeToSegment.get(layer);
            if (segments != null) {
                segments.sort(OPAQUE_ORDER);
            }
        }
        affinities.clear();
        stateSaved = false;
        anyIdSet = false;
    }

    List<RenderLayer> prepare() {
        ensurePrepared();
        return new ArrayList<>(order);
    }

    List<BufferSegment> segmentsFor(RenderLayer layer) {
        return typeToSegment.get(layer);
    }

    public void endBatchWithType(TransparencyType type, LayerDrawHook hook) {
        ensurePrepared();
        int keep = 0;
        for (int i = 0, n = order.size(); i < n; i++) {
            final RenderLayer layer = order.get(i);
            if (getTransparencyType(layer) != type) {
                order.set(keep++, layer);
                continue;
            }
            drawLayer(layer, hook);
        }
        for (int i = order.size() - 1; i >= keep; i--) {
            order.remove(i);
        }
    }

    public void endBatch(LayerDrawHook hook) {
        ensurePrepared();
        for (int i = 0, n = order.size(); i < n; i++) {
            drawLayer(order.get(i), hook);
        }
        order.clear();
        finish();
    }

    private void drawLayer(RenderLayer layer, LayerDrawHook hook) {
        final List<BufferSegment> segments = typeToSegment.get(layer);
        final boolean hasDynamic = segments != null && !segments.isEmpty();
        final boolean hasRetained = hook != null && hook.hasDraws(layer);
        if (!hasDynamic && !hasRetained) return;
        if (!stateSaved) {
            stateSaved = true;
            GLStateManager.glPushAttrib(SAVED_STATE_BITS);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
        layer.startDrawing();
        if (hasDynamic) {
            anyIdSet = true;
            GLStateManager.glPushMatrix();
            GLStateManager.glLoadIdentity();
            SegmentedBufferBuilder.LayerBuffer bound = null;
            int currentId = Integer.MIN_VALUE;
            for (int i = 0, n = segments.size(); i < n; i++) {
                final BufferSegment segment = segments.get(i);
                if (segment.getBlockEntityId() != currentId) {
                    currentId = segment.getBlockEntityId();
                    setBlockEntityAndRebind(currentId);
                }
                final SegmentedBufferBuilder.LayerBuffer owner = segment.getOwner();
                if (owner != bound) {
                    if (bound != null) bound.finishDraw();
                    bound = owner;
                    bound.uploadForDraw();
                    bound.setupDraw();
                }
                bound.draw(segment.getFirstVertex(), segment.getVertexCount());
            }
            if (bound != null) bound.finishDraw();
            GLStateManager.glPopMatrix();
        }
        if (hasRetained) {
            anyIdSet = true;
            hook.drawLayer(layer);
        }
        layer.endDrawing();
    }

    private void finish() {
        if (stateSaved) {
            GLStateManager.glPopAttrib();
        }
        if (anyIdSet) {
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        }
        typeToSegment.clear();
        final long now = System.currentTimeMillis();
        final long maxIdle = getTargetClearTime();
        for (SegmentedBufferBuilder builder : builders) {
            builder.resetAndReclaim(now, maxIdle);
        }
        prepared = false;
        stateSaved = false;
        anyIdSet = false;
    }

    public void freeBuffers() {
        for (SegmentedBufferBuilder builder : builders) {
            builder.freeAll();
        }
        typeToSegment.clear();
        order.clear();
        affinities.clear();
        renderOrderManager.reset();
        prepared = false;
    }

    public long allocatedBytes() {
        long bytes = 0;
        for (SegmentedBufferBuilder builder : builders) {
            bytes += builder.allocatedBytes();
        }
        return bytes;
    }

    private long getTargetClearTime() {
        final long sizeInMiB = allocatedBytes() / 1024L / 1024L;
        if (sizeInMiB > 5000) {
            return 1_000;
        } else if (sizeInMiB > 1000) {
            return 5_000;
        }
        return 10_000;
    }

    public static void setBlockEntityAndRebind(int blockEntityId) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(blockEntityId);
        if (Iris.enabled) {
            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.rebindCurrentPass();
            }
        }
    }

    static TransparencyType getTransparencyType(RenderLayer type) {
        while (type instanceof WrappableRenderType wrappable) {
            type = wrappable.unwrap();
        }
        if (type instanceof BlendingStateHolder holder) {
            return holder.getTransparencyType();
        }
        return TransparencyType.GENERAL_TRANSPARENT;
    }

    @Override
    public void startGroup() {
        renderOrderManager.startGroup();
    }

    @Override
    public boolean maybeStartGroup() {
        return renderOrderManager.maybeStartGroup();
    }

    @Override
    public void endGroup() {
        renderOrderManager.endGroup();
    }
}
