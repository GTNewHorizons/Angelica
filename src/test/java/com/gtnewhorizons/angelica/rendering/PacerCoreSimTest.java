package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.ObjIntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerCoreSimTest {

    private static final long US = 1_000L;
    private static final long MS = 1_000_000L;
    private static final int FRAMES = 3000;
    private static final int WARMUP = 300;

    private static final long LAG_MEAN = 30 * US;
    private static final long JITTER = 10 * US;
    private static final long SEED = 987654321L;
    private static final int MAILBOX_IMAGES = 2;

    private static final long P60 = 1_000_000_000L / 60;
    private static final long P5994 = 1_000_000_000L * 1001 / 60_000;
    private static final long P144 = 1_000_000_000L / 144;

    private enum Mode {
        FIFO, MAILBOX, IMMEDIATE
    }

    private enum Gate {
        SWAP, FIRST_CLEAR, NONE
    }

    private static final class FakeClock implements LongSupplier {
        long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class FakeSleeper implements LongConsumer {
        private final FakeClock clock;
        private final Random rng = new Random(SEED);
        private final long granularity;
        int parks;

        FakeSleeper(FakeClock clock, long granularity) {
            this.clock = clock;
            this.granularity = granularity;
        }

        @Override
        public void accept(long request) {
            parks++;
            long actual = request + LAG_MEAN + rng.nextInt(2 * (int) JITTER + 1) - JITTER;
            if (actual < 0L) actual = 0L;
            if (granularity > 0L) actual = Math.max(granularity, (actual + granularity - 1) / granularity * granularity);
            clock.now += actual;
        }
    }

    private static final class Frame {
        final int index;
        final long frameStart;
        final long ready;
        long entry;
        long showTime = -1L;

        Frame(int index, long frameStart, long ready) {
            this.index = index;
            this.frameStart = frameStart;
            this.ready = ready;
        }
    }

    private static final class FakeDisplay {
        long period;
        private final long gpuTail;
        private final int queue;
        private final Gate gate;
        private final Mode mode;
        private final int outageFrom;
        private final int outageTo;
        private final ArrayDeque<Long> scheduled = new ArrayDeque<>();
        final List<Frame> frames = new ArrayList<>();
        private long lastFlip = Long.MIN_VALUE;
        private long deferredGate = Long.MIN_VALUE;

        FakeDisplay(Sim sim) {
            this.period = sim.displayPeriod > 0L ? sim.displayPeriod
                : sim.refreshPeriod + sim.refreshPeriod * sim.driftPpm / 1_000_000L;
            this.gpuTail = sim.gpuTail;
            this.queue = sim.queue;
            this.gate = sim.gate;
            this.mode = sim.mode;
            this.outageFrom = sim.gateOutageFrom;
            this.outageTo = sim.gateOutageTo;
        }

        long present(int index, long frameStart, long entry) {
            final boolean outage = index >= outageFrom && index < outageTo;
            long accepted = entry;
            final int depth = mode == Mode.FIFO ? queue : MAILBOX_IMAGES;
            if (mode != Mode.IMMEDIATE && !outage) {
                purge(accepted);
                while (scheduled.size() >= depth) {
                    accepted = Math.max(accepted, scheduled.peek());
                    purge(accepted);
                }
            }
            final long ready = accepted + gpuTail;
            final Frame frame = new Frame(index, frameStart, ready);
            frame.entry = entry;
            if (mode == Mode.FIFO) {
                final long floor = lastFlip == Long.MIN_VALUE ? ready : Math.max(ready, lastFlip + period);
                final long flip = ceilVblank(floor);
                lastFlip = flip;
                scheduled.add(flip);
                frame.showTime = flip;
            } else if (mode == Mode.IMMEDIATE) {
                frame.showTime = ready;
            } else {
                scheduled.add(ceilVblank(ready));
            }
            if (outage) {
                scheduled.clear();
                lastFlip = Long.MIN_VALUE;
            }
            frames.add(frame);

            if (gate == Gate.FIRST_CLEAR) {
                deferredGate = accepted;
                return entry;
            }
            return accepted;
        }

        long firstDraw(long now) {
            if (gate != Gate.FIRST_CLEAR || deferredGate == Long.MIN_VALUE) return now;
            final long blocked = Math.max(now, deferredGate);
            deferredGate = Long.MIN_VALUE;
            return blocked;
        }

        private void purge(long now) {
            while (!scheduled.isEmpty() && scheduled.peek() <= now) scheduled.poll();
        }

        private long ceilVblank(long time) {
            return Math.floorDiv(time + period - 1, period) * period;
        }

        void resolve(Result result) {
            final long warmupTime = frames.get(WARMUP).frameStart;
            if (mode == Mode.MAILBOX) resolveMailbox(result, warmupTime);
            if (mode == Mode.FIFO) countVblanks(result, warmupTime);
        }

        private void resolveMailbox(Result result, long warmupTime) {
            int i = 0;
            long vblank = ceilVblank(frames.get(0).ready);
            while (i < frames.size()) {
                Frame latest = null;
                while (i < frames.size() && frames.get(i).ready <= vblank) {
                    if (latest != null && latest.index >= WARMUP) result.dropped++;
                    latest = frames.get(i);
                    i++;
                }
                if (latest != null) latest.showTime = vblank;
                else if (vblank >= warmupTime) result.repeats++;
                vblank += period;
            }
        }

        private void countVblanks(Result result, long warmupTime) {
            final long last = frames.get(frames.size() - 1).showTime;
            int next = 0;
            for (long vblank = ceilVblank(warmupTime); vblank <= last; vblank += period) {
                while (next < frames.size() && frames.get(next).showTime < vblank) next++;
                if (next < frames.size() && frames.get(next).showTime == vblank) continue;
                result.repeats++;
                for (final Frame frame : frames) {
                    if (frame.ready <= vblank && frame.showTime > vblank) {
                        result.dups++;
                        break;
                    }
                }
            }
        }
    }

    private static final class Sim {
        long refreshPeriod = P60;
        long displayPeriod;
        boolean tearFree = true;
        boolean vsyncOn = true;
        int cap;
        Mode mode = Mode.FIFO;
        Gate gate = Gate.SWAP;
        int queue = 1;
        int gateOutageFrom = Integer.MAX_VALUE;
        int gateOutageTo = Integer.MAX_VALUE;
        long gpuTail = 500 * US;
        long work = 4 * MS;
        long stallWork;
        int stallEvery;
        int refreshChangeFrame = Integer.MAX_VALUE;
        long refreshChangeTo;
        long granularity;
        long driftPpm;
        ObjIntConsumer<PacerCore> schedule;
        String name = "";

        Sim named(String value) {
            name = value;
            return this;
        }
    }

    private static final class Result {
        String name;
        int dups;
        int repeats;
        int dropped;
        int startParks;
        int lockedStartParks;
        int probeFrames;
        int lockFrame = -1;
        int unlockFrame = -1;
        int relockFrame = -1;
        long cadence;
        long displayPeriod;
        long minInterval;
        long maxInterval;
        long meanInterval;
        long intervalSd;
        long maxStartSleep;
        long latencyP50;
        long latencyP99;
        long margin;
        boolean driverPaced;
        long[] entries;
        long[] cadences;
        long[] margins;
        boolean[] probed;
        boolean[] driven;

        @Override
        public String toString() {
            return name + " [C=" + cadence / 1000 + "us driverPaced=" + driverPaced
                + " dups=" + dups + " repeats=" + repeats + " dropped=" + dropped
                + " startParks=" + startParks + " lockedStartParks=" + lockedStartParks
                + " lockFrame=" + lockFrame + " unlockFrame=" + unlockFrame + " relockFrame=" + relockFrame
                + " probeFrames=" + probeFrames
                + " intervalUs mean=" + meanInterval / 1000 + " sd=" + intervalSd / 1000 + "." + intervalSd % 1000
                + " min=" + minInterval / 1000 + " max=" + maxInterval / 1000
                + " latencyUs p50=" + latencyP50 / 1000 + " p99=" + latencyP99 / 1000
                + " marginUs=" + margin / 1000 + "]";
        }
    }

    private static Result run(Sim sim) {
        final FakeClock clock = new FakeClock();
        final FakeSleeper parker = new FakeSleeper(clock, sim.granularity);
        final PacerSleeper sleeper = new PacerSleeper(clock, parker, false);
        final PacerCore core = new PacerCore(clock, sleeper);
        core.setRefreshPeriodNanos(sim.refreshPeriod);
        core.setTearFree(sim.tearFree);
        core.setVsyncOn(sim.vsyncOn);
        core.setCap(sim.cap);

        final FakeDisplay display = new FakeDisplay(sim);
        final Result result = new Result();
        result.name = sim.name;
        result.displayPeriod = display.period;
        result.entries = new long[FRAMES];
        result.cadences = new long[FRAMES];
        result.margins = new long[FRAMES];
        result.probed = new boolean[FRAMES];
        result.driven = new boolean[FRAMES];
        boolean wasLocked = false;

        for (int f = 0; f < FRAMES; f++) {
            if (sim.schedule != null) sim.schedule.accept(core, f);
            if (f == sim.refreshChangeFrame) {
                core.setRefreshPeriodNanos(sim.refreshChangeTo);
                display.period = sim.refreshChangeTo + sim.refreshChangeTo * sim.driftPpm / 1_000_000L;
            }

            final int parksBefore = parker.parks;
            clock.now = core.beginFrame(clock.now);
            final int startParks = parker.parks - parksBefore;
            if (f >= WARMUP) result.startParks += startParks;
            result.probed[f] = core.probing();
            result.driven[f] = Reflect.<Boolean>invoke(core, "driverPaced", new Class<?>[0]);
            if (core.probing()) result.probeFrames++;
            if (core.locked()) {
                if (result.lockFrame < 0) result.lockFrame = f;
                if (wasLocked == false && result.unlockFrame >= 0 && result.relockFrame < 0) result.relockFrame = f;
                result.lockedStartParks += startParks;
                wasLocked = true;
            } else if (wasLocked) {
                result.unlockFrame = f;
                wasLocked = false;
            }
            if (core.slackNanos() > result.maxStartSleep) result.maxStartSleep = core.slackNanos();
            result.cadences[f] = Reflect.<Long>get(core, "cadenceNanos");
            result.margins[f] = core.marginNanos();

            final long start = clock.now;
            core.markFrameStart(start);

            long firstClearBlockNanos = 0L;
            if (sim.gate == Gate.FIRST_CLEAR) {
                final long blocked = display.firstDraw(clock.now);
                if (blocked > clock.now) {
                    firstClearBlockNanos = blocked - clock.now;
                    core.onGateSample(firstClearBlockNanos, blocked);
                    clock.now = blocked;
                }
            }

            clock.now += sim.stallEvery > 0 && f > 0 && f % sim.stallEvery == 0 ? sim.stallWork : sim.work;

            final long entry = core.beforePresent(clock.now, firstClearBlockNanos);
            clock.now = entry;
            result.entries[f] = entry;
            final long accepted = display.present(f, start, entry);
            if (sim.gate == Gate.SWAP && accepted > entry) {
                core.onGateSample(accepted - entry, accepted);
            }
            clock.now = accepted;
        }

        result.driverPaced = Reflect.<Boolean>invoke(core, "driverPaced", new Class<?>[0]);
        result.cadence = Reflect.<Long>get(core, "cadenceNanos");
        result.margin = core.marginNanos();
        display.resolve(result);
        measure(display.frames, result);
        return result;
    }

    private static void measure(List<Frame> frames, Result result) {
        final List<Long> intervals = new ArrayList<>();
        final List<Long> latencies = new ArrayList<>();
        for (int i = WARMUP; i < frames.size(); i++) {
            final Frame frame = frames.get(i);
            intervals.add(frame.entry - frames.get(i - 1).entry);
            if (frame.showTime >= 0L) latencies.add(frame.showTime - frame.frameStart);
        }

        long sum = 0L;
        result.minInterval = Long.MAX_VALUE;
        for (final long interval : intervals) {
            sum += interval;
            result.minInterval = Math.min(result.minInterval, interval);
            result.maxInterval = Math.max(result.maxInterval, interval);
        }
        result.meanInterval = sum / intervals.size();
        double variance = 0.0;
        for (final long interval : intervals) {
            final double d = interval - (double) result.meanInterval;
            variance += d * d;
        }
        result.intervalSd = (long) Math.sqrt(variance / intervals.size());

        Collections.sort(latencies);
        result.latencyP50 = latencies.get(latencies.size() / 2);
        result.latencyP99 = latencies.get(Math.min(latencies.size() - 1, latencies.size() * 99 / 100));
    }

    private static Sim fifo60(long work, int queue, Gate gate) {
        final Sim sim = new Sim();
        sim.work = work;
        sim.queue = queue;
        sim.gate = gate;
        return sim;
    }

    private static void assertNeverBunches(Result r, long slopNanos) {
        for (int f = WARMUP; f < FRAMES; f++) {
            if (r.driven[f] || r.driven[f - 1]) continue;
            final long interval = r.entries[f] - r.entries[f - 1];
            final long c = r.cadences[f];
            final long unpinned = r.probed[f] ? r.margins[f] : 0L;
            final long floor = c - c / PacerCore.CATCHUP_DIVISOR - slopNanos - unpinned;
            assertTrue(interval >= floor, r + " interval " + interval + "ns at frame " + f + ", floor is " + floor);
            assertTrue(r.maxStartSleep <= 2 * c, r + " start sleep " + r.maxStartSleep + "ns exceeds two periods");
        }
    }

    @Test
    void anHonoredVsyncGateIsLeftToPaceItself() {
        for (final long drift : new long[] { 0L, 1000L }) {
            for (final long work : new long[] { 4 * MS, 10 * MS }) {
                for (final int queue : new int[] { 1, 2 }) {
                    for (final Gate gate : new Gate[] { Gate.SWAP, Gate.FIRST_CLEAR }) {
                        final Sim sim = fifo60(work, queue, gate)
                            .named("fifo60 R=" + work / MS + "ms Q=" + queue + " " + gate + " drift=" + drift);
                        sim.driftPpm = drift;
                        final Result r = run(sim);
                        assertTrue(r.driverPaced, r + " must hand pacing to the driver");
                        assertTrue(r.lockFrame >= 0 && r.lockFrame <= 150, r + " must lock inside the probe window");
                        assertEquals(0, r.dups, r.toString());
                        assertEquals(0, r.repeats, r + " must fill every vblank");
                        assertEquals(0, r.lockedStartParks, r + " must not sleep at frame start once locked");
                    }
                }
            }
        }
    }

    @Test
    void aPanelFasterThanItsReportedRateIsStillFoundByTheProbe() {
        final Sim sim = fifo60(4 * MS, 2, Gate.SWAP).named("lateDrift60 Q=2");
        sim.refreshPeriod = P5994;
        sim.displayPeriod = P60;
        final Result r = run(sim);

        assertTrue(r.lockFrame >= 0 && r.lockFrame <= 150, r + " must lock inside the probe window");
        assertTrue(r.driverPaced, r.toString());
        assertEquals(0, r.repeats, r + " must fill every vblank once locked");
        assertEquals(0, r.lockedStartParks, r.toString());

        final Sim single = fifo60(4 * MS, 1, Gate.SWAP).named("lateDrift60 Q=1");
        single.driftPpm = -1000L;
        final Result rs = run(single);

        assertTrue(rs.displayPeriod < P60, rs + " the panel must run faster than its reported rate");
        assertTrue(rs.lockFrame >= 0 && rs.lockFrame <= 150, rs + " must lock inside the probe window");
        assertTrue(rs.driverPaced, rs.toString());
        assertEquals(0, rs.repeats, rs.toString());
        assertEquals(0, rs.lockedStartParks, rs.toString());
    }

    @Test
    void aDriverThatNeverBlocksFallsBackToTheExactCadenceAfterTheProbe() {
        final Sim sim = fifo60(4 * MS, Integer.MAX_VALUE, Gate.NONE).named("freeRunFifo60");
        final Result r = run(sim);

        assertEquals(-1, r.lockFrame, r + " has no gate to lock onto");

        for (int f = WARMUP; f < FRAMES; f++) {
            final long interval = r.entries[f] - r.entries[f - 1];
            assertTrue(Math.abs(interval - P60) <= 200 * US, r + " interval " + interval + "ns at frame " + f);
        }

        final double vblanks = (r.entries[FRAMES - 1] - r.entries[WARMUP]) / (double) r.displayPeriod;
        final double ratio = (FRAMES - 1 - WARMUP) / vblanks;
        assertTrue(Math.abs(ratio - 1.0) <= 0.01, r + " presented " + ratio + " frames per vblank");
        assertTrue(r.repeats <= 2, r + " repeats");
    }

    @Test
    void aGateOutageReleasesTheLockAndTheProbeFindsItAgain() {
        final Sim sim = fifo60(4 * MS, 1, Gate.SWAP).named("gateOutage60");
        sim.gateOutageFrom = 100;
        sim.gateOutageTo = 100 + PacerCore.LOCK_FRAMES;
        final Result r = run(sim);

        assertTrue(r.lockFrame >= 0 && r.lockFrame < sim.gateOutageFrom, r + " must lock before the outage");
        assertTrue(r.unlockFrame >= sim.gateOutageTo - 1, r + " must hold the lock for the whole lapse window");
        assertTrue(r.relockFrame > r.unlockFrame && r.relockFrame - r.unlockFrame <= 150,
            r + " the re-armed probe must find the gate again");
        assertTrue(r.driverPaced, r.toString());
    }

    @Test
    void aFractionalRefreshRateSwallowsAnIntegerCapOfTheSameRate() {
        final Sim sim = fifo60(4 * MS, 1, Gate.SWAP).named("fifo59.94 cap60");
        sim.refreshPeriod = P5994;
        sim.cap = 60;
        sim.driftPpm = 1000L;
        final Result r = run(sim);

        assertTrue(r.lockFrame >= 0 && r.lockFrame <= 150, r + " must lock inside the probe window");
        assertEquals(P5994, r.cadence, r.toString());
        assertTrue(r.driverPaced, r.toString());
        assertEquals(0, r.dups, r.toString());
        assertEquals(0, r.repeats, r.toString());
    }

    @Test
    void aCapBetweenVblanksSnapsDownToTheNearestMultiple() {
        final Sim sim = fifo60(4 * MS, 1, Gate.SWAP).named("fifo144 cap50");
        sim.refreshPeriod = P144;
        sim.cap = 50;
        sim.gpuTail = 200 * US;
        final Result r = run(sim);

        assertTrue(r.intervalSd < 300 * US, r.toString());
        assertTrue(Math.abs(r.meanInterval - 3 * P144) < 300 * US, r.toString());
    }

    @Test
    void mailboxIsPacedToTheRefreshRateInsteadOfBeatingAgainstIt() {
        final Sim sim = new Sim().named("mailbox144");
        sim.refreshPeriod = P144;
        sim.mode = Mode.MAILBOX;
        sim.gate = Gate.SWAP;
        sim.vsyncOn = false;
        sim.work = 3 * MS;
        sim.gpuTail = MS;
        final Result r = run(sim);

        assertTrue(r.dropped <= 1, r + " must not drop presents it paid to render");
        assertTrue(r.intervalSd < 200 * US, r.toString());
    }

    @Test
    void aTearingCapHoldsItsPeriodAndKeepsLatencyToOneFrameOfWork() {
        final Sim sim = new Sim().named("vsyncOff cap100");
        sim.tearFree = false;
        sim.vsyncOn = false;
        sim.mode = Mode.IMMEDIATE;
        sim.gate = Gate.NONE;
        sim.cap = 100;
        sim.work = 4 * MS;
        sim.gpuTail = 200 * US;
        final Result r = run(sim);

        assertTrue(Math.abs(r.minInterval - 10 * MS) < 200 * US, r.toString());
        assertTrue(Math.abs(r.maxInterval - 10 * MS) < 200 * US, r.toString());
        assertTrue(r.latencyP50 <= sim.work + r.margin + MS, r.toString());
        assertTrue(r.latencyP99 <= sim.work + r.margin + MS, r.toString());
    }

    @Test
    void aFrameSlowerThanTheCadenceIsNeverSleptOn() {
        final Sim sim = new Sim().named("cpuBound R=20ms cap60");
        sim.tearFree = false;
        sim.vsyncOn = false;
        sim.mode = Mode.IMMEDIATE;
        sim.gate = Gate.NONE;
        sim.cap = 60;
        sim.work = 20 * MS;
        sim.gpuTail = 200 * US;
        final Result r = run(sim);

        assertEquals(0, r.startParks, r.toString());
        assertEquals(20 * MS, r.minInterval, r.toString());
        assertEquals(20 * MS, r.maxInterval, r.toString());
    }

    @Test
    void aCoarseSleepTimerWidensTheMarginWithoutSaturatingIt() {
        final Sim sim = new Sim().named("coarseTimer cap60");
        sim.tearFree = false;
        sim.vsyncOn = false;
        sim.mode = Mode.IMMEDIATE;
        sim.gate = Gate.NONE;
        sim.cap = 60;
        sim.granularity = 4 * MS;
        sim.gpuTail = 200 * US;
        final Result r = run(sim);

        assertTrue(r.margin > PacerCore.MARGIN_MIN_NANOS, r + " a coarse timer must buy extra lead");
        assertTrue(r.margin < PacerCore.MARGIN_CEILING_NANOS, r + " but not saturate the margin");
    }

    @Test
    void aPeriodicStallIsWalkedOffWithoutBunchingPresents() {
        final Sim sim = fifo60(8 * MS, 1, Gate.SWAP).named("fifo60 cap30 stall");
        sim.cap = 30;
        sim.stallWork = 200 * MS;
        sim.stallEvery = 300;
        final Result r = run(sim);

        final int stalls = (FRAMES - WARMUP) / sim.stallEvery;
        final long floor = r.cadence - r.cadence / PacerCore.CATCHUP_DIVISOR - PacerSleeper.MIN_SPIN_FLOOR_NANOS - JITTER;
        assertTrue(r.dups <= stalls, r + " allowed " + stalls + " duplicated flips");
        assertTrue(r.minInterval >= floor, r + " floor is " + floor);
    }

    @Test
    void aMidRunCapChangeNeverBunchesPresents() {
        final Sim sim = fifo60(4 * MS, 1, Gate.SWAP).named("capChange 0-30-0");
        sim.schedule = (core, frame) -> {
            if (frame == 1000) core.setCap(30);
            if (frame == 2000) core.setCap(0);
        };
        final Result r = run(sim);

        assertEquals(P60, r.cadences[999], r.toString());
        assertEquals(2 * P60, r.cadences[1500], r.toString());
        assertEquals(P60, r.cadences[2500], r.toString());
        assertNeverBunches(r, PacerSleeper.MIN_SPIN_FLOOR_NANOS + JITTER);
    }

    @Test
    void aMidRunRefreshChangeNeverBunchesPresents() {
        final Sim sim = fifo60(4 * MS, 1, Gate.SWAP).named("refreshChange 60-144");
        sim.refreshChangeFrame = 1500;
        sim.refreshChangeTo = P144;
        final Result r = run(sim);

        assertEquals(P60, r.cadences[1499], r.toString());
        assertEquals(P144, r.cadences[2500], r.toString());
        assertTrue(r.relockFrame > 1500 && r.relockFrame - 1500 <= 150, r + " must find the new panel's gate");
        assertTrue(r.driverPaced, r.toString());
        assertNeverBunches(r, PacerSleeper.MIN_SPIN_FLOOR_NANOS + JITTER);
    }

}
