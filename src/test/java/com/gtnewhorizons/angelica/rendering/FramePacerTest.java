package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FramePacerTest {

    @Test
    void vsyncNeverPaces() {
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 0, 0));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 60, 144));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.ON, 260, 60));
    }

    @Test
    void vsyncOffHonorsOnlyTheCap() {
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.OFF, 215, 0));
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.OFF, 215, 144));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.OFF, 0, 144));
    }

    @Test
    void mailboxTakesTheLowerOfCapAndRefresh() {
        assertEquals(60, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 60, 143));
        assertEquals(143, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 215, 143));
        assertEquals(143, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 143, 143));
    }

    @Test
    void mailboxTreatsZeroAsNoLimitFromThatSource() {
        assertEquals(143, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 0, 143));
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 215, 0));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 0, 0));
    }

    @Test
    void nonPositiveCapIsUncapped() {
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.OFF, -1, 0));
        assertEquals(143, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, -1, 143));
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
    void implausibleRefreshLeavesMailboxOnTheCapAlone() {
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 215, 19));
        assertEquals(215, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 215, 1001));
        assertEquals(0, FramePacer.pacingCeilingHz(VSyncMode.MAILBOX, 0, 1001));
    }

    @Test
    void sleepDeductsWorkAndOverhead() {
        assertEquals(10_000L, FramePacer.sleepNanosFor(16_000L, 5_000L, 1_000L));
    }

    @Test
    void anOverrunAsksForNoSleep() {
        assertEquals(-5_000L, FramePacer.sleepNanosFor(16_000L, 21_000L, 0L));
    }

    @Test
    void indicatorNamesWhatIsHoldingTheRateDown() {
        assertEquals(" [vsync 144]", FramePacer.debugIndicator(VSyncMode.ON, 144, 0));
        assertEquals(" [mailbox 144]", FramePacer.debugIndicator(VSyncMode.MAILBOX, 144, 0));
        assertEquals(" [mailbox 144, cap 60]", FramePacer.debugIndicator(VSyncMode.MAILBOX, 144, 60));
        assertEquals(" [cap 60]", FramePacer.debugIndicator(VSyncMode.OFF, 144, 60));
    }

    @Test
    void indicatorOmitsACapThatDoesNothing() {
        assertEquals(" [vsync 144]", FramePacer.debugIndicator(VSyncMode.ON, 144, 60));
        assertEquals(" [vsync]", FramePacer.debugIndicator(VSyncMode.ON, 0, 60));
    }

    @Test
    void indicatorStillReportsVsyncWithoutARefreshRate() {
        assertEquals(" [vsync]", FramePacer.debugIndicator(VSyncMode.ON, 0, 0));
        assertEquals(" [mailbox, cap 60]", FramePacer.debugIndicator(VSyncMode.MAILBOX, 0, 60));
    }

    @Test
    void indicatorIsAbsentWhenNothingLimits() {
        assertNull(FramePacer.debugIndicator(VSyncMode.OFF, 144, 0));
        assertNull(FramePacer.debugIndicator(VSyncMode.OFF, 0, 0));
    }
}
