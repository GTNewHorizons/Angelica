package com.gtnewhorizons.angelica.rendering.celeritas;

import java.util.concurrent.atomic.AtomicInteger;

public class CeleritasDebug {
    private static final AtomicInteger chunkUpdateCounter = new AtomicInteger(0);

    public static void incrementChunkUpdateCounter() {
        chunkUpdateCounter.getAndIncrement();
    }

    public static int readAndResetChunkUpdateCounter() {
        return chunkUpdateCounter.getAndSet(0);
    }
}
