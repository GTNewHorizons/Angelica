package com.gtnewhorizons.angelica.glsm.streaming;

import net.minecraft.client.renderer.Tessellator;

import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutLong;

/**
 * Entity, block entity, and item ids of the vertices in the main Tessellator batch.
 */
public final class TessellatorEntityIds {

    private static Tessellator owner;
    private static int runCount;
    private static int[] runStarts = new int[8];
    private static long[] runIds = new long[8];

    private TessellatorEntityIds() {}

    public static void onIdsChanged(long previous, long next) {
        final Tessellator tess = Tessellator.instance;
        if (tess == null || !tess.isDrawing || tess.vertexCount == 0) return;
        final int start = tess.vertexCount;
        if (owner != tess) {
            owner = tess;
            runCount = 0;
        }
        if (runCount == 0) addRun(0, previous);
        if (runStarts[runCount - 1] == start) {
            runIds[runCount - 1] = next;
            if (runCount > 1 && runIds[runCount - 2] == next) runCount--;
        } else {
            addRun(start, next);
        }
    }

    public static void onReset(Tessellator tess) {
        if (tess != owner) return;
        owner = null;
        runCount = 0;
    }

    public static boolean hasIdChanges(Tessellator tess) {
        return tess == owner && runCount > 1;
    }

    static int runsFor(Tessellator tess, int vertexCount) {
        if (tess != owner) return 0;
        int runs = runCount;
        while (runs > 0 && runStarts[runs - 1] >= vertexCount) runs--;
        return runs;
    }

    static long firstRunIds() {
        return runIds[0];
    }

    static void write(long dst, int stride, int vertexCount, int runs) {
        for (int run = 0; run < runs; run++) {
            final int end = run + 1 < runs ? runStarts[run + 1] : vertexCount;
            final long ids = runIds[run];
            for (int vertex = runStarts[run]; vertex < end; vertex++) {
                memPutLong(dst + (long) vertex * stride, ids);
            }
        }
    }

    private static void addRun(int start, long ids) {
        if (runCount == runStarts.length) {
            runStarts = Arrays.copyOf(runStarts, runCount * 2);
            runIds = Arrays.copyOf(runIds, runCount * 2);
        }
        runStarts[runCount] = start;
        runIds[runCount] = ids;
        runCount++;
    }
}
