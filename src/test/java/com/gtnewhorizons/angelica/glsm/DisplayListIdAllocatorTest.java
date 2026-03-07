package com.gtnewhorizons.angelica.glsm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayListIdAllocatorTest {

    @Test
    void zeroNeverReturned() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator(1);
        // Allocate enough IDs to fill the first word (32 IDs). None should be 0.
        for (int i = 0; i < 31; i++) {
            int id = alloc.allocRange(1);
            assertNotEquals(0, id, "ID 0 must never be returned");
            assertTrue(id > 0, "All returned IDs must be positive");
        }
    }

    @Test
    void singleAllocReturnsSequential() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // First alloc should be 1 (0 is reserved)
        assertEquals(1, alloc.allocRange(1));
        assertEquals(2, alloc.allocRange(1));
        assertEquals(3, alloc.allocRange(1));
    }

    @Test
    void isAllocatedTracksState() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        assertFalse(alloc.isAllocated(0), "ID 0 should not report as allocated via isAllocated");
        assertFalse(alloc.isAllocated(1));

        int id = alloc.allocRange(1);
        assertTrue(alloc.isAllocated(id));
        assertFalse(alloc.isAllocated(id + 1));
    }

    @Test
    void freeAndReuse() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        int a = alloc.allocRange(1); // 1
        int b = alloc.allocRange(1); // 2
        int c = alloc.allocRange(1); // 3

        alloc.free(b); // free 2
        assertFalse(alloc.isAllocated(b));

        // Next alloc should reuse 2 (lowestFreeIdx reset)
        int d = alloc.allocRange(1);
        assertEquals(b, d, "Freed ID should be reused");
    }

    @Test
    void freeRangeReleasesAll() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        int base = alloc.allocRange(1);
        alloc.allocRange(1);
        alloc.allocRange(1);
        // IDs 1, 2, 3 allocated
        assertTrue(alloc.isAllocated(1));
        assertTrue(alloc.isAllocated(2));
        assertTrue(alloc.isAllocated(3));

        alloc.freeRange(1, 3);
        assertFalse(alloc.isAllocated(1));
        assertFalse(alloc.isAllocated(2));
        assertFalse(alloc.isAllocated(3));
    }

    @Test
    void rangeAllocReturnsConsecutiveIds() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // Range alloc of 64 should return 64 consecutive IDs starting at a word-aligned base
        int base = alloc.allocRange(64);
        assertTrue(base > 0, "Base must be positive");
        assertEquals(0, base % 32, "Range base should be word-aligned");

        // All IDs in range must be allocated
        for (int i = 0; i < 64; i++) {
            assertTrue(alloc.isAllocated(base + i), "ID " + (base + i) + " should be allocated");
        }
        // ID just past range should not be allocated
        assertFalse(alloc.isAllocated(base + 64));
    }

    @Test
    void rangeAllocPartialWord() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // Allocate 10 IDs — less than a full word, still word-aligned base
        int base = alloc.allocRange(10);
        assertTrue(base > 0);
        for (int i = 0; i < 10; i++) {
            assertTrue(alloc.isAllocated(base + i));
        }
        // Bits past the 10 in that same word should be free
        assertFalse(alloc.isAllocated(base + 10));
    }

    @Test
    void autoGrowOnExhaustion() {
        // Start very small — 1 word = 32 IDs (minus ID 0 = 31 usable)
        DisplayListIDAllocator alloc = new DisplayListIDAllocator(1);
        // Allocate all 31 usable IDs in the first word
        for (int i = 0; i < 31; i++) {
            int id = alloc.allocRange(1);
            assertTrue(id > 0 && id < 32);
        }
        // Next alloc must trigger grow
        int id = alloc.allocRange(1);
        assertTrue(id >= 32, "After exhausting first word, should allocate from grown region");
        assertTrue(alloc.isAllocated(id));
    }

    @Test
    void autoGrowForRange() {
        // Start with 1 word. Request 64 consecutive IDs — must grow.
        DisplayListIDAllocator alloc = new DisplayListIDAllocator(1);
        int base = alloc.allocRange(64);
        assertTrue(base > 0);
        for (int i = 0; i < 64; i++) {
            assertTrue(alloc.isAllocated(base + i));
        }
    }

    @Test
    void freeZeroIsNoOp() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // Should not throw
        alloc.free(0);
        alloc.free(-1);
    }

    @Test
    void isAllocatedOutOfBoundsReturnsFalse() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator(1);
        assertFalse(alloc.isAllocated(99999), "Out-of-bounds ID should return false");
        assertFalse(alloc.isAllocated(-1));
    }

    @Test
    void invalidCountThrows() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        assertThrows(IllegalArgumentException.class, () -> alloc.allocRange(0));
        assertThrows(IllegalArgumentException.class, () -> alloc.allocRange(-1));
    }

    @Test
    void stressAllocFreeRealloc() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        int[] ids = new int[1000];
        // Allocate 1000 IDs
        for (int i = 0; i < 1000; i++) {
            ids[i] = alloc.allocRange(1);
            assertTrue(ids[i] > 0);
            assertTrue(alloc.isAllocated(ids[i]));
        }
        // Free all odd-indexed IDs
        for (int i = 1; i < 1000; i += 2) {
            alloc.free(ids[i]);
            assertFalse(alloc.isAllocated(ids[i]));
        }
        // Even-indexed IDs should still be allocated
        for (int i = 0; i < 1000; i += 2) {
            assertTrue(alloc.isAllocated(ids[i]));
        }
        // Reallocate 500 IDs — should reuse freed slots
        for (int i = 0; i < 500; i++) {
            int id = alloc.allocRange(1);
            assertTrue(id > 0);
            assertTrue(alloc.isAllocated(id));
        }
    }

    @Test
    void noDoubleAllocation() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // Allocate many IDs and verify uniqueness
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            int id = alloc.allocRange(1);
            assertTrue(seen.add(id), "Duplicate ID returned: " + id);
        }
    }

    @Test
    void rangeAfterSingleAllocs() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        // Fragment the bitmap with single allocs
        for (int i = 0; i < 10; i++) {
            alloc.allocRange(1);
        }
        // Now request a range — should skip past the occupied word(s)
        int base = alloc.allocRange(32);
        assertTrue(base > 0);
        // The range must not overlap with any previously allocated ID
        for (int i = 0; i < 32; i++) {
            assertTrue(alloc.isAllocated(base + i));
        }
        // The first 10 IDs (1-10) should still be allocated
        for (int i = 1; i <= 10; i++) {
            assertTrue(alloc.isAllocated(i));
        }
    }

    @Test
    void multipleRanges() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        int base1 = alloc.allocRange(32);
        int base2 = alloc.allocRange(32);
        // Ranges must not overlap
        assertTrue(base2 >= base1 + 32 || base1 >= base2 + 32,
            "Ranges must not overlap: base1=" + base1 + " base2=" + base2);
        // All IDs in both ranges must be allocated
        for (int i = 0; i < 32; i++) {
            assertTrue(alloc.isAllocated(base1 + i));
            assertTrue(alloc.isAllocated(base2 + i));
        }
    }

    @Test
    void freeRangeThenReallocRange() {
        DisplayListIDAllocator alloc = new DisplayListIDAllocator();
        int base1 = alloc.allocRange(64);
        alloc.freeRange(base1, 64);
        // All should be free
        for (int i = 0; i < 64; i++) {
            assertFalse(alloc.isAllocated(base1 + i));
        }
        // Reallocate — should reuse the freed region
        int base2 = alloc.allocRange(64);
        assertEquals(base1, base2, "Freed range should be reused");
    }
}
