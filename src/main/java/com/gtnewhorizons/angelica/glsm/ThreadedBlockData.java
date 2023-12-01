package com.gtnewhorizons.angelica.glsm;

import net.minecraft.block.Block;

/**
 * Used to store the block bounds fields in a thread-safe manner, as instance fields don't work correctly
 * on multiple threads.
 */
public class ThreadedBlockData {
    public double minX, minY, minZ, maxX, maxY, maxZ;

    public ThreadedBlockData() {}

    public ThreadedBlockData(ThreadedBlockData other) {
        this.minX = other.minX;
        this.minY  = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
    }

    public static ThreadedBlockData get(Block block) {
        return ((Getter)block).angelica$getThreadData();
    }

    public interface Getter {
        ThreadedBlockData angelica$getThreadData();
    }
}
