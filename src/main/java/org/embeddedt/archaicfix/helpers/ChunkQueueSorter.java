package org.embeddedt.archaicfix.helpers;

import net.minecraft.world.ChunkCoordIntPair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;

public class ChunkQueueSorter implements Comparator<Pair<ChunkCoordIntPair, Runnable>> {
    private final ChunkCoordIntPair[] playerChunks;

    public ChunkQueueSorter(ChunkCoordIntPair[] playerChunks) {
        this.playerChunks = playerChunks;
    }

    private int averagePlayerDistance(ChunkCoordIntPair pair) {
        int shortestDistance = Integer.MAX_VALUE;
        for(ChunkCoordIntPair p : playerChunks) {
            int xDist = p.chunkXPos - pair.chunkXPos;
            int zDist = p.chunkZPos - pair.chunkZPos;
            shortestDistance = Math.min(shortestDistance, (xDist * xDist) + (zDist * zDist));
        }
        return shortestDistance;
    }

    @Override
    public int compare(Pair<ChunkCoordIntPair, Runnable> c1, Pair<ChunkCoordIntPair, Runnable> c2) {
        int d1 = averagePlayerDistance(c1.getLeft());
        int d2 = averagePlayerDistance(c2.getLeft());
        return d1 - d2;
    }
}
