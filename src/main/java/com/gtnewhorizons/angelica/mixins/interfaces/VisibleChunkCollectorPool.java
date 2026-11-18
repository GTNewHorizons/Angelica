package com.gtnewhorizons.angelica.mixins.interfaces;

import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;

public interface VisibleChunkCollectorPool {
    void angelica$setRecycled(ChunkRenderList[] slot);
}
