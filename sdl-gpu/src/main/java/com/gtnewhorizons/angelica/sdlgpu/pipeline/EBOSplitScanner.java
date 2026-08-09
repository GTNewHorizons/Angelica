package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public final class EBOSplitScanner {
    private EBOSplitScanner() {}

    public record EboSplit(int firstIndex, int count) {}

    public static EboSplit[] scanIndices(ByteBuffer shadow, int indexCount, int indexType, int sentinel) {
        if (indexCount <= 0) return new EboSplit[0];
        final ArrayList<EboSplit> runs = new ArrayList<>(4);
        int runStart = 0;
        switch (indexType) {
            case GL11.GL_UNSIGNED_SHORT -> {
                final short s = (short) sentinel;
                final ShortBuffer sb = shadow.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer();
                for (int i = 0; i < indexCount; i++) {
                    if (sb.get(i) == s) {
                        if (i > runStart) runs.add(new EboSplit(runStart, i - runStart));
                        runStart = i + 1;
                    }
                }
            }
            case GL11.GL_UNSIGNED_INT -> {
                final IntBuffer ib = shadow.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
                for (int i = 0; i < indexCount; i++) {
                    if (ib.get(i) == sentinel) {
                        if (i > runStart) runs.add(new EboSplit(runStart, i - runStart));
                        runStart = i + 1;
                    }
                }
            }
            default -> {
                return new EboSplit[]{ new EboSplit(0, indexCount) };
            }
        }
        if (indexCount > runStart) runs.add(new EboSplit(runStart, indexCount - runStart));
        return runs.toArray(new EboSplit[0]);
    }

    public static int sliceSplits(EboSplit[] full, int firstIndex, int count, int[] firstsOut, int[] countsOut) {
        if (full.length == 0 || count <= 0) return 0;
        final int drawEnd = firstIndex + count;
        int n = 0;
        for (EboSplit s : full) {
            final int sStart = s.firstIndex();
            final int sEnd = sStart + s.count();
            if (sEnd <= firstIndex || sStart >= drawEnd) continue;
            final int clippedStart = Math.max(sStart, firstIndex);
            final int clippedEnd = Math.min(sEnd, drawEnd);
            firstsOut[n] = clippedStart - firstIndex;
            countsOut[n] = clippedEnd - clippedStart;
            n++;
        }
        return n;
    }
}
