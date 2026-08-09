package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferThreadWakePolicyTest {

    @Test
    void wakesOnceEveryCoalesceWindow() {
        int woken = 0;
        for (long seq = 1; seq <= 1600; seq++) {
            if (TransferThread.shouldWakeOnEnqueue(seq)) woken++;
        }
        assertEquals(100, woken, "1600 enqueues must produce 100 unparks, not 1600");
    }

    @Test
    void windowIsEvenlySpaced() {
        long previous = -1;
        for (long seq = 1; seq <= 512; seq++) {
            if (!TransferThread.shouldWakeOnEnqueue(seq)) continue;
            if (previous >= 0) assertEquals(16, seq - previous, "gaps between unparks must be uniform");
            previous = seq;
        }
    }

    @Test
    void neighboursOfAWakeDoNotWake() {
        assertTrue(TransferThread.shouldWakeOnEnqueue(16));
        assertFalse(TransferThread.shouldWakeOnEnqueue(15));
        assertFalse(TransferThread.shouldWakeOnEnqueue(17));
    }

    @Test
    void policyHoldsAtLargeSequenceNumbers() {
        final long base = 1L << 40;
        int woken = 0;
        for (long seq = base; seq < base + 1600; seq++) {
            if (TransferThread.shouldWakeOnEnqueue(seq)) woken++;
        }
        assertEquals(100, woken);
    }
}
