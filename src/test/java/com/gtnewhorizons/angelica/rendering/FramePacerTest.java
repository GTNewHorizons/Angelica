package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FramePacerTest {

    @Test
    void vsyncStillHonoursTheCap() {
        assertEquals(60, FramePacer.pacingCeilingHz(VSyncMode.ON, 60, 144, true));
        assertEquals(30, FramePacer.pacingCeilingHz(VSyncMode.ON, 30, 144, false));
    }

    @Test
    void anActiveGateGetsTheHeadroomBackstop() {
        assertEquals(151, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 144, true));
        assertEquals(151, FramePacer.pacingCeilingHz(VSyncMode.ON, 260, 144, true));
    }

    @Test
    void aQuietGateGetsThePlainRefreshBackstopToAvoidQueueSaturation() {
        assertEquals(144, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 144, false));
        assertEquals(144, FramePacer.pacingCeilingHz(VSyncMode.ON, 260, 144, false));
    }

    @Test
    void mailboxGetsThePlainRefreshBackstopRegardlessOfTheGate() {
        assertEquals(60, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 0, 60, false));
        assertEquals(60, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 0, 60, true));
        assertEquals(45, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 45, 60, false));
    }

    @Test
    void vsyncOffIsBoundedOnlyByTheCap() {
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.OFF, 0, 144, false));
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.OFF, 215, 144, false));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.OFF, -1, 144, false));
    }

    @Test
    void anUnknownRefreshRateLeavesTearFreeModesUnbounded() {
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 0, true));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 19, false));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 1001, true));
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.ON, 215, 1001, true));
    }

    @Test
    void gateThresholdScalesWithThePeriodAboveAFloor() {
        assertEquals(100_000L, FramePacer.gateBlockThresholdNanos(1_000_000L));
        assertEquals(173_611L, FramePacer.gateBlockThresholdNanos(5_555_555L));
        assertEquals(520_833L, FramePacer.gateBlockThresholdNanos(16_666_666L));
    }

    @Test
    void implausibleRefreshIsIgnored() {
        assertEquals(0, FramePacer.plausibleRefreshHz(0));
        assertEquals(0, FramePacer.plausibleRefreshHz(-1));
        assertEquals(0, FramePacer.plausibleRefreshHz(19));
        assertEquals(0, FramePacer.plausibleRefreshHz(1001));
        assertEquals(20, FramePacer.plausibleRefreshHz(20));
        assertEquals(1000, FramePacer.plausibleRefreshHz(1000));
    }

    @Test
    void headroomRoundsToTheNearestHz() {
        assertEquals(21, FramePacer.withHeadroom(20, FramePacer.PACING_HEADROOM_PERCENT));
        assertEquals(62, FramePacer.withHeadroom(59, FramePacer.PACING_HEADROOM_PERCENT));
        assertEquals(63, FramePacer.withHeadroom(60, FramePacer.PACING_HEADROOM_PERCENT));
        assertEquals(151, FramePacer.withHeadroom(144, FramePacer.PACING_HEADROOM_PERCENT));
        assertEquals(252, FramePacer.withHeadroom(240, FramePacer.PACING_HEADROOM_PERCENT));
        assertEquals(1050, FramePacer.withHeadroom(1000, FramePacer.PACING_HEADROOM_PERCENT));
    }

    @Test
    void headroomAlwaysClearsTheRefreshRate() {
        for (int hz = 20; hz <= 1000; hz++) {
            assertTrue(FramePacer.withHeadroom(hz, FramePacer.PACING_HEADROOM_PERCENT) > hz, "headroom must exceed " + hz + "Hz");
            assertTrue(FramePacer.withHeadroom(hz, 0) > hz, "a zero-percent headroom must still exceed " + hz + "Hz");
        }
        assertEquals(0, FramePacer.withHeadroom(0, FramePacer.PACING_HEADROOM_PERCENT));
    }

    @Test
    void theDeadlineAdvancesByExactlyOnePeriodDespiteJitter() {
        final long target = 16_666_666L;
        long deadline = FramePacer.nextDeadline(0L, 1_000L, target);
        assertEquals(1_000L + target, deadline);

        final long first = deadline;
        final long[] wakeJitter = { -400_000L, 900_000L, -120_000L, 250_000L };
        for (int i = 0; i < wakeJitter.length; i++) {
            deadline = FramePacer.nextDeadline(deadline, deadline + wakeJitter[i], target);
            assertEquals(first + target * (i + 1), deadline, "jitter must not accumulate into the deadline");
        }
    }

    @Test
    void fallingMoreThanTwoPeriodsBehindResynchronises() {
        final long target = 16_666_666L;
        final long deadline = 1_000_000_000L;
        final long lateNow = deadline + target * 3;
        assertEquals(lateNow + target, FramePacer.nextDeadline(deadline, lateNow, target));
    }

    @Test
    void indicatorNamesWhatIsHoldingTheRateDown() {
        assertEquals(" [vsync 144]", FramePacer.debugIndicator(VSyncMode.ON, 144, 0, false));
        assertEquals(" [vsync 144, cap 60]", FramePacer.debugIndicator(VSyncMode.ON, 144, 60, false));
        assertEquals(" [cap 215]", FramePacer.debugIndicator(VSyncMode.OFF, 144, 215, false));
        assertEquals(" [mailbox 144]", FramePacer.debugIndicator(VSyncMode.MAILBOX, 144, 0, false));
    }

    @Test
    void indicatorOmitsACapTheBackstopAlreadyBeats() {
        assertEquals(" [vsync 144]", FramePacer.debugIndicator(VSyncMode.ON, 144, 260, false));
    }

    @Test
    void indicatorStillReportsVsyncWithoutARefreshRate() {
        assertEquals(" [vsync]", FramePacer.debugIndicator(VSyncMode.ON, 0, 0, false));
        assertEquals(" [vsync, cap 60]", FramePacer.debugIndicator(VSyncMode.ON, 0, 60, false));
    }

    @Test
    void indicatorIsAbsentWhenNothingLimits() {
        assertNull(FramePacer.debugIndicator(VSyncMode.OFF, 144, 0, false));
        assertNull(FramePacer.debugIndicator(VSyncMode.OFF, 0, 0, false));
    }
}
