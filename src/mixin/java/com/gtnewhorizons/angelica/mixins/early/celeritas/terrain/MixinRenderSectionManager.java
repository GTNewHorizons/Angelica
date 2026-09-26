package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.mixins.interfaces.RenderSectionManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinRenderSectionManager implements RenderSectionManagerAccessor {

    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow @Final private ConcurrentLinkedDeque<Runnable> asyncSubmittedTasks;

    @Shadow @Final private ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> buildResults;

    @Override
    public Long2ReferenceMap<RenderSection> angelica$getSectionByPosition() {
        return sectionByPosition;
    }

    @Override
    public ConcurrentLinkedDeque<Runnable> angelica$getAsyncSubmittedTasks() {
        return asyncSubmittedTasks;
    }

    @Override
    public ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> angelica$getBuildResults() {
        return buildResults;
    }
}
