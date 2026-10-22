package com.gtnewhorizons.angelica.rendering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.rendering.PacerTestClock.MS;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.P144;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.P5994;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.P60;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.cadenceNanos;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.cpuHighNanos;
import static com.gtnewhorizons.angelica.rendering.PacerTestClock.driverPaced;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerCoreTest {

    private static final class Rig {
        final PacerTestClock time = new PacerTestClock();
        final PacerCore core;
        final List<Long> presents = new ArrayList<>();

        Rig(long refreshPeriodNanos, boolean fifo, int capHz) {
            this(refreshPeriodNanos, fifo, fifo, capHz);
        }

        Rig(long refreshPeriodNanos, boolean fifo, boolean vsyncOn, int capHz) {
            final PacerSleeper sleeper = new PacerSleeper(time.clock(), time.parker(), false);
            core = new PacerCore(time.clock(), sleeper);
            core.setRefreshPeriodNanos(refreshPeriodNanos);
            core.setFifo(fifo);
            core.setVsyncOn(vsyncOn);
            core.setCap(capHz);
        }

        long frameWithFirstClear(long blockNanos, long workNanos) {
            if (blockNanos > 0L) {
                core.onGateSample(blockNanos, time.now);
            }
            time.now = core.beginFrame(time.now);
            core.markFrameStart(time.now);
            time.now += blockNanos + workNanos;
            final long present = core.beforePresent(time.now, blockNanos);
            time.now = present;
            presents.add(present);
            return present;
        }

        long frame(long workNanos) {
            time.now = core.beginFrame(time.now);
            core.markFrameStart(time.now);
            time.now += workNanos;
            final long present = core.beforePresent(time.now, 0L);
            time.now = present;
            presents.add(present);
            return present;
        }

        void feed(long gateEndNanos, long durationNanos, long atNanos) {
            if (durationNanos > 0L) core.onGateSample(durationNanos, gateEndNanos);
            time.now = Math.max(time.now, atNanos);
            time.now = core.beginFrame(time.now);
        }

        long feedRun(int count, long firstEndNanos, long durationNanos, long strideNanos) {
            long end = firstEndNanos;
            for (int i = 0; i < count; i++) {
                feed(end, durationNanos, end);
                end += strideNanos;
            }
            return end;
        }

        long feedOffGrid(long endNanos, int count) {
            long end = endNanos;
            for (int i = 0; i < count; i++) {
                end += P60 / 2;
                feed(end, 5 * MS, end);
            }
            return end;
        }

        void idleFrames(int count, long atNanos) {
            for (int i = 0; i < count; i++) {
                time.now = Math.max(time.now, atNanos + i);
                core.beginFrame(time.now);
            }
        }
    }

    static Stream<Arguments> cadencePeriods() {
        return Stream.of(
            Arguments.of("a cap at the refresh rate collapses onto the refresh period", 60, P5994, true, P5994),
            Arguments.of("a cap near the refresh rate collapses onto the refresh period", 58, P60, true, P60),
            Arguments.of("no cap uses the refresh period under fifo", 0, P60, true, P60),
            Arguments.of("a cap above the refresh rate collapses onto the refresh period", 240, P60, true, P60),
            Arguments.of("a cap far from every multiple keeps its literal period", 45, P60, true, 1_000_000_000L / 45),
            Arguments.of("a cap far from every multiple keeps its literal period", 100, P144, true, 1_000_000_000L / 100),
            Arguments.of("a cap near a vblank multiple snaps to that multiple", 30, P60, true, 2 * P60),
            Arguments.of("a cap near a vblank multiple snaps to that multiple", 50, P144, true, 3 * P144),
            Arguments.of("a non-fifo mode ignores the refresh period", 100, P60, false, 10 * MS),
            Arguments.of("a non-fifo mode with no cap is unpaced", 0, P60, false, 0L),
            Arguments.of("a zero refresh period with no cap is unpaced", 0, 0L, true, 0L),
            Arguments.of("a zero refresh period keeps the literal cap", 100, 0L, true, 10 * MS));
    }

    @ParameterizedTest(name = "{0}: cadencePeriodNanos({1}, {2}, {3}) -> {4}")
    @MethodSource("cadencePeriods")
    void cadencePeriodNanos(String name, int capHz, long refreshPeriodNanos, boolean fifo, long expected) {
        assertEquals(expected, PacerCore.cadencePeriodNanos(capHz, refreshPeriodNanos, fifo));
    }

    @Test
    void threeConsistentPairsOnTheRefreshGridLockThePhase() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS, 20 * MS, 5 * MS, P60);
        assertFalse(rig.core.locked(), "the last pair of the run has not arrived yet");
        assertTrue(rig.core.probing());

        rig.feed(20 * MS + PacerCore.LOCK_PAIRS * P60, 5 * MS, 20 * MS + PacerCore.LOCK_PAIRS * P60);
        assertTrue(rig.core.locked());
        assertTrue(driverPaced(rig.core));
        assertFalse(rig.core.probing());
    }

    @Test
    void pairsSpanningSeveralRefreshPeriodsAlsoLock() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, 2 * P60);
        assertTrue(rig.core.locked());
    }

    @Test
    void oneBadPairRestartsTheRunOfConsistentPairs() {
        final Rig rig = new Rig(P60, true, 0);
        long end = rig.feedRun(PacerCore.LOCK_PAIRS, 20 * MS, 5 * MS, P60);

        end = rig.feedOffGrid(end, 1);
        assertFalse(rig.core.locked());

        for (int i = 0; i < PacerCore.LOCK_PAIRS; i++) {
            end += P60;
            rig.feed(end, 5 * MS, end);
        }
        assertTrue(rig.core.locked(), "a fresh run of consistent pairs must still lock");
    }

    @Test
    void aRunOfInconsistentPairsGivesUpOnTheGateEntirely() {
        final Rig rig = new Rig(P60, true, 0);
        final long end = rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());

        rig.feedOffGrid(end, PacerCore.LOCK_WINDOW + 1);
        assertFalse(rig.core.locked(), "a gate that stops landing on the grid must be released");
        assertFalse(rig.core.probing(), "a gate proven off the grid must not be probed again");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "an abandoned gate stays retired through another on-grid run, false, false",
        "invalidate clears an abandoned gate, true, true"})
    void abandonedGate(String label, boolean invalidate, boolean lockedAfter) {
        final Rig rig = new Rig(P60, true, 120);
        long end = rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());
        assertEquals(60, rig.core.effectiveCapHz());

        end = rig.feedOffGrid(end, PacerCore.LOCK_WINDOW + 1);
        assertFalse(rig.core.locked());
        assertFalse(rig.core.probing());

        if (invalidate) rig.core.invalidate();
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, end + P60, 5 * MS, P60);
        assertEquals(lockedAfter, rig.core.locked(), label);
        assertFalse(rig.core.probing());
    }

    @Test
    void samplesFurtherApartThanTheLockWindowNeverPairUp() {
        final Rig rig = new Rig(P60, true, 0);
        long end = 20 * MS;
        for (int i = 0; i < 5; i++) {
            rig.feed(end, 5 * MS, end);
            rig.idleFrames(PacerCore.LOCK_WINDOW + 1, end + 1);
            end += (PacerCore.LOCK_WINDOW + 2) * P60;
        }
        assertFalse(rig.core.locked());
    }

    @Test
    void aSampleShorterThanTheGateThresholdIsNotAPhaseCandidate() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS + 2, 20 * MS, PacerCore.gateThresholdNanos(P60), P60);
        assertFalse(rig.core.locked());
    }

    @Test
    void theLockLapsesAfterSixHundredSampleFreeFrames() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());

        rig.idleFrames(PacerCore.LOCK_FRAMES - 1, 100 * MS);
        assertTrue(rig.core.locked(), "the lock must survive one frame short of the timeout");

        rig.idleFrames(1, 200 * MS);
        assertFalse(rig.core.locked());
    }

    @Test
    void aLockedGateStillLeavesADividedCadencePacedByTheWallClock() {
        final Rig rig = new Rig(P60, true, 30);
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());
        assertEquals(2 * P60, cadenceNanos(rig.core));
        assertFalse(driverPaced(rig.core));
    }

    @Test
    void aTearingModeNeverLocksHoweverRegularTheGateIs() {
        final Rig rig = new Rig(P60, false, true, 60);
        rig.feedRun(20, 20 * MS, 5 * MS, P60);
        assertFalse(rig.core.locked());
        assertFalse(rig.core.probing());
    }

    @Test
    void aFirstClearBlockIsChargedToTheDriverNotToTheFrame() {
        final Rig blocked = new Rig(P60, false, 60);
        blocked.frameWithFirstClear(5 * MS, 7 * MS);
        blocked.frame(4 * MS);

        assertEquals(7 * MS, cpuHighNanos(blocked.core));

        final Rig plain = new Rig(P60, false, 60);
        plain.frame(12 * MS);
        plain.frame(4 * MS);

        assertEquals(12 * MS, cpuHighNanos(plain.core));
    }

    @Test
    void aStallIsWalkedOffAtOneEighthOfAPeriodPerFrameInsteadOfBunching() {
        final Rig rig = new Rig(P60, false, 60);
        final long cadence = 1_000_000_000L / 60;
        for (int i = 0; i < 200; i++) rig.frame(4 * MS);
        final int stallIndex = rig.presents.size();
        rig.frame(208 * MS);
        for (int i = 0; i < 200; i++) rig.frame(4 * MS);

        long min = Long.MAX_VALUE;
        for (int i = stallIndex + 1; i < rig.presents.size(); i++) {
            min = Math.min(min, rig.presents.get(i) - rig.presents.get(i - 1));
        }
        final long floor = cadence - cadence / 8 - PacerSleeper.MIN_SPIN_FLOOR_NANOS;
        assertTrue(min >= floor, "shortest post-stall interval was " + min + "ns, floor is " + floor);
        assertTrue(min < cadence, "the pacer must actually claw back the stall, min interval was " + min);
    }

    @Test
    void aStallFrameIsKeptOutOfTheMarginWindows() {
        final Rig rig = new Rig(P60, false, 60);
        for (int i = 0; i < 200; i++) rig.frame(4 * MS);
        final long before = rig.core.marginNanos();
        final long cpuBefore = cpuHighNanos(rig.core);
        assertEquals(4 * MS, cpuBefore);

        rig.frame(208 * MS);
        rig.frame(4 * MS);
        assertEquals(before, rig.core.marginNanos());
        assertEquals(cpuBefore, cpuHighNanos(rig.core));

        rig.frame(4 * MS);
        assertEquals(before, rig.core.marginNanos());
        assertEquals(cpuBefore, cpuHighNanos(rig.core));
    }

    @Test
    void aFrameThatStartsLateWidensTheMargin() {
        final Rig rig = new Rig(P60, false, 60);
        for (int i = 0; i < 300; i++) rig.frame(4 * MS);
        final long before = rig.core.marginNanos();
        assertEquals(PacerCore.MARGIN_MIN_NANOS, before);

        for (int i = 0; i < PacerCore.LOCK_PAIRS + 1; i++) {
            rig.frame(6 * MS);
            for (int j = 0; j < 20; j++) rig.frame(4 * MS);
        }

        final long after = rig.core.marginNanos();
        assertTrue(after > before, "an overrunning frame must buy more lead, margin is " + after);
        assertTrue(after < PacerCore.MARGIN_CEILING_NANOS, "a 2ms overrun must not saturate the margin, margin is " + after);
    }

    @Test
    void idleWorkStillRunsWhenTheDriverPaces() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(driverPaced(rig.core));

        final int[] runs = { 0 };
        rig.core.setIdleWork(deadline -> {
            runs[0]++;
            rig.time.now += MS;
        });
        final int parksBefore = rig.time.parks;
        for (int i = 0; i < 10; i++) rig.frame(2 * MS);

        assertEquals(10, runs[0], "a driver-paced frame still has idle time before the present target");
        assertEquals(parksBefore, rig.time.parks, "but it must never sleep");
    }

    @Test
    void theFirstPacedFrameAfterAnUnpacedStretchIsAnchoredWithoutSleeping() {
        final Rig rig = new Rig(P60, false, 0);
        for (int i = 0; i < 50; i++) rig.frame(4 * MS);
        assertEquals(0, rig.time.parks);
        assertEquals(0L, rig.core.slackNanos());

        rig.core.setCap(60);
        rig.frame(4 * MS);
        assertEquals(0, rig.time.parks, "the anchor frame must not stall the loop");
        assertEquals(0L, rig.core.slackNanos());

        rig.frame(4 * MS);
        assertTrue(rig.time.parks > 0, "the cadence resumes on the frame after the anchor");
    }

    @Test
    void aTargetMoreThanTwoPeriodsAheadIsPulledBack() {
        final Rig rig = new Rig(P60, false, 60);
        for (int i = 0; i < 20; i++) rig.frame(4 * MS);

        rig.time.now -= 500 * MS;
        rig.core.beginFrame(rig.time.now);

        assertTrue(rig.core.slackNanos() <= P60, "a backward clock step must not park for half a second: " + rig.core.slackNanos());
    }

    @Test
    void theEffectiveCapReportsTheCadenceThePacerActuallyRuns() {
        final Rig unpaced = new Rig(0L, false, 0);
        unpaced.frame(4 * MS);
        assertEquals(0, unpaced.core.effectiveCapHz());
        assertFalse(unpaced.core.paced());

        final Rig snapped = new Rig(P144, true, 50);
        snapped.frame(4 * MS);
        assertEquals(48, snapped.core.effectiveCapHz());
        assertTrue(snapped.core.paced());
    }

    @Test
    void theMarginIsClampedIntoItsBandByTheCadence() {
        assertEquals(PacerCore.MARGIN_MIN_NANOS, PacerCore.clampMargin(0L, 16_666_666L));
        assertEquals(PacerCore.MARGIN_CEILING_NANOS, PacerCore.clampMargin(50 * MS, 100 * MS));
        assertEquals(2 * MS, PacerCore.clampMargin(2 * MS, 16_666_666L));
        assertEquals(P144 / 4, PacerCore.clampMargin(3 * MS, P144), "a short cadence caps the margin below its own minimum");
    }

    @Test
    void theProbeRunsAheadOfThePanelOnlyUntilItHasGainedTwoPeriods() {
        final Rig rig = new Rig(P60, true, 0);
        for (int i = 0; i < 400; i++) rig.frame(4 * MS);
        final long lead = P60 / PacerCore.PROBE_LEAD_DIVISOR;

        assertEquals(P60 - lead, rig.presents.get(10) - rig.presents.get(9), "the probe must present ahead of the panel");
        assertEquals(P60, rig.presents.get(300) - rig.presents.get(299), "an expired probe must fall back to the exact cadence");
    }

    @Test
    void anExpiredProbeLeadStillLocksWhenTheGateArrives() {
        final Rig rig = new Rig(P60, true, 0);
        for (int i = 0; i < 20; i++) rig.frame(4 * MS);
        assertFalse(rig.core.locked());
        assertFalse(rig.core.probing());

        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());
        assertFalse(rig.core.probing());
    }

    @Test
    void mailboxIsNeverProbedNeverLocksAndIsUncapped() {
        final Rig rig = new Rig(P60, false, false, 0);
        for (int i = 0; i < 40; i++) {
            final long end = 20 * MS + i * P60;
            rig.feed(end, 5 * MS, end);
            assertFalse(rig.core.probing());
        }
        assertFalse(rig.core.locked());
        assertFalse(rig.core.paced(), "mailbox must render past the refresh rate");
        assertEquals(0L, cadenceNanos(rig.core));
    }

    @Test
    void aMailboxCapIsLiteralRatherThanSnappedToTheRefreshRate() {
        final Rig rig = new Rig(P60, false, false, 240);
        rig.frame(MS);
        assertEquals(1_000_000_000L / 240, cadenceNanos(rig.core));
    }

    @Test
    void aCapChangeRearmsTheProbe() {
        final Rig rig = new Rig(P60, true, 0);
        for (int i = 0; i < 200; i++) rig.frame(4 * MS);
        assertFalse(rig.core.probing());

        rig.core.setCap(45);
        rig.frame(4 * MS);
        assertFalse(rig.core.probing(), "a cadence off the refresh period is never probed");

        rig.core.setCap(0);
        rig.frame(4 * MS);
        assertTrue(rig.core.probing());
    }

    @Test
    void aRefreshChangeRearmsTheProbe() {
        final Rig rig = new Rig(P60, true, 0);
        for (int i = 0; i < 200; i++) rig.frame(4 * MS);
        assertFalse(rig.core.probing());

        rig.core.setRefreshPeriodNanos(P144);
        rig.frame(4 * MS);
        assertTrue(rig.core.probing());
    }

    @Test
    void aFirstClearBlockIsKeptOutOfTheStartResidualToo() {
        final Rig plain = new Rig(P60, false, 60);
        final Rig blocked = new Rig(P60, false, 60);
        for (int i = 0; i < 300; i++) {
            plain.frame(4 * MS);
            blocked.frameWithFirstClear(5 * MS, 4 * MS);
        }

        assertEquals(cpuHighNanos(plain.core), cpuHighNanos(blocked.core));
        assertEquals(plain.core.marginNanos(), blocked.core.marginNanos());
        assertTrue(blocked.core.marginNanos() < PacerCore.MARGIN_CEILING_NANOS, "the driver block must not saturate the margin");
    }
}
