package com.gtnewhorizons.angelica.debug.screen.entries;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllocationRateTest {

    @Test
    void gcCountChangeKeepsPreviousRate() {
        final MemoryEntry.AllocationRate rate = new MemoryEntry.AllocationRate();
        rate.sample(1000L, 1_000L, 3L);
        rate.sample(1500L, 2_000L, 3L);
        rate.sample(2000L, 500L, 4L);
        assertEquals(2_000L, rate.rate());
        rate.sample(2500L, 1_000L, 4L);
        assertEquals(1_000L, rate.rate());
    }

    @Test
    void sampleCloserThanHalfASecondChangesNothing() {
        final MemoryEntry.AllocationRate rate = new MemoryEntry.AllocationRate();
        rate.sample(1000L, 1_000L, 3L);
        rate.sample(1200L, 9_000L, 3L);
        assertEquals(0L, rate.rate());
        rate.sample(1500L, 2_000L, 3L);
        assertEquals(2_000L, rate.rate());
    }

    @Test
    void gapOverFiveSecondsResetsTheBaselineWithoutARate() {
        final MemoryEntry.AllocationRate rate = new MemoryEntry.AllocationRate();
        rate.sample(1000L, 1_000L, 3L);
        rate.sample(1500L, 2_000L, 3L);
        rate.sample(9000L, 90_000L, 3L);
        assertEquals(2_000L, rate.rate());
        rate.sample(9500L, 92_000L, 3L);
        assertEquals(4_000L, rate.rate());
    }
}
