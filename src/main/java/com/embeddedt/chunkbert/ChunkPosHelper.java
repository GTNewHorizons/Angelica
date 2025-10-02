package com.embeddedt.chunkbert;

public class ChunkPosHelper {
    public static int getPackedX(long pos) {
        return (int)(pos & 4294967295L);
    }

    public static int getPackedZ(long pos) {
        return (int)(pos >>> 32 & 4294967295L);
    }
}
