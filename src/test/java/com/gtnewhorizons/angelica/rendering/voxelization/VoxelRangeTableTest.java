package com.gtnewhorizons.angelica.rendering.voxelization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoxelRangeTableTest {

    private final VoxelRangeTable t = new VoxelRangeTable();

    @AfterEach
    void free() {
        t.free();
    }

    private int entry(int i) {
        return memGetInt(memAddress0(t.entries()) + i * 4L);
    }

    @Test
    void singleRegionPrefixes() {
        t.beginRegion(7, 1f, 2f, 3f);
        t.addRange(100, 10);
        t.addRange(200, 20);
        t.addRange(50, 5);
        assertEquals(1, t.regionCount());
        assertEquals(7, t.regionVbuf(0));
        assertEquals(1f, t.regionX(0));
        assertEquals(2f, t.regionY(0));
        assertEquals(3f, t.regionZ(0));
        assertEquals(0, t.regionRangeBase(0));
        assertEquals(3, t.regionRangeCount(0));
        assertEquals(35, t.regionVertexTotal(0));
        assertEquals(3, t.entryCount());
        assertEquals(24, t.entries().remaining());
        assertEquals(100, entry(0));
        assertEquals(0, entry(1));
        assertEquals(200, entry(2));
        assertEquals(10, entry(3));
        assertEquals(50, entry(4));
        assertEquals(30, entry(5));
    }

    @Test
    void regionsAppendInOrderAndNeverMergeAcross() {
        t.beginRegion(1, 0f, 0f, 0f);
        t.addRange(0, 4);
        t.addRange(40, 6);
        t.beginRegion(2, 5f, 0f, 0f);
        t.addRange(46, 8);
        t.addRange(2000, 2);
        assertEquals(2, t.regionCount());
        assertEquals(1, t.regionVbuf(0));
        assertEquals(0f, t.regionX(0));
        assertEquals(0, t.regionRangeBase(0));
        assertEquals(2, t.regionRangeCount(0));
        assertEquals(10, t.regionVertexTotal(0));
        assertEquals(2, t.regionVbuf(1));
        assertEquals(5f, t.regionX(1));
        assertEquals(2, t.regionRangeBase(1));
        assertEquals(2, t.regionRangeCount(1));
        assertEquals(10, t.regionVertexTotal(1));
        assertEquals(4, t.entryCount());
        assertEquals(0, entry(0));
        assertEquals(0, entry(1));
        assertEquals(40, entry(2));
        assertEquals(4, entry(3));
        assertEquals(46, entry(4));
        assertEquals(0, entry(5));
        assertEquals(2000, entry(6));
        assertEquals(8, entry(7));
    }

    @Test
    void contiguousRangesMerge() {
        t.beginRegion(3, 0f, 0f, 0f);
        t.addRange(100, 10);
        t.addRange(110, 5);
        t.addRange(200, 3);
        t.addRange(300, 2);
        assertEquals(3, t.regionRangeCount(0));
        assertEquals(20, t.regionVertexTotal(0));
        assertEquals(3, t.entryCount());
        assertEquals(100, entry(0));
        assertEquals(0, entry(1));
        assertEquals(200, entry(2));
        assertEquals(15, entry(3));
        assertEquals(300, entry(4));
        assertEquals(18, entry(5));
    }

    @Test
    void growsAndClearsForReuse() {
        for (int i = 0; i < 300; i++) {
            t.beginRegion(i, 0f, 0f, 0f);
            t.addRange(i * 100, 3);
            t.addRange(i * 100 + 50, 3);
        }
        assertEquals(300, t.regionCount());
        assertEquals(600, t.entryCount());
        assertEquals(4800, t.entries().remaining());
        assertEquals(299 * 100 + 50, entry(1198));
        assertEquals(3, entry(1199));
        assertEquals(598, t.regionRangeBase(299));
        t.clear();
        t.beginRegion(5, 1f, 1f, 1f);
        t.addRange(9, 2);
        assertEquals(1, t.regionCount());
        assertEquals(1, t.entryCount());
        assertEquals(0, t.regionRangeBase(0));
        assertEquals(2, t.regionVertexTotal(0));
        assertEquals(9, entry(0));
    }
}
