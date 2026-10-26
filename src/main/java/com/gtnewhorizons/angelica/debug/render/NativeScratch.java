package com.gtnewhorizons.angelica.debug.render;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memRealloc;

public final class NativeScratch {

    private final int initial;
    private ByteBuffer buffer;
    private long address;
    private int capacity;

    public NativeScratch(int initial) {
        this.initial = initial;
    }

    public long address() {
        return address;
    }

    public void ensure(int bytes) {
        if (bytes <= capacity) return;
        int grown = Math.max(1, Math.max(initial, capacity));
        while (grown < bytes) grown <<= 1;
        buffer = memRealloc(buffer, grown);
        address = memAddress0(buffer);
        capacity = buffer.capacity();
    }

    public ByteBuffer window(int bytes) {
        buffer.clear();
        buffer.limit(bytes);
        return buffer;
    }

    public void free() {
        memFree(buffer);
        buffer = null;
        address = 0L;
        capacity = 0;
    }
}
