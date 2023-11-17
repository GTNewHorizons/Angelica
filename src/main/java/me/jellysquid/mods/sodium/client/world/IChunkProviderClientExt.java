package me.jellysquid.mods.sodium.client.world;

public interface IChunkProviderClientExt {
    // TODO: allow multiple listeners to be added?
    void setListener(ChunkStatusListener listener);
    void doPostChunk(int x, int z);
}
