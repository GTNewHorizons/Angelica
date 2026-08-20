package com.gtnewhorizons.angelica.sdlgpu.resource;

public final class UploadArena {
    public static final int INITIAL_CAPACITY = 16 * 1024 * 1024;

    public static final int MAX_CAPACITY = 64 * 1024 * 1024;

    private static final int ALIGNMENT = 16;

    private int capacity = INITIAL_CAPACITY;
    private int growTo = INITIAL_CAPACITY;
    private int offset;
    private int copyCount;

    public int reserve(int size) {
        if (size < 0) return -1;
        final int at = (offset + ALIGNMENT - 1) & -ALIGNMENT;
        if ((long) at + size > capacity) return -1;
        offset = at + size;
        copyCount++;
        return at;
    }

    public boolean fits(int size) {
        return size >= 0 && size <= capacity;
    }

    public void requestGrow(int minSize) {
        if (minSize <= growTo) return;
        long next = growTo;
        while (next < minSize && next < MAX_CAPACITY) next <<= 1;
        growTo = (int) Math.min(next, MAX_CAPACITY);
    }

   public boolean applyGrow() {
        if (growTo > capacity) {
            capacity = growTo;
            return true;
        }
        return false;
    }

    public void reset() {
        offset = 0;
        copyCount = 0;
    }

    public int copyCount() { return copyCount; }
    public int usedBytes() { return offset; }
    public int capacity() { return capacity; }
}
