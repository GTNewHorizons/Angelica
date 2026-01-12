package me.jellysquid.mods.sodium.client.render.chunk.map;

import net.minecraft.world.ChunkCoordIntPair;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;

public class CubicChunkTracker implements IChunkTracker {

    private final LongOpenHashSet chunkPresence = new LongOpenHashSet();
    private final LongOpenHashSet cubePresence = new LongOpenHashSet();

    private final LongOpenHashSet loadedChunks = new LongOpenHashSet();
    private final LongOpenHashSet loadedCubes = new LongOpenHashSet();

    private final Long2ObjectOpenHashMap<State> chunkStateChanges = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<State> cubeStateChanges = new Long2ObjectOpenHashMap<>();

    @Override
    public synchronized void onChunkAdded(int x, int z) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x, z);

        if (chunkPresence.add(key)) {
            chunkStateChanges.put(key, State.LOAD_PENDING);
        }
    }

    @Override
    public synchronized void onChunkRemoved(int x, int z) {
        long key = ChunkCoordIntPair.chunkXZ2Int(x, z);

        if (chunkPresence.remove(key)) {
            chunkStateChanges.put(key, State.UNLOAD_PENDING);
        }
    }

    @Override
    public synchronized void onCubeAdded(int x, int y, int z) {
        long key = CoordinatePacker.pack(x, y, z);

        if (cubePresence.add(key)) {
            this.updateCubeNeighbors(x, y, z);
        }
    }

    @Override
    public synchronized void onCubeRemoved(int x, int y, int z) {
        long key = CoordinatePacker.pack(x, y, z);

        if (cubePresence.remove(key)) {
            this.updateCubeNeighbors(x, y, z);
        }
    }

    private void updateCubeNeighbors(int x, int y, int z) {
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            this.updateCubesMerged(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
        }
    }

    private boolean isCubePresent(int x, int y, int z) {
        return cubePresence.contains(CoordinatePacker.pack(x, y, z));
    }

    private void updateCubesMerged(int x, int y, int z) {
        boolean canRender = isCubePresent(x, y, z);

        if (canRender) {
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (!isCubePresent(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ)) {
                    canRender = false;
                    break;
                }
            }
        }

        long key = CoordinatePacker.pack(x, y, z);

        boolean isRendering = loadedCubes.contains(key);

        if (isRendering != canRender) {
            if (canRender) {
                cubeStateChanges.put(key, State.LOAD_PENDING);
            } else {
                cubeStateChanges.put(key, State.UNLOAD_PENDING);
            }
        }
    }

    @Override
    public synchronized void forAllReady(ChunkStatusListener listener) {
        loadedChunks.forEach((long k) -> {
            int x = unpackChunkX(k);
            int z = unpackChunkZ(k);

            listener.onChunkAdded(x, z);
        });

        loadedCubes.forEach((long k) -> {
            int x = CoordinatePacker.unpackX(k);
            int y = CoordinatePacker.unpackY(k);
            int z = CoordinatePacker.unpackZ(k);

            listener.onCubeAdded(x, y, z);
        });
    }

    @Override
    public synchronized void forEachEvent(ChunkStatusListener listener) {
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

        Long2ObjectMaps.fastForEach(
            cubeStateChanges, e -> {
                int x = CoordinatePacker.unpackX(e.getLongKey());
                int y = CoordinatePacker.unpackY(e.getLongKey());
                int z = CoordinatePacker.unpackZ(e.getLongKey());

                if (e.getValue() == State.LOAD_PENDING) {
                    if (loadedCubes.add(e.getLongKey())) {
                        listener.onCubeAdded(x, y, z);
                    }
                } else {
                    if (loadedCubes.remove(e.getLongKey())) {
                        listener.onCubeRemoved(x, y, z);
                    }
                }
            });

        cubeStateChanges.clear();
    }

    private static int unpackChunkZ(long pos) {
        return (int) ((pos >>> 32L) & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long pos) {
        return (int) (pos & 0xFFFFFFFFL);
    }
}
