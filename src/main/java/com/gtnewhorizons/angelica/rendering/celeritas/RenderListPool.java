package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.mixins.interfaces.ChunkRenderListReuse;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

import java.util.Arrays;

// Two slots are enough only while submitSearch rejects a second in-flight search per manager
public final class RenderListPool {
    private final ChunkRenderList[][] slots = { new ChunkRenderList[0], new ChunkRenderList[0] };
    private int generation;

    public ChunkRenderList[] beginSearch(int regionIdsLength) {
        final int s = generation++ & 1;
        ChunkRenderList[] slot = slots[s];
        if (slot.length < regionIdsLength) {
            slot = slots[s] = Arrays.copyOf(slot, regionIdsLength);
        }
        return slot;
    }

    public static ChunkRenderList acquire(ChunkRenderList[] slot, RenderRegion region) {
        final int id = region.getId();
        final ChunkRenderList list = slot[id];
        if (list != null && list.getRegion() == region) {
            ((ChunkRenderListReuse) list).angelica$reset();
            return list;
        }
        return slot[id] = new ChunkRenderList(region);
    }
}
