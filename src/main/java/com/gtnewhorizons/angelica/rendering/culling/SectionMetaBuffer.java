package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SectionMetaBuffer {
    private static final int INITIAL_CAPACITY = 16384;
    public static final int BYTES_PER_SECTION = 64;
    public static final int FACING_COUNT = 7;
    public static final int POST_COUNT = FACING_COUNT + 1;

    public static final int OFFSET_SLICE_MASK = 12;
    public static final int OFFSET_POSTS = 16;
    public static final int OFFSET_FIRST_INDEX_BASE = 48;

    private final Long2IntOpenHashMap keyToSlot = new Long2IntOpenHashMap();
    private final IntArrayList freeSlots = new IntArrayList();
    private ByteBuffer mirror;
    private ByteBuffer mirrorView;
    private int capacity;
    private int highWaterMark;
    private boolean dirty;

    public SectionMetaBuffer() {
        this.keyToSlot.defaultReturnValue(-1);
    }

    private static long sectionKey(int passIndex, int sectionOriginX, int sectionOriginY, int sectionOriginZ) {
        final long p  = ((long) passIndex) & 0x7L;
        final long sx = ((long)(sectionOriginX >> 4)) & 0x3FFFFFL;
        final long sy = ((long)(sectionOriginY >> 4)) & 0xFFFFL;
        final long sz = ((long)(sectionOriginZ >> 4)) & 0x7FFFFFL;
        return (p << 61) | (sx << 39) | (sy << 23) | sz;
    }

    public synchronized int update(int passIndex, int regionOriginX, int regionOriginY, int regionOriginZ, int localSectionIndex,
                                   long dataPtr, SectionRenderDataUnsafe.Strategy layout, ChunkPrimitiveType primitiveType) {
        if (dataPtr == 0L) return -1;
        final int sectionOriginX = regionOriginX + (((localSectionIndex >> 5) & 0x07) << 4);
        final int sectionOriginY = regionOriginY + (((localSectionIndex     ) & 0x03) << 4);
        final int sectionOriginZ = regionOriginZ + (((localSectionIndex >> 2) & 0x07) << 4);
        final long key = sectionKey(passIndex, sectionOriginX, sectionOriginY, sectionOriginZ);
        int slot = keyToSlot.get(key);
        if (slot < 0) {
            slot = allocateSlot();
            keyToSlot.put(key, slot);
        }
        writeSlot(slot, sectionOriginX, sectionOriginY, sectionOriginZ, dataPtr, layout, primitiveType);
        dirty = true;
        return slot;
    }

    public synchronized void remove(int passIndex, int regionOriginX, int regionOriginY, int regionOriginZ, int localSectionIndex) {
        if (mirror == null) return;
        final int sectionOriginX = regionOriginX + (((localSectionIndex >> 5) & 0x07) << 4);
        final int sectionOriginY = regionOriginY + (((localSectionIndex     ) & 0x03) << 4);
        final int sectionOriginZ = regionOriginZ + (((localSectionIndex >> 2) & 0x07) << 4);
        final long key = sectionKey(passIndex, sectionOriginX, sectionOriginY, sectionOriginZ);
        final int slot = keyToSlot.remove(key);
        if (slot < 0) return;
        zeroSlot(slot);
        freeSlots.add(slot);
        dirty = true;
    }

    private int allocateSlot() {
        if (!freeSlots.isEmpty()) {
            return freeSlots.removeInt(freeSlots.size() - 1);
        }
        if (mirror == null) {
            grow(INITIAL_CAPACITY);
        } else if (highWaterMark >= capacity) {
            grow(capacity * 2);
        }
        return highWaterMark++;
    }

    private void grow(int newCapacity) {
        final int newSize = newCapacity * BYTES_PER_SECTION;
        final ByteBuffer next = MemoryUtilities.memAlloc(newSize).order(ByteOrder.nativeOrder());
        if (mirror != null) {
            mirror.position(0).limit(highWaterMark * BYTES_PER_SECTION);
            next.put(mirror);
            mirrorView = null;
            MemoryUtilities.memFree(mirror);
        }
        next.position(0);
        next.limit(newSize);
        mirror = next;
        mirrorView = mirror.duplicate().order(ByteOrder.nativeOrder());
        capacity = newCapacity;
    }

    private void writeSlot(int slot, int sectionOriginX, int sectionOriginY, int sectionOriginZ, long dataPtr, SectionRenderDataUnsafe.Strategy layout, ChunkPrimitiveType primitiveType) {
        final int base = slot * BYTES_PER_SECTION;
        mirror.putInt(base + 0,  sectionOriginX);
        mirror.putInt(base + 4,  sectionOriginY);
        mirror.putInt(base + 8,  sectionOriginZ);
        mirror.putInt(base + OFFSET_SLICE_MASK, SectionRenderDataUnsafe.getSliceMask(dataPtr));
        for (int facing = 0; facing < FACING_COUNT; facing++) {
            mirror.putInt(base + OFFSET_POSTS + facing * 4, layout.getVertexOffset(dataPtr, facing));
        }
        mirror.putInt(base + OFFSET_POSTS + FACING_COUNT * 4, layout.getRunVertexEnd(dataPtr, FACING_COUNT - 1, primitiveType));
        mirror.putInt(base + OFFSET_FIRST_INDEX_BASE, layout.getIndexOffset(dataPtr, 0) / 4);
        mirror.putInt(base + 52, 0);
        mirror.putInt(base + 56, 0);
        mirror.putInt(base + 60, 0);
    }

    private void zeroSlot(int slot) {
        final int base = slot * BYTES_PER_SECTION;
        for (int i = 0; i < BYTES_PER_SECTION; i += 4) {
            mirror.putInt(base + i, 0);
        }
    }

    synchronized int lookupSlot(int passIndex, int sectionOriginX, int sectionOriginY, int sectionOriginZ) {
        return keyToSlot.get(sectionKey(passIndex, sectionOriginX, sectionOriginY, sectionOriginZ));
    }

    synchronized boolean isDirty() { return dirty; }
    synchronized void clearDirty() { dirty = false; }
    synchronized int getCapacity() { return capacity; }
    public synchronized int getHighWaterMark() { return highWaterMark; }

    public synchronized ByteBuffer getMirrorForReadOnly() { return mirrorView; }

    @FunctionalInterface
    public interface Sink {
        boolean accept(ByteBuffer mirror);
    }

    public synchronized boolean syncIfDirty(Sink sink) {
        if (!dirty) return false;
        if (mirror == null || highWaterMark == 0 || mirrorView == null) {
            dirty = false;
            return false;
        }
        final int activeBytes = highWaterMark * BYTES_PER_SECTION;
        mirrorView.position(0).limit(activeBytes);
        if (!sink.accept(mirrorView)) return false;
        dirty = false;
        return true;
    }

    public synchronized void reset() {
        mirrorView = null;
        if (mirror != null) {
            MemoryUtilities.memFree(mirror);
            mirror = null;
        }
        keyToSlot.clear();
        freeSlots.clear();
        capacity = 0;
        highWaterMark = 0;
        dirty = false;
    }

    public synchronized void shutdown() { reset(); }
}
