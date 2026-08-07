package com.gtnewhorizons.angelica.sdlgpu.resource;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class PersistentMapping {
    public final ByteBuffer staging;
    public final long offset;
    public final long length;
    public final int accessFlags;
    private final AtomicLong dirtyRange = new AtomicLong(CLEAN);
    public static final long CLEAN = -1L;

    public volatile long lastEnqueuedSeq = 0L;
    public PersistentMapping(ByteBuffer staging, long offset, long length, int accessFlags) {
        this.staging = staging;
        this.offset = offset;
        this.length = length;
        this.accessFlags = accessFlags;
    }

    public static boolean isClean(long packed) { return packed == CLEAN; }
    public static long rangeOffset(long packed) { return packed >>> 32; }
    public static long rangeSize(long packed) { return (packed & 0xFFFFFFFFL) - (packed >>> 32); }
    static long packRange(long min, long max) { return (min << 32) | (max & 0xFFFFFFFFL); }

    public boolean isDirty() { return dirtyRange.get() != CLEAN; }

    public long claimDirty() { return dirtyRange.getAndSet(CLEAN); }

    public boolean markDirty(long offset, long size) {
        final long end = offset + size;
        if (end <= offset) return false;
        for (;;) {
            final long prev = dirtyRange.get();
            final long next;
            if (prev == CLEAN) {
                next = packRange(offset, end);
            } else {
                final long min = Math.min(rangeOffset(prev), offset);
                final long max = Math.max((prev & 0xFFFFFFFFL), end);
                next = packRange(min, max);
                if (next == prev) return false;
            }
            if (dirtyRange.compareAndSet(prev, next)) return prev == CLEAN;
        }
    }
}
