package com.gtnewhorizons.angelica.rendering.voxelization;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

public final class VoxelRangeTable {
    private static final int ENTRY_BYTES = 8;

    private ByteBuffer entries;
    private long entriesAddr;
    private int entryCount;
    private int runEnd;

    private int regionCount;
    private int[] regVbuf = new int[16];
    private float[] regX = new float[16];
    private float[] regY = new float[16];
    private float[] regZ = new float[16];
    private int[] regBase = new int[16];
    private int[] regCount = new int[16];
    private int[] regTotal = new int[16];

    public void beginRegion(int vbufGlId, float x, float y, float z) {
        final int g = regionCount++;
        if (g == regVbuf.length) {
            final int n = g << 1;
            regVbuf = Arrays.copyOf(regVbuf, n);
            regX = Arrays.copyOf(regX, n);
            regY = Arrays.copyOf(regY, n);
            regZ = Arrays.copyOf(regZ, n);
            regBase = Arrays.copyOf(regBase, n);
            regCount = Arrays.copyOf(regCount, n);
            regTotal = Arrays.copyOf(regTotal, n);
        }
        regVbuf[g] = vbufGlId;
        regX[g] = x;
        regY[g] = y;
        regZ[g] = z;
        regBase[g] = entryCount;
        regCount[g] = 0;
        regTotal[g] = 0;
    }

    public void addRange(int vertexStart, int vertexCount) {
        final int g = regionCount - 1;
        if (regCount[g] > 0 && vertexStart == runEnd) {
            regTotal[g] += vertexCount;
            runEnd += vertexCount;
            return;
        }
        if (entries == null) {
            entries = memAlloc(4096);
            entriesAddr = memAddress0(entries);
        } else if ((entryCount + 1) * ENTRY_BYTES > entries.capacity()) {
            entries = memRealloc(entries, entries.capacity() << 1);
            entriesAddr = memAddress0(entries);
        }
        final long p = entriesAddr + (long) entryCount * ENTRY_BYTES;
        memPutInt(p, vertexStart);
        memPutInt(p + 4, regTotal[g]);
        entryCount++;
        regCount[g]++;
        regTotal[g] += vertexCount;
        runEnd = vertexStart + vertexCount;
    }

    public ByteBuffer entries() {
        entries.position(0).limit(entryCount * ENTRY_BYTES);
        return entries;
    }

    public int entryCount() {
        return entryCount;
    }

    public int regionCount() {
        return regionCount;
    }

    public int regionVbuf(int i) {
        return regVbuf[i];
    }

    public float regionX(int i) {
        return regX[i];
    }

    public float regionY(int i) {
        return regY[i];
    }

    public float regionZ(int i) {
        return regZ[i];
    }

    public int regionRangeBase(int i) {
        return regBase[i];
    }

    public int regionRangeCount(int i) {
        return regCount[i];
    }

    public int regionVertexTotal(int i) {
        return regTotal[i];
    }

    public void clear() {
        regionCount = 0;
        entryCount = 0;
    }

    public void free() {
        if (entries != null) {
            memFree(entries);
            entries = null;
        }
    }
}
