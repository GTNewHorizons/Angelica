package me.jellysquid.mods.sodium.client.render.chunk.map;

import com.gtnewhorizons.angelica.compat.ModStatus;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;

public interface IChunkTracker extends ClientChunkEventListener {

    void forAllReady(ChunkStatusListener listener);
    void forEachEvent(ChunkStatusListener listener);

    static IChunkTracker newInstance() {
        if (ModStatus.isCubicChunksLoaded) {
            return new CubicChunkTracker();
        } else {
            return new VanillaChunkTracker();
        }
    }
}
