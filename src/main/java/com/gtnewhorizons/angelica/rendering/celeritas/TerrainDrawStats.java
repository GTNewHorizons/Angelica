package com.gtnewhorizons.angelica.rendering.celeritas;

public final class TerrainDrawStats {
    private static int commandsSubmitted;
    private static int regionsDrawn;

    private TerrainDrawStats() {
    }

    public static void recordBatch(int commandCount) {
        commandsSubmitted += commandCount;
        regionsDrawn++;
    }

    public static int takeCommandsSubmitted() {
        final int n = commandsSubmitted;
        commandsSubmitted = 0;
        return n;
    }

    public static int takeRegionsDrawn() {
        final int n = regionsDrawn;
        regionsDrawn = 0;
        return n;
    }
}
