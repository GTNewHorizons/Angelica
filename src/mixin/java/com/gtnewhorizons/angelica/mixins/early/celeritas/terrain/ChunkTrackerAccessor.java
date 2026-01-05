package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkTracker.class)
public interface ChunkTrackerAccessor extends com.gtnewhorizons.angelica.mixins.interfaces.ChunkTrackerAccessor {
    @Accessor("chunkStatus")
    Long2IntOpenHashMap angelica$getChunkStatus();

    @Accessor("chunkReady")
    LongOpenHashSet angelica$getChunkReady();

    @Accessor("unloadQueue")
    LongSet angelica$getUnloadQueue();

    @Accessor("loadQueue")
    LongSet angelica$getLoadQueue();
}
