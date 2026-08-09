package com.gtnewhorizons.angelica.rendering.celeritas;

import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.rendering.celeritas.SectionAgeMath.STEP_NANOS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionAgeMathTest {

    @Test
    void quantizeRoundsDownToStep() {
        assertEquals(0L, SectionAgeMath.quantize(0L));
        assertEquals(0L, SectionAgeMath.quantize(STEP_NANOS - 1));
        assertEquals(STEP_NANOS, SectionAgeMath.quantize(STEP_NANOS));
        assertEquals(7 * STEP_NANOS, SectionAgeMath.quantize(7 * STEP_NANOS + 42));

        assertEquals(-STEP_NANOS, SectionAgeMath.quantize(-1L));
        assertEquals(-2 * STEP_NANOS, SectionAgeMath.quantize(-STEP_NANOS - 1));
        assertEquals(-STEP_NANOS, SectionAgeMath.quantize(-STEP_NANOS));
    }

    @Test
    void freshStateNeverSkips() {
        final long[] state = SectionAgeMath.newState();
        assertFalse(SectionAgeMath.canSkip(state, 0L, 0L));
        assertFalse(SectionAgeMath.canSkip(state, 123L, SectionAgeMath.quantize(456L)));
    }

    @Test
    void unsaturatedSkipsOnlyWithinTheSameStep() {
        final long[] state = SectionAgeMath.newState();
        final long newest = 1_000L;
        final long tsQ = SectionAgeMath.quantize(50 * STEP_NANOS + 3);
        SectionAgeMath.record(state, newest, tsQ, false);

        assertTrue(SectionAgeMath.canSkip(state, newest, tsQ));
        assertFalse(SectionAgeMath.canSkip(state, newest, tsQ + STEP_NANOS), "next step must rebuild");
        assertFalse(SectionAgeMath.canSkip(state, newest + 1, tsQ), "newer section load must rebuild");
    }

    @Test
    void saturatedSkipsAcrossStepsUntilNewestChanges() {
        final long[] state = SectionAgeMath.newState();
        final long newest = 1_000L;
        final long tsQ = SectionAgeMath.quantize(400 * STEP_NANOS);
        SectionAgeMath.record(state, newest, tsQ, true);

        assertTrue(SectionAgeMath.canSkip(state, newest, tsQ));
        assertTrue(SectionAgeMath.canSkip(state, newest, tsQ + 10_000 * STEP_NANOS), "saturation is time-independent");
        assertFalse(SectionAgeMath.canSkip(state, newest + 1, tsQ), "a new section load ends saturation");

        SectionAgeMath.record(state, newest + 1, tsQ + STEP_NANOS, false);
        assertTrue(SectionAgeMath.canSkip(state, newest + 1, tsQ + STEP_NANOS));
        assertFalse(SectionAgeMath.canSkip(state, newest + 1, tsQ + 2 * STEP_NANOS));
    }
}
