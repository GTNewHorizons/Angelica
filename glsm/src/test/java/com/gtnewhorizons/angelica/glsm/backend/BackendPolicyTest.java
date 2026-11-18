package com.gtnewhorizons.angelica.glsm.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "vsync is the default even when the backend offers mailbox, , true, ON",
        "vsync is the default when mailbox is unsupported, , false, ON",
        "an unsupported mailbox preference falls back to vsync, MAILBOX, false, ON",
        "an explicit supported vsync preference wins, ON, true, ON",
        "an explicit supported mailbox preference wins, MAILBOX, true, MAILBOX",
        "off is never a tear-free preference with mailbox, OFF, true, ON",
        "off is never a tear-free preference without mailbox, OFF, false, ON",
    })
    void tearFreeModeResolution(String label, VSyncMode preference, boolean mailboxSupported, VSyncMode expected) {
        assertEquals(expected, RenderBackend.resolveTearFreeMode(preference, mode -> mailboxSupported || mode != VSyncMode.MAILBOX));
    }

    @ParameterizedTest(name = "{0}/{1} or {2} Hz -> {3} ns")
    @CsvSource({
        "60000, 1001, 0.0, 16683333",
        "144, 1, 0.0, 6944444",
        "0, 0, 59.94, 16683350",
        "0, 0, 60.0, 16666667",
        "0, 0, 0.0, 0",
        "-1, -1, -1.0, 0",
        "144, 0, 0.0, 0",
    })
    void periodPrefersTheExactRationalThenTheFloatRate(int numerator, int denominator, float hz, long expected) {
        assertEquals(expected, RenderBackend.periodFromRational(numerator, denominator, hz));
    }

    @ParameterizedTest(name = "{0} Hz -> {1} ns")
    @CsvSource({"59, 16683333", "143, 6951389", "60, 16666667", "144, 6944444", "0, 0", "-60, 0"})
    void integerNtscRatesAreAdjusted(int hz, long expected) {
        assertEquals(expected, RenderBackend.periodFromIntegerHz(hz));
    }

    @Test
    void hzFromPeriodRoundsToNearest() {
        assertEquals(60, RenderBackend.hzFromPeriod(16_666_667L));
        assertEquals(144, RenderBackend.hzFromPeriod(6_944_444L));
        assertEquals(0, RenderBackend.hzFromPeriod(0L));
        assertEquals(0, RenderBackend.hzFromPeriod(-1L));
        assertEquals(60, RenderBackend.hzFromPeriod(RenderBackend.periodFromIntegerHz(59)));
    }

    @Test
    void plausiblePeriodFilterKeeps20To1000Hz() {
        assertEquals(0L, RenderBackend.plausiblePeriodNanos(999_000L));
        assertEquals(1_000_000L, RenderBackend.plausiblePeriodNanos(1_000_000L));
        assertEquals(50_000_000L, RenderBackend.plausiblePeriodNanos(50_000_000L));
        assertEquals(0L, RenderBackend.plausiblePeriodNanos(51_000_000L));
    }
}
