package me.jellysquid.mods.sodium.client.render.chunk.map;

public interface ClientChunkEventListener {

    void onChunkAdded(int x, int z);
    void onChunkRemoved(int x, int z);

    void onCubeAdded(int x, int y, int z);
    void onCubeRemoved(int x, int y, int z);
}
