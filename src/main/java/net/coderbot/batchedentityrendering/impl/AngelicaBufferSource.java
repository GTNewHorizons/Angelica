package net.coderbot.batchedentityrendering.impl;

import com.gtnewhorizons.angelica.rendering.RenderFailures;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.batchedentityrendering.impl.ordering.SimpleRenderOrderManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Collects geometry into per-RenderLayer buffers during a pass and draws it all at endBatch, one state bracket per layer. Based on Iris's FullyBufferedMultiBufferSource */
public class AngelicaBufferSource implements Groupable {
    private static final int NUM_BUFFERS = 32;

    public enum GroupIdKind { BLOCK_ENTITY, ENTITY }

    public interface LayerDrawHook {
        boolean hasDraws(RenderLayer layer);
        void drawLayer(RenderLayer layer);
    }

    private static final Comparator<BufferSegment> OPAQUE_ORDER = Comparator.comparingInt(BufferSegment::getBlockEntityId);

    private final SimpleRenderOrderManager renderOrderManager = new SimpleRenderOrderManager();
    private final SegmentedBufferBuilder[] builders = new SegmentedBufferBuilder[NUM_BUFFERS];
    private final Object2IntLinkedOpenHashMap<RenderLayer> affinities = new Object2IntLinkedOpenHashMap<>(NUM_BUFFERS);
    private final Map<RenderLayer, List<BufferSegment>> typeToSegment = new Object2ObjectOpenHashMap<>();
    private final ObjectArrayList<List<BufferSegment>> segmentLists = new ObjectArrayList<>();

    private boolean prepared;
    private final List<RenderLayer> order = new ArrayList<>();
    private int drawStateDepth = -1;
    private boolean anyIdSet;
    private GroupIdKind idKind = GroupIdKind.BLOCK_ENTITY;

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
        clearSegmentLists();
        for (SegmentedBufferBuilder builder : builders) {
            final List<BufferSegment> builderSegments = builder.getSegments();
            for (int i = 0, n = builderSegments.size(); i < n; i++) {
                final BufferSegment segment = builderSegments.get(i);
                List<BufferSegment> segments = typeToSegment.get(segment.getRenderType());
                if (segments == null) {
                    segments = new ArrayList<>();
                    typeToSegment.put(segment.getRenderType(), segments);
                    segmentLists.add(segments);
                }
                segments.add(segment);
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
        try {
            for (int i = 0, n = order.size(); i < n; i++) {
                final RenderLayer layer = order.get(i);
                if (getTransparencyType(layer) != type) {
                    order.set(keep++, layer);
                    continue;
                }
                drawLayer(layer, hook);
            }
        } catch (Throwable t) {
            discardAfterFailure(t);
            throw t;
        }
        for (int i = order.size() - 1; i >= keep; i--) {
            order.remove(i);
        }
    }

    public void endBatch(LayerDrawHook hook) {
        ensurePrepared();
        try {
            for (int i = 0, n = order.size(); i < n; i++) {
                drawLayer(order.get(i), hook);
            }
        } catch (Throwable t) {
            discardAfterFailure(t);
            throw t;
        }
        order.clear();
        finish();
    }

    public void pauseBatch() {
        restoreDrawState();
        clearCurrentId();
    }

    public void discard() {
        order.clear();
        finish();
    }

    private void restoreDrawState() {
        if (drawStateDepth >= 0) {
            GLStateManager.popStateTo(drawStateDepth);
            drawStateDepth = -1;
        }
    }

    private void clearCurrentId() {
        if (!anyIdSet) return;
        // A pass can set either kind: nested entities inside a TESR write the entity id during a block entity pass
        CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(-1, 0);
        CapturedRenderingState.INSTANCE.setCurrentEntityColor(0f, 0f, 0f, 0f);
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        anyIdSet = false;
    }

    public GroupIdKind effectiveIdKind() {
        return GbufferPrograms.getCurrentPhase() == WorldRenderingPhase.ENTITIES ? GroupIdKind.ENTITY : idKind;
    }

    private void drawLayer(RenderLayer layer, LayerDrawHook hook) {
        final List<BufferSegment> segments = typeToSegment.get(layer);
        final boolean hasDynamic = segments != null && !segments.isEmpty();
        final boolean hasRetained = hook != null && hook.hasDraws(layer);
        if (!hasDynamic && !hasRetained) return;
        if (drawStateDepth < 0) {
            drawStateDepth = GLStateManager.pushState(StateSet.BATCH);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        }
        layer.startDrawing();
        Throwable failure = null;
        try {
            if (hasDynamic) {
                drawDynamic(segments);
            }
            if (hasRetained) {
                anyIdSet = true;
                hook.drawLayer(layer);
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            try {
                layer.endDrawing();
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            }
        }
        if (failure != null) RenderFailures.rethrow(failure);
    }

    private void drawDynamic(List<BufferSegment> segments) {
        anyIdSet = true;
        boolean matrixPushed = false;
        SegmentedBufferBuilder.LayerBuffer bound = null;
        boolean boundSetup = false;
        Throwable failure = null;
        try {
            GLStateManager.glPushMatrix();
            matrixPushed = true;
            GLStateManager.glLoadIdentity();
            final boolean entityKind = effectiveIdKind() == GroupIdKind.ENTITY;
            int currentId = Integer.MIN_VALUE;
            int currentColor = 0;
            boolean colorSet = false;
            for (int i = 0, n = segments.size(); i < n; i++) {
                final BufferSegment segment = segments.get(i);
                if (entityKind && (!colorSet || segment.getEntityColor() != currentColor)) {
                    colorSet = true;
                    currentColor = segment.getEntityColor();
                    setEntityColor(currentColor);
                }
                if (segment.getBlockEntityId() != currentId) {
                    currentId = segment.getBlockEntityId();
                    applyIdAndRebind(currentId);
                }
                final SegmentedBufferBuilder.LayerBuffer owner = segment.getOwner();
                if (owner != bound) {
                    if (boundSetup) {
                        bound.finishDraw();
                        boundSetup = false;
                    }
                    bound = null;
                    owner.uploadForDraw();
                    owner.setupDraw();
                    bound = owner;
                    boundSetup = true;
                }
                bound.draw(segment.getFirstVertex(), segment.getVertexCount());
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            if (boundSetup) {
                try {
                    bound.finishDraw();
                } catch (Throwable cleanup) {
                    failure = RenderFailures.suppress(failure, cleanup);
                }
            }
            if (matrixPushed) {
                try {
                    GLStateManager.glPopMatrix();
                } catch (Throwable cleanup) {
                    failure = RenderFailures.suppress(failure, cleanup);
                }
            }
        }
        if (failure != null) RenderFailures.rethrow(failure);
    }

    private void clearSegmentLists() {
        for (int i = 0, n = segmentLists.size(); i < n; i++) {
            segmentLists.get(i).clear();
        }
    }

    private void finish() {
        final Throwable failure = cleanupAfterBatch(null);
        if (failure != null) RenderFailures.rethrow(failure);
    }

    private Throwable cleanupAfterBatch(Throwable failure) {
        try {
            restoreDrawState();
        } catch (Throwable t) {
            failure = RenderFailures.suppress(failure, t);
        }
        try {
            clearCurrentId();
        } catch (Throwable t) {
            failure = RenderFailures.suppress(failure, t);
        }
        try {
            clearSegmentLists();
        } catch (Throwable t) {
            failure = RenderFailures.suppress(failure, t);
        }
        final long now = System.currentTimeMillis();
        final long maxIdle = getTargetClearTime();
        for (int i = 0; i < builders.length; i++) {
            try {
                builders[i].resetAndReclaim(now, maxIdle);
            } catch (Throwable t) {
                failure = RenderFailures.suppress(failure, t);
            }
        }
        affinities.clear();
        renderOrderManager.reset();
        prepared = false;
        return failure;
    }

    private void discardAfterFailure(Throwable failure) {
        order.clear();
        cleanupAfterBatch(failure);
    }

    public void freeBuffers() {
        for (SegmentedBufferBuilder builder : builders) {
            builder.freeAll();
        }
        typeToSegment.clear();
        segmentLists.clear();
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

    public void setIdKind(GroupIdKind kind) {
        this.idKind = kind;
    }

    public void applyIdAndRebind(int id) {
        anyIdSet = true;
        if (effectiveIdKind() == GroupIdKind.ENTITY) {
            CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(id, 0);
            rebindPass();
        } else {
            setBlockEntityAndRebind(id);
        }
    }

    public static int packEntityColor(Vector4fc c) {
        return packAbgr(c.x(), c.y(), c.z(), c.w());
    }

    public static int packAbgr(float r, float g, float b, float a) {
        return (unit(a) << 24) | (unit(b) << 16) | (unit(g) << 8) | unit(r);
    }

    private static int unit(float v) {
        return (int) (Math.clamp(v, 0f, 1f) * 255f + 0.5f);
    }

    public static void setEntityColor(int abgr) {
        CapturedRenderingState.INSTANCE.setCurrentEntityColor((abgr & 0xFF) / 255f, ((abgr >>> 8) & 0xFF) / 255f, ((abgr >>> 16) & 0xFF) / 255f, (abgr >>> 24) / 255f);
    }

    public static void setBlockEntityAndRebind(int blockEntityId) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(blockEntityId);
        rebindPass();
    }

    public static void rebindPass() {
        if (Iris.enabled) {
            final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline != null) {
                pipeline.rebindCurrentPass();
            }
        }
    }

    static TransparencyType getTransparencyType(RenderLayer type) {
        return type.getTransparencyType();
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
