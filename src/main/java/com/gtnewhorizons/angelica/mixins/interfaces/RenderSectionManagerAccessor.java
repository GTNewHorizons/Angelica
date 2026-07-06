package com.gtnewhorizons.angelica.mixins.interfaces;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

import java.util.concurrent.ConcurrentLinkedDeque;

public interface RenderSectionManagerAccessor {
    Long2ReferenceMap<RenderSection> angelica$getSectionByPosition();
    ConcurrentLinkedDeque<Runnable> angelica$getAsyncSubmittedTasks();
}
