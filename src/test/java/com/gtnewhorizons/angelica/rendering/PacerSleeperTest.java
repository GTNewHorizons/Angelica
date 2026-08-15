package com.gtnewhorizons.angelica.rendering;

import org.junit.jupiter.api.Test;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerSleeperTest {

    private static final long MS = 1_000_000L;
    private static final long SPIN_FLOOR = 100_000L;

    private static final class FakeTime {
        long now;
        long lag;
        long granularity;
        long nextParkActual = -1;
        int parks;
        long lastRequest;

        LongSupplier clock() {
            return () -> now;
        }

        LongConsumer parker() {
            return nanos -> {
                parks++;
                lastRequest = nanos;
                final long actual;
                if (nextParkActual >= 0) {
                    actual = nextParkActual;
                    nextParkActual = -1;
                } else {
                    actual = Math.max(nanos + lag, granularity);
                }
                now += actual;
            };
        }
    }

    private static PacerSleeper sleeper(FakeTime time) {
        return new PacerSleeper(time.clock(), time.parker(), false);
    }

    @Test
    void aLongWaitCostsOneParkNotAChunkPerMillisecond() {
        final FakeTime time = new FakeTime();
        final PacerSleeper s = sleeper(time);

        s.sleepUntil(10 * MS, 0L);

        assertEquals(1, time.parks, "the request must be sized to the deadline, not to a fixed chunk");
        assertEquals(10 * MS - SPIN_FLOOR, time.lastRequest, "the park should land one spin floor short of the deadline");
    }

    @Test
    void whatIsLeftForTheSpinNeverExceedsTheFloor() {
        final FakeTime time = new FakeTime();
        time.lag = 20_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < 20; i++) {
            final long deadline = time.now + 13 * MS;
            final long woke = s.sleepUntil(deadline, time.now);
            assertTrue(deadline - woke <= SPIN_FLOOR,
                "left " + (deadline - woke) + "ns to spin, floor is " + SPIN_FLOOR);
        }
    }

    @Test
    void theLagEstimateConvergesSoTheDeadlineIsNotOvershot() {
        final FakeTime time = new FakeTime();
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
        final FakeTime time = new FakeTime();
        final PacerSleeper s = sleeper(time);
        time.nextParkActual = 2 * MS;

        final long woke = s.sleepUntil(10 * MS, 0L);

        assertEquals(2, time.parks, "a short park must be followed by another, not by a spin");
        assertTrue(10 * MS - woke <= SPIN_FLOOR, "the second park should still land inside the floor");
    }

    @Test
    void aCoarseTimerParksLateRatherThanSpinningOutTheFrame() {
        final FakeTime time = new FakeTime();
        time.granularity = 15_600_000L;
        final PacerSleeper s = sleeper(time);

        for (int i = 0; i < 30; i++) {
            final long deadline = time.now + 13_889_000L;
            final long woke = s.sleepUntil(deadline, time.now);

            assertEquals(i + 1, time.parks, "every frame must still park exactly once");
            assertTrue(woke >= deadline, "frame " + i + " left " + (deadline - woke) + "ns to busy-wait; a coarse timer must never hand the frame to the spin loop");
        }
    }

    @Test
    void aPeriodShorterThanTheWakeUpLagStillParksRatherThanSpinning() {
        final FakeTime time = new FakeTime();
        time.granularity = 15_600_000L;
        final PacerSleeper s = sleeper(time);

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
        final FakeTime time = new FakeTime();
        final PacerSleeper s = sleeper(time);

        time.nextParkActual = 500 * MS;
        s.sleepUntil(13 * MS, 0L);

        assertEquals(4 * MS / 10, s.avgOvershootNanos(), "a half-second scheduler stall must be clamped, not averaged in whole");
    }

    @Test
    void eachParkEvictsExactlyOneSlotFromTheEstimate() {
        final FakeTime time = new FakeTime();
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
        final FakeTime time = new FakeTime();
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
    void aDeadlineInsideTheFloorParksNotAtAll() {
        final FakeTime time = new FakeTime();
        final PacerSleeper s = sleeper(time);

        final long woke = s.sleepUntil(SPIN_FLOOR, 0L);

        assertEquals(0, time.parks);
        assertEquals(0L, woke);
    }
}
