package org.embeddedt.archaicfix.block;

import net.minecraft.block.Block;

public class ThreadedBlockData {
    public double minX = 0;
    public double minY = 0;
    public double minZ = 0;
    public double maxX = 0;
    public double maxY = 0;
    public double maxZ = 0;

    public ThreadedBlockData() {

    }

    public ThreadedBlockData(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public ThreadedBlockData(ThreadedBlockData other) {
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
    }
}
