package com.gtnewhorizons.angelica.mixins.interfaces;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface ChunkTrackerAccessor {
    Long2IntOpenHashMap angelica$getChunkStatus();
    LongOpenHashSet angelica$getChunkReady();
    LongSet angelica$getUnloadQueue();
    LongSet angelica$getLoadQueue();
}
