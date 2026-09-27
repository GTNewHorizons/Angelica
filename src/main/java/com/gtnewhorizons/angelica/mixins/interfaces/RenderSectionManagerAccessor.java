package com.gtnewhorizons.angelica.mixins.interfaces;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;

import java.util.concurrent.ConcurrentLinkedDeque;

public interface RenderSectionManagerAccessor {
    Long2ReferenceMap<RenderSection> angelica$getSectionByPosition();
    ConcurrentLinkedDeque<Runnable> angelica$getAsyncSubmittedTasks();
    ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> angelica$getBuildResults();
}
