package com.gtnewhorizons.angelica.mixins.interfaces;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;

import java.util.concurrent.ConcurrentLinkedDeque;

public interface RenderSectionManagerAccessor {
    ConcurrentLinkedDeque<Runnable> angelica$getAsyncSubmittedTasks();
    ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> angelica$getBuildResults();
}
