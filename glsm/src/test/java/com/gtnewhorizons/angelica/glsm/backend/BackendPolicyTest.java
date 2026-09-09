package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPolicyTest {

    @Test
    void onlyImmediateCanTear() {
        assertTrue(VSyncMode.ON.tearFree());
        assertTrue(VSyncMode.MAILBOX.tearFree());
        assertFalse(VSyncMode.OFF.tearFree());
    }

    private static final Predicate<VSyncMode> NO_MAILBOX = mode -> mode != VSyncMode.MAILBOX;
    private static final Predicate<VSyncMode> EVERYTHING = mode -> true;

    @Test
    void mailboxIsTakenWhenTheBackendOffersIt() {
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(null, EVERYTHING));
    }

    @Test
    void vsyncIsTheFallbackWhenMailboxIsUnsupported() {
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(null, NO_MAILBOX));
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.MAILBOX, NO_MAILBOX));
    }

    @Test
    void anExplicitSupportedPreferenceWins() {
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.ON, EVERYTHING));
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(VSyncMode.MAILBOX, EVERYTHING));
    }

    @Test
    void offIsNeverATearFreePreference() {
        assertEquals(VSyncMode.MAILBOX, RenderBackend.resolveTearFreeMode(VSyncMode.OFF, EVERYTHING));
        assertEquals(VSyncMode.ON, RenderBackend.resolveTearFreeMode(VSyncMode.OFF, NO_MAILBOX));
    }

    @Test
    void periodPrefersTheExactRational() {
        assertEquals(16_683_333L, RenderBackend.periodFromRational(60000, 1001, 0.0f));
        assertEquals(6_944_444L, RenderBackend.periodFromRational(144, 1, 0.0f));
    }

    @Test
    void periodFallsBackToTheFloatRate() {
        assertEquals(16_683_350L, RenderBackend.periodFromRational(0, 0, 59.94f));
        assertEquals(16_666_667L, RenderBackend.periodFromRational(0, 0, 60.0f));
    }

    @Test
    void periodIsZeroWhenUnknown() {
        assertEquals(0L, RenderBackend.periodFromRational(0, 0, 0.0f));
        assertEquals(0L, RenderBackend.periodFromRational(-1, -1, -1.0f));
        assertEquals(0L, RenderBackend.periodFromRational(144, 0, 0.0f));
    }

    @Test
    void integerNtscRatesAreAdjusted() {
        assertEquals(16_683_333L, RenderBackend.periodFromIntegerHz(59));
        assertEquals(6_951_389L, RenderBackend.periodFromIntegerHz(143));
        assertEquals(16_666_667L, RenderBackend.periodFromIntegerHz(60));
        assertEquals(6_944_444L, RenderBackend.periodFromIntegerHz(144));
        assertEquals(0L, RenderBackend.periodFromIntegerHz(0));
        assertEquals(0L, RenderBackend.periodFromIntegerHz(-60));
        assertEquals(60, RenderBackend.hzFromPeriod(RenderBackend.periodFromIntegerHz(59)));
    }

    @Test
    void hzFromPeriodRoundsToNearest() {
        assertEquals(60, RenderBackend.hzFromPeriod(16_666_667L));
        assertEquals(144, RenderBackend.hzFromPeriod(6_944_444L));
        assertEquals(0, RenderBackend.hzFromPeriod(0L));
        assertEquals(0, RenderBackend.hzFromPeriod(-1L));
    }

    @Test
    void plausiblePeriodFilterKeeps20To1000Hz() {
        assertEquals(0L, RenderBackend.plausiblePeriodNanos(999_000L));
        assertEquals(1_000_000L, RenderBackend.plausiblePeriodNanos(1_000_000L));
        assertEquals(50_000_000L, RenderBackend.plausiblePeriodNanos(50_000_000L));
        assertEquals(0L, RenderBackend.plausiblePeriodNanos(51_000_000L));
    }
}
