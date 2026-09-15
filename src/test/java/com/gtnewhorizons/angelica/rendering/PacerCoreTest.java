package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerCoreTest {

    private static final long MS = 1_000_000L;
    private static final long P60 = 1_000_000_000L / 60;
    private static final long P5994 = 1_000_000_000L * 1001 / 60_000;
    private static final long P144 = 1_000_000_000L / 144;

    private static final class Rig {
        final long[] now = { 0L };
        final PacerCore core;
        final List<Long> presents = new ArrayList<>();
        int parks;

        Rig(long refreshPeriodNanos, boolean tearFree, int capHz) {
            this(refreshPeriodNanos, tearFree, tearFree, capHz);
        }

        Rig(long refreshPeriodNanos, boolean tearFree, boolean vsyncOn, int capHz) {
            final PacerSleeper sleeper = new PacerSleeper(() -> now[0], nanos -> {
                parks++;
                now[0] += nanos;
            }, false);
            core = new PacerCore(() -> now[0], sleeper);
            core.setRefreshPeriodNanos(refreshPeriodNanos);
            core.setTearFree(tearFree);
            core.setVsyncOn(vsyncOn);
            core.setCap(capHz);
        }

        long frameWithFirstClear(long blockNanos, long workNanos) {
            if (blockNanos > 0L) {
                core.onGateSample(blockNanos, now[0]);
            }
            now[0] = core.beginFrame(now[0]);
            core.markFrameStart(now[0]);
            now[0] += blockNanos + workNanos;
            final long present = core.beforePresent(now[0], blockNanos);
            now[0] = present;
            presents.add(present);
            return present;
        }

        long frame(long workNanos) {
            now[0] = core.beginFrame(now[0]);
            core.markFrameStart(now[0]);
            now[0] += workNanos;
            final long present = core.beforePresent(now[0], 0L);
            now[0] = present;
            presents.add(present);
            return present;
        }

        void feed(long gateEndNanos, long durationNanos, long atNanos) {
            if (durationNanos > 0L) core.onGateSample(durationNanos, gateEndNanos);
            now[0] = Math.max(now[0], atNanos);
            now[0] = core.beginFrame(now[0]);
        }

        void feedRun(int count, long firstEndNanos, long durationNanos, long strideNanos) {
            long end = firstEndNanos;
            for (int i = 0; i < count; i++) {
                feed(end, durationNanos, end);
                end += strideNanos;
            }
        }

        void idleFrames(int count, long atNanos) {
            for (int i = 0; i < count; i++) {
                now[0] = Math.max(now[0], atNanos + i);
                core.beginFrame(now[0]);
            }
        }
    }

    @Test
    void aCapAtOrAboveTheRefreshRateCollapsesOntoTheRefreshPeriod() {
        assertEquals(P5994, PacerCore.cadencePeriodNanos(60, P5994, true));
        assertEquals(P60, PacerCore.cadencePeriodNanos(58, P60, true));
        assertEquals(P60, PacerCore.cadencePeriodNanos(0, P60, true));
        assertEquals(P60, PacerCore.cadencePeriodNanos(240, P60, true));
    }

    @Test
    void aCapNearAVblankMultipleSnapsToThatMultiple() {
        assertEquals(2 * P60, PacerCore.cadencePeriodNanos(30, P60, true));
        assertEquals(3 * P144, PacerCore.cadencePeriodNanos(50, P144, true));
        assertEquals(48, RenderBackend.hzFromPeriod(3 * P144));
    }

    @Test
    void aCapFarFromEveryMultipleKeepsItsLiteralPeriod() {
        assertEquals(1_000_000_000L / 45, PacerCore.cadencePeriodNanos(45, P60, true));
        assertEquals(1_000_000_000L / 100, PacerCore.cadencePeriodNanos(100, P144, true));
    }

    @Test
    void aTearingModeIgnoresTheRefreshPeriodEntirely() {
        assertEquals(10 * MS, PacerCore.cadencePeriodNanos(100, P60, false));
        assertEquals(0L, PacerCore.cadencePeriodNanos(0, P60, false));
        assertEquals(0L, PacerCore.cadencePeriodNanos(0, 0L, true));
        assertEquals(10 * MS, PacerCore.cadencePeriodNanos(100, 0L, true));
    }

    @Test
    void threeConsistentPairsOnTheRefreshGridLockThePhase() {
        final Rig rig = new Rig(P60, true, 0);
        rig.feedRun(PacerCore.LOCK_PAIRS, 20 * MS, 5 * MS, P60);
        assertFalse(rig.core.locked(), "the last pair of the run has not arrived yet");
        assertTrue(rig.core.probing());

        rig.feed(20 * MS + PacerCore.LOCK_PAIRS * P60, 5 * MS, 20 * MS + PacerCore.LOCK_PAIRS * P60);
        assertTrue(rig.core.locked());
        assertTrue(Reflect.<Boolean>invoke(rig.core, "driverPaced", new Class<?>[0]));
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
        rig.feedRun(PacerCore.LOCK_PAIRS, 20 * MS, 5 * MS, P60);
        long end = 20 * MS + PacerCore.LOCK_PAIRS * P60;

        end += P60 / 2;
        rig.feed(end, 5 * MS, end);
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
        rig.feedRun(PacerCore.LOCK_PAIRS + 1, 20 * MS, 5 * MS, P60);
        assertTrue(rig.core.locked());

        long end = 20 * MS + (PacerCore.LOCK_PAIRS + 1) * P60;
        for (int i = 0; i < PacerCore.LOCK_WINDOW + 1; i++) {
            end += P60 / 2;
            rig.feed(end, 5 * MS, end);
        }
        assertFalse(rig.core.locked(), "a gate that stops landing on the grid must be released");
        assertFalse(rig.core.probing(), "a gate proven off the grid must not be probed again");
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
        assertEquals(2 * P60, Reflect.<Long>get(rig.core, "cadenceNanos"));
        assertFalse(Reflect.<Boolean>invoke(rig.core, "driverPaced", new Class<?>[0]));
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

        assertEquals(7 * MS, Reflect.<Long>get(blocked.core, "cpuHighNanos"));

        final Rig plain = new Rig(P60, false, 60);
        plain.frame(12 * MS);
        plain.frame(4 * MS);

        assertEquals(12 * MS, Reflect.<Long>get(plain.core, "cpuHighNanos"));
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
        final long cpuBefore = Reflect.<Long>get(rig.core, "cpuHighNanos");
        assertEquals(4 * MS, cpuBefore);

        rig.frame(208 * MS);
        rig.frame(4 * MS);
        assertEquals(before, rig.core.marginNanos());
        assertEquals(cpuBefore, Reflect.<Long>get(rig.core, "cpuHighNanos"));

        rig.frame(4 * MS);
        assertEquals(before, rig.core.marginNanos());
        assertEquals(cpuBefore, Reflect.<Long>get(rig.core, "cpuHighNanos"));
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
        assertTrue(Reflect.<Boolean>invoke(rig.core, "driverPaced", new Class<?>[0]));

        final int[] runs = { 0 };
        rig.core.setIdleWork(deadline -> {
            runs[0]++;
            rig.now[0] += MS;
        });
        final int parksBefore = rig.parks;
        for (int i = 0; i < 10; i++) rig.frame(2 * MS);

        assertEquals(10, runs[0], "a driver-paced frame still has idle time before the present target");
        assertEquals(parksBefore, rig.parks, "but it must never sleep");
    }

    @Test
    void theFirstPacedFrameAfterAnUnpacedStretchIsAnchoredWithoutSleeping() {
        final Rig rig = new Rig(P60, false, 0);
        for (int i = 0; i < 5; i++) rig.frame(4 * MS);
        assertEquals(0, rig.parks);

        rig.core.setCap(60);
        rig.frame(4 * MS);
        assertEquals(0, rig.parks, "the anchor frame must not stall the loop");
        assertEquals(0L, rig.core.slackNanos());

        rig.frame(4 * MS);
        assertTrue(rig.parks > 0, "the cadence resumes on the frame after the anchor");
    }

    @Test
    void aTargetMoreThanTwoPeriodsAheadIsPulledBack() {
        final Rig rig = new Rig(P60, false, 60);
        for (int i = 0; i < 20; i++) rig.frame(4 * MS);

        rig.now[0] -= 500 * MS;
        rig.core.beginFrame(rig.now[0]);

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
    void anUnpacedFrameNeverSleeps() {
        final Rig rig = new Rig(P60, false, 0);
        for (int i = 0; i < 50; i++) rig.frame(4 * MS);
        assertEquals(0, rig.parks);
        assertEquals(0L, rig.core.slackNanos());
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
    void mailboxIsNeverProbedAndNeverLocks() {
        final Rig rig = new Rig(P60, true, false, 0);
        for (int i = 0; i < 40; i++) {
            final long end = 20 * MS + i * P60;
            rig.feed(end, 5 * MS, end);
            assertFalse(rig.core.probing());
        }
        assertFalse(rig.core.locked());
        assertEquals(P60, Reflect.<Long>get(rig.core, "cadenceNanos"));
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

        assertEquals(Reflect.<Long>get(plain.core, "cpuHighNanos"), Reflect.<Long>get(blocked.core, "cpuHighNanos"));
        assertEquals(plain.core.marginNanos(), blocked.core.marginNanos());
        assertTrue(blocked.core.marginNanos() < PacerCore.MARGIN_CEILING_NANOS, "the driver block must not saturate the margin");
    }
}
