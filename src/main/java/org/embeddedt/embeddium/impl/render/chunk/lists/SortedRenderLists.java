package org.embeddedt.embeddium.impl.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.util.iterator.ReversibleObjectArrayIterator;

import java.util.Set;

public class SortedRenderLists implements ChunkRenderListIterable {
    private static final SortedRenderLists EMPTY = new SortedRenderLists(new ObjectArrayList<>());

    private final ObjectArrayList<ChunkRenderList> lists;
    private final ReferenceOpenHashSet<TerrainRenderPass> passes;

    SortedRenderLists(ObjectArrayList<ChunkRenderList> lists) {
        this.lists = lists;
        this.passes = getAllPassesInLists(lists);
    }

    private static ReferenceOpenHashSet<TerrainRenderPass> getAllPassesInLists(ObjectArrayList<ChunkRenderList> lists) {
        ReferenceOpenHashSet<TerrainRenderPass> usedPasses = new ReferenceOpenHashSet<>();

        for (var list : lists) {
            usedPasses.addAll(list.getRegion().getPasses());
        }

        return usedPasses;
    }

    @Override
    public ReversibleObjectArrayIterator<ChunkRenderList> iterator(boolean reverse) {
        return new ReversibleObjectArrayIterator<>(this.lists, reverse);
    }

    @Override
    public boolean hasPass(TerrainRenderPass pass) {
        return this.passes.contains(pass);
    }

    public Set<TerrainRenderPass> getPasses() {
        return this.passes;
    }

    public static SortedRenderLists empty() {
        return EMPTY;
    }
}
