package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadArenaTest {

    @Test
    void reserveBumpsOffsetAndCountsCopies() {
        final UploadArena a = new UploadArena();
        assertEquals(0, a.reserve(100));
        assertEquals(112, a.reserve(50));
        assertEquals(162, a.usedBytes());
        assertEquals(2, a.copyCount());
    }

    @Test
    void reserveFailsWhenFullWithoutCounting() {
        final UploadArena a = new UploadArena();
        assertEquals(0, a.reserve(a.capacity()));
        assertEquals(-1, a.reserve(1));
        assertEquals(-1, a.reserve(-1));
        assertEquals(1, a.copyCount());
    }

    @Test
    void fitsChecksAgainstCapacityNotRemaining() {
        final UploadArena a = new UploadArena();
        a.reserve(a.capacity() - 10);
        assertTrue(a.fits(100));
        assertFalse(a.fits(a.capacity() + 1));
        assertFalse(a.fits(-1));
    }

    @Test
    void resetClearsOffsetAndCountButKeepsCapacity() {
        final UploadArena a = new UploadArena();
        a.reserve(500);
        a.reset();
        assertEquals(0, a.usedBytes());
        assertEquals(0, a.copyCount());
        assertEquals(UploadArena.INITIAL_CAPACITY, a.capacity());
        assertEquals(0, a.reserve(10));
    }

    @Test
    void growIsDeferredUntilApplyGrow() {
        final UploadArena a = new UploadArena();
        a.requestGrow(UploadArena.INITIAL_CAPACITY + 1);
        assertEquals(UploadArena.INITIAL_CAPACITY, a.capacity());
        assertTrue(a.applyGrow());
        assertEquals(UploadArena.INITIAL_CAPACITY * 2, a.capacity());
        assertFalse(a.applyGrow());
    }

    @Test
    void requestGrowDoublesUntilMinSizeFits() {
        final UploadArena a = new UploadArena();
        a.requestGrow(UploadArena.INITIAL_CAPACITY * 3);
        assertTrue(a.applyGrow());
        assertEquals(UploadArena.INITIAL_CAPACITY * 4, a.capacity());
    }

    @Test
    void requestGrowClampsAtMaxCapacityInsteadOfSpinning() {
        final UploadArena a = new UploadArena();
        a.requestGrow(Integer.MAX_VALUE);
        assertTrue(a.applyGrow());
        assertEquals(UploadArena.MAX_CAPACITY, a.capacity());
        a.requestGrow(Integer.MAX_VALUE);
        assertFalse(a.applyGrow());
        assertEquals(UploadArena.MAX_CAPACITY, a.capacity());
    }

    @Test
    void requestGrowIsIdempotentBelowCurrentTarget() {
        final UploadArena a = new UploadArena();
        a.requestGrow(UploadArena.INITIAL_CAPACITY * 4);
        a.requestGrow(1);
        assertTrue(a.applyGrow());
        assertEquals(UploadArena.INITIAL_CAPACITY * 4, a.capacity());
    }

    @Test
    void overflowThenGrowRoundTrip() {
        final UploadArena a = new UploadArena();
        assertEquals(0, a.reserve(a.capacity()));
        final int want = a.usedBytes() + 4096;
        assertEquals(-1, a.reserve(4096));
        a.requestGrow(want);
        a.reset();
        assertTrue(a.applyGrow());
        assertEquals(0, a.reserve(a.capacity()));
        assertEquals(1, a.copyCount());
    }
}
