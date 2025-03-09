package org.embeddedt.embeddium.impl.render.chunk.map;

public interface ChunkTrackerHolder {
    static ChunkTracker get(Object world) {
        return ((ChunkTrackerHolder) world).sodium$getTracker();
    }

    ChunkTracker sodium$getTracker();
}
