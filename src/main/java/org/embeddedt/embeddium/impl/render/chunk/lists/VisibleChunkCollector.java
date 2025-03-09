package org.embeddedt.embeddium.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionNode;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;

public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;
    private final Reference2ReferenceOpenHashMap<RenderRegion, ChunkRenderList> renderListsByRegion;

    private final int frame;

    private final boolean ignoreQueueSizeLimit;

    public VisibleChunkCollector(int frame, boolean ignoreQueueSizeLimit) {
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);
        this.ignoreQueueSizeLimit = ignoreQueueSizeLimit;
        this.renderListsByRegion = new Reference2ReferenceOpenHashMap<>();

        for (var type : ChunkUpdateType.values()) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    private ChunkRenderList createRenderList(RenderRegion region) {
        ChunkRenderList renderList = new ChunkRenderList(region);
        this.sortedRenderLists.add(renderList);
        this.renderListsByRegion.put(region, renderList);
        return renderList;
    }

    @Override
    public void visit(OcclusionNode node, boolean visible) {
        var section = node.getRenderSection();

        // Note: even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = this.renderListsByRegion.get(region);

        if (renderList == null) {
            renderList = this.createRenderList(region);
        }

        if (visible && section.hasAnythingToRender()) {
            renderList.add(section);
        }

        this.addToRebuildLists(section);
    }

    private void addToRebuildLists(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getBuildCancellationToken() == null) {
            Queue<RenderSection> queue = this.sortedRebuildLists.get(type);

            if (this.ignoreQueueSizeLimit || queue.size() < type.getMaximumQueueSize()) {
                queue.add(section);
            }
        }
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
