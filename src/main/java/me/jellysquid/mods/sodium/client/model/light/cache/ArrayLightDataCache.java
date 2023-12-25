package me.jellysquid.mods.sodium.client.model.light.cache;

import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockRenderView;

import java.util.Arrays;

/**
 * A light data cache which uses a flat-array to store the light data for the blocks in a given chunk and its direct
 * neighbors. This is considerably faster than using a hash table to lookup values for a given block position and
 * can be re-used by {@link WorldSlice} to avoid allocations.
 */
public class ArrayLightDataCache extends LightDataAccess {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int BLOCK_LENGTH = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

    private final long[] light;

    private int xOffset, yOffset, zOffset;

    public ArrayLightDataCache(BlockRenderView world) {
        this.world = world;
        this.light = new long[BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH];
    }

    public void reset(ChunkSectionPos origin) {
        this.xOffset = origin.getMinX() - NEIGHBOR_BLOCK_RADIUS;
        this.yOffset = origin.getMinY() - NEIGHBOR_BLOCK_RADIUS;
        this.zOffset = origin.getMinZ() - NEIGHBOR_BLOCK_RADIUS;

        Arrays.fill(this.light, 0L);
    }

    private int index(int x, int y, int z) {
        int x2 = x - this.xOffset;
        int y2 = y - this.yOffset;
        int z2 = z - this.zOffset;

        return (z2 * BLOCK_LENGTH * BLOCK_LENGTH) + (y2 * BLOCK_LENGTH) + x2;
    }

    @Override
    public long get(int x, int y, int z) {
        int l = this.index(x, y, z);

        long word = this.light[l];

        if (word != 0) {
            return word;
        }

        return this.light[l] = this.compute(x, y, z);
    }

}