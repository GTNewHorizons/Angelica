package me.jellysquid.mods.sodium.client.render.chunk.map;

import net.minecraft.client.multiplayer.WorldClient;

public interface ChunkTrackerHolder {
    static IChunkTracker get(WorldClient world) {
        return ((ChunkTrackerHolder) world).sodium$getTracker();
    }

    IChunkTracker sodium$getTracker();
}
