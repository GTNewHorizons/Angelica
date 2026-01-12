package me.jellysquid.mods.sodium.client.render.chunk.map;

import com.gtnewhorizons.angelica.compat.ModStatus;

public class ChunkTracker {

    private static volatile IChunkTracker tracker;

    public synchronized static IChunkTracker getTracker() {
        if (tracker == null) {
            if (ModStatus.isCubicChunksLoaded) {
                tracker = new CubicChunkTracker();
            } else {
                tracker = new VanillaChunkTracker();
            }
        }

        return tracker;
    }

}
