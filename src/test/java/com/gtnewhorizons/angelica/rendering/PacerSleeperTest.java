package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;

import static com.gtnewhorizons.angelica.rendering.PacerTestClock.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerSleeperTest {

    private static final long COLD_FLOOR = PacerSleeper.MIN_SPIN_FLOOR_NANOS;

    private static PacerSleeper sleeper(PacerTestClock time) {
        return new PacerSleeper(time.clock(), time.parker(), false);
    }

    @Test
    void aLongWaitCostsOneParkNotAChunkPerMillisecond() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        s.sleepUntil(10 * MS, 0L);

        assertEquals(1, time.parks, "the request must be sized to the deadline, not to a fixed chunk");
        assertEquals(10 * MS - COLD_FLOOR, time.lastRequest, "the park should land one spin floor short of the deadline");
    }

    @Test
    void aWellBehavedTimerLeavesNoMoreThanTheFloorToSpin() {
        final PacerTestClock time = new PacerTestClock();
        time.lag = 20_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < 20; i++) {
            final long deadline = time.now + 13 * MS;
            final long floor = Reflect.<Long>get(s, "spinFloorNanos");
            final long woke = s.sleepUntil(deadline, time.now);
            assertTrue(deadline - woke <= floor, "left " + (deadline - woke) + "ns to spin, floor is " + floor);
        }
    }

    @Test
    void theLagEstimateConvergesSoTheDeadlineIsNotOvershot() {
        final PacerTestClock time = new PacerTestClock();
        time.lag = 300_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < 15; i++) {
            s.sleepUntil(time.now + 13 * MS, time.now);
        }
        assertEquals(300_000L, s.avgOvershootNanos(), "the measured lag should settle on the platform's actual lag");

        final long deadline = time.now + 13 * MS;
        final long woke = s.sleepUntil(deadline, time.now);
        assertTrue(woke <= deadline, "a converged estimate must not overshoot: overshot by " + (woke - deadline));
    }

    @Test
    void anEarlyWakeReParksInsteadOfSpinningTheRemainder() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);
        time.nextParkActual = 2 * MS;

        final long woke = s.sleepUntil(10 * MS, 0L);

        assertEquals(2, time.parks, "a short park must be followed by another, not by a spin");
        assertTrue(10 * MS - woke <= Reflect.<Long>get(s, "spinFloorNanos"), "the second park should still land inside the floor");
    }

    @Test
    void aCoarseTimerParksLateRatherThanSpinningOutTheFrame() {
        final PacerTestClock time = new PacerTestClock();
        time.granularity = 15_600_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < 30; i++) {
            final long deadline = time.now + 13_889_000L;
            final long woke = s.sleepUntil(deadline, time.now);

            assertEquals(i + 1, time.parks, "every frame must still park exactly once");
            assertTrue(woke >= deadline, "frame " + i + " left " + (deadline - woke) + "ns to busy-wait; a coarse timer must never hand the frame to the spin loop");
        }

        for (int i = 0; i < 30; i++) {
            final int before = time.parks;
            final long deadline = time.now + 3 * MS;
            final long woke = s.sleepUntil(deadline, time.now);

            assertEquals(before + 1, time.parks, "frame " + i + " must park, not fall through to the spin loop");
            assertTrue(woke >= deadline, "frame " + i + " left " + (deadline - woke) + "ns to busy-wait");
        }
        assertTrue(s.avgOvershootNanos() >= 3 * MS, "the estimate should have grown past the period by now");
    }

    @Test
    void aOneOffStallDoesNotPoisonTheEstimate() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        time.nextParkActual = 500 * MS;
        s.sleepUntil(13 * MS, 0L);

        assertEquals(4 * MS / 10, s.avgOvershootNanos(), "a half-second scheduler stall must be clamped, not averaged in whole");
    }

    @Test
    void eachParkEvictsExactlyOneSlotFromTheEstimate() {
        final PacerTestClock time = new PacerTestClock();
        time.lag = MS;
        final PacerSleeper s = sleeper(time);

        for (int i = 1; i <= 5; i++) {
            s.sleepUntil(time.now + 13 * MS, time.now);
            assertEquals(i * MS / 10, s.avgOvershootNanos(), "the running sum must replace the evicted slot, not accumulate");
        }
        for (int i = 0; i < 10; i++) {
            s.sleepUntil(time.now + 13 * MS, time.now);
        }
        assertEquals(MS, s.avgOvershootNanos(), "a full window of identical lags averages to that lag");
    }

    @Test
    void interruptionAbandonsTheWait() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        Thread.currentThread().interrupt();
        try {
            s.sleepUntil(100 * MS, 0L);
        } finally {
            assertTrue(Thread.interrupted(), "interrupt flag must survive the bail-out");
        }
        assertEquals(0, time.parks);
    }

    @Test
    void theSpinLoopFinishesTheFrameAndIsCounted() {
        final long step = 10_000L;
        final long[] clock = { 0L };
        final int[] parks = { 0 };
        final PacerSleeper s = new PacerSleeper(() -> clock[0] += step, nanos -> {
            parks[0]++;
            clock[0] += nanos;
        }, true);

        final long woke = s.sleepUntil(5 * MS, 0L);
        s.noteFrame(0L, 0L);

        assertEquals(5 * MS, woke, "the spin must carry the frame to the deadline");
        assertEquals(1, parks[0]);
        assertEquals(10_000L, s.lastFrameSpinNanos, "the spin covers what the park left short");
    }

    @Test
    void aGapWiderThanTheFloorIsStillClosedWithoutParking() {
        final long step = 10_000L;
        final long overshoot = 2 * MS;
        final long[] clock = { 0L };
        final int[] parks = { 0 };
        final PacerSleeper s = new PacerSleeper(() -> clock[0] += step, nanos -> {
            parks[0]++;
            clock[0] += nanos + overshoot;
        }, true);

        for (int i = 0; i < PacerSleeper.SLOTS; i++) {
            s.sleepUntil(clock[0] + 13 * MS, clock[0]);
        }
        s.noteFrame(0L, 0L);
        assertTrue(s.avgOvershootNanos() > MS, "the park estimate must exceed the remaining gap for this branch to be reached");

        final long deadline = clock[0] + MS;
        final int before = parks[0];
        final long woke = s.sleepUntil(deadline, clock[0]);
        s.noteFrame(0L, 0L);

        assertEquals(before, parks[0], "a gap under one park quantum must not park again");
        assertTrue(s.lastFrameSpinNanos > 0L, "the spin loop has to close a gap far wider than the floor");
        assertTrue(woke >= deadline, "woke " + woke + "ns short of deadline " + deadline);
    }

    @Test
    void aSteadyPlatformNeedsOnlyTheMinimumSpinFloor() {
        final PacerTestClock time = new PacerTestClock();
        time.lag = 300_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < PacerSleeper.SLOTS; i++) {
            s.sleepUntil(time.now + 13 * MS, time.now);
        }

        assertEquals(PacerSleeper.MIN_SPIN_FLOOR_NANOS, Reflect.<Long>get(s, "spinFloorNanos"), "identical overshoots are pure bias, not jitter, so the floor stays at its minimum");
    }

    @Test
    void aJitteryPlatformWidensTheSpinFloorToCoverTheWorstPark() {
        final PacerTestClock time = new PacerTestClock();
        time.lag = 100_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < PacerSleeper.SLOTS - 1; i++) {
            s.sleepUntil(time.now + 13 * MS, time.now);
        }
        time.lag = 500_000L;
        s.sleepUntil(time.now + 13 * MS, time.now);

        assertEquals(140_000L, s.avgOvershootNanos());
        assertEquals(500_000L - 140_000L, Reflect.<Long>get(s, "spinFloorNanos"), "the floor must cover the spread between the worst park and the mean, not the mean itself");
    }

    @Test
    void aDeadlineInsideTheFloorParksNotAtAll() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        final long woke = s.sleepUntil(COLD_FLOOR, 0L);

        assertEquals(0, time.parks);
        assertEquals(0L, woke);
    }

    @Test
    void everyWaitInAFrameLandsOnTheOneFrameOfBookkeeping() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        s.sleepUntil(10 * MS, 0L);
        s.sleepUntil(time.now + 10 * MS, time.now);
        s.noteFrame(0L, 0L);
        s.sleepUntil(time.now + 10 * MS, time.now);
        s.noteFrame(0L, 0L);

        assertEquals(3, time.parks);
        assertEquals(2L, Reflect.<Long>get(s, "frames"));
        assertEquals(3L, Reflect.<Long>get(s, "parkTotal"));

        s.sleepUntil(time.now + 10 * MS, time.now);
        s.resetStats();
        s.noteFrame(0L, 0L);

        assertEquals(1L, Reflect.<Long>get(s, "frames"));
        assertEquals(0L, Reflect.<Long>get(s, "parkTotal"));
    }

    @Test
    void aWakeInsideTheToleranceIsNotLate() {
        final PacerTestClock time = new PacerTestClock();
        final PacerSleeper s = sleeper(time);

        s.noteFrame(0L, PacerSleeper.LATE_TOL_NANOS);
        s.noteFrame(-5 * MS, 0L);

        assertEquals(0L, Reflect.<Long>get(s, "lateFrames"));
        assertEquals(0L, Reflect.<Long>get(s, "lateMax"));

        s.noteFrame(0L, PacerSleeper.LATE_TOL_NANOS + 1);
        s.noteFrame(0L, 3 * MS);
        s.noteFrame(0L, MS);

        assertEquals(3L, Reflect.<Long>get(s, "lateFrames"));
        assertEquals(3 * MS, Reflect.<Long>get(s, "lateMax"));
    }
}
