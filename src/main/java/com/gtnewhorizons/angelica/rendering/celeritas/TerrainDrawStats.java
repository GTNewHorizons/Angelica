package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;

public final class TerrainDrawStats {
    private static final Tracy.ZoneId Z_EXECUTE_BATCH = Tracy.zoneId("chunkExecuteBatch", Tracy.COLOR_TERRAIN);

    private static int commandsSubmitted;
    private static int regionsDrawn;
    private static int batchRebuilds;

    private static int cullCommandsSubmitted;
    private static int cullRegionsDrawn;
    private static int cullMaxRegionCommands;
    private static long sectionMetaBytes;

    private TerrainDrawStats() {
    }

    public static void beginExecuteZone() {
        if (Tracy.ENABLED) Tracy.beginZone(Z_EXECUTE_BATCH);
    }

    public static void endExecuteZone() {
        if (Tracy.ENABLED) Tracy.endZone();
    }

    public static void recordBatch(int commandCount) {
        commandsSubmitted += commandCount;
        regionsDrawn++;
    }

    public static void recordRebuild() {
        batchRebuilds++;
    }

    public static void recordCullRegion(int commandCount) {
        cullCommandsSubmitted += commandCount;
        cullRegionsDrawn++;
        if (commandCount > cullMaxRegionCommands) cullMaxRegionCommands = commandCount;
    }

    public static void recordSectionMetaBytes(int bytes) {
        sectionMetaBytes += bytes;
    }

    public static int takeBatchRebuilds() {
        final int n = batchRebuilds;
        batchRebuilds = 0;
        return n;
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

    public static int takeCullCommandsSubmitted() {
        final int n = cullCommandsSubmitted;
        cullCommandsSubmitted = 0;
        return n;
    }

    public static int takeCullRegionsDrawn() {
        final int n = cullRegionsDrawn;
        cullRegionsDrawn = 0;
        return n;
    }

    public static int takeCullMaxRegionCommands() {
        final int n = cullMaxRegionCommands;
        cullMaxRegionCommands = 0;
        return n;
    }

    public static long takeSectionMetaBytes() {
        final long n = sectionMetaBytes;
        sectionMetaBytes = 0;
        return n;
    }
}
