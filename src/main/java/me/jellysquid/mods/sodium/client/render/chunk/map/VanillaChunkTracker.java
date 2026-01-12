package me.jellysquid.mods.sodium.client.render.chunk.map;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.util.ForgeDirection;

import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;

public class VanillaChunkTracker implements IChunkTracker {

    private final LongOpenHashSet chunkPresence = new LongOpenHashSet();

    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();

    private final Long2ObjectOpenHashMap<State> chunkStateChanges = new Long2ObjectOpenHashMap<>();

    @Override
    public void onChunkAdded(int x, int z) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x, z);

        if (chunkPresence.add(key)) {
            this.updateChunkNeighbors(x, z);
        }
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x, z);

        if (chunkPresence.remove(key)) {
            this.updateChunkNeighbors(x, z);
        }
    }

    @Override
    public void onCubeAdded(int x, int y, int z) {
        throw new UnsupportedOperationException("Cubes should never be added to the vanilla tracker");
    }

    @Override
    public void onCubeRemoved(int x, int y, int z) {
        throw new UnsupportedOperationException("Cubes should never be removed from the vanilla tracker");
    }

    private void updateChunkNeighbors(int x, int z) {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            this.updateChunksMerged(x + dir.offsetX, z + dir.offsetZ);
        }
    }

    private boolean isChunkPresent(int chunkX, int chunkZ) {
        return chunkPresence.contains(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
    }

    private void updateChunksMerged(int x, int z) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x, z);

        boolean canRender = isChunkPresent(x, z);

        if (canRender) {
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (!isChunkPresent(x + dir.offsetX, z + dir.offsetZ)) {
                    canRender = false;
                    break;
                }
            }
        }

        boolean isRendering = loadedChunks.contains(key);

        if (isRendering != canRender) {
            if (canRender) {
                chunkStateChanges.put(key, State.LOAD_PENDING);
            } else {
                chunkStateChanges.put(key, State.UNLOAD_PENDING);
            }
        }
    }

    public void forAllReady(ChunkStatusListener listener) {
        loadedChunks.forEach((long k) -> {
            int x = unpackChunkX(k);
            int z = unpackChunkZ(k);

            listener.onChunkAdded(x, z);
        });
    }

    public void forEachEvent(ChunkStatusListener listener) {
        Long2ObjectMaps.fastForEach(chunkStateChanges, e -> {
            int x = unpackChunkX(e.getLongKey());
            int z = unpackChunkZ(e.getLongKey());

            if (e.getValue() == State.LOAD_PENDING) {
                loadedChunks.add(e.getLongKey());
                listener.onChunkAdded(x, z);
            } else {
                loadedChunks.remove(e.getLongKey());
                listener.onChunkRemoved(x, z);
            }
        });

        chunkStateChanges.clear();
    }

    private static int unpackChunkZ(long pos) {
        return (int) ((pos >> 32L) & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long pos) {
        return (int) (pos & 0xFFFFFFFFL);
    }
}
