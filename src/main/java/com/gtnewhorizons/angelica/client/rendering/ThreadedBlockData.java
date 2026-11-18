package com.gtnewhorizons.angelica.client.rendering;

import net.minecraft.block.Block;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to store the block bounds fields in a thread-safe manner, as instance fields don't work correctly
 * on multiple threads.
 */
public final class ThreadedBlockData {

    public static final Thread MAIN_THREAD = Thread.currentThread();
    private static Thread SERVER_THREAD;
    private static final AtomicInteger CURRENT_SERVER_THREAD_ID = new AtomicInteger();

    private final int serverThreadId = Thread.currentThread() == SERVER_THREAD ? CURRENT_SERVER_THREAD_ID.get() : -1;
    public double minX, minY, minZ, maxX, maxY, maxZ;

    public ThreadedBlockData() {}

    public ThreadedBlockData(ThreadedBlockData other) {
        this.minX = other.minX;
        this.minY = other.minY;
        this.minZ = other.minZ;
        this.maxX = other.maxX;
        this.maxY = other.maxY;
        this.maxZ = other.maxZ;
    }

    public boolean isCurrent() {
      return this.serverThreadId == CURRENT_SERVER_THREAD_ID.get();
    }

    public static ThreadedBlockData get(Block block) {
        return ((Getter) block).angelica$getThreadData();
    }

    public static void setServerThread(Thread t) {
        ThreadedBlockData.SERVER_THREAD = t;
        if (t != null) {
            CURRENT_SERVER_THREAD_ID.incrementAndGet();
        }
    }

    public static Thread getServerThread() {
        return SERVER_THREAD;
    }

    public interface Getter {
        ThreadedBlockData angelica$getThreadData();
    }
}
