package com.gtnewhorizons.angelica.rendering;

import com.google.common.primitives.Longs;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class PacerCore {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MARGIN_BASE_NANOS = 500_000L;
    static final long MARGIN_MIN_NANOS = 1_000_000L;
    static final long MARGIN_CEILING_NANOS = 4_000_000L;
    private static final long GATE_THRESHOLD_FLOOR_NANOS = 100_000L;
    private static final long IDLE_MIN_BUDGET_NANOS = 500_000L;
    private static final long PRESENT_SLOP_NANOS = 50_000L;

    private static final int PROBE_LEAD_PERIODS = 2;
    static final int PROBE_LEAD_DIVISOR = 8;
    static final int LOCK_PAIRS = 3;
    static final int LOCK_FRAMES = 600;
    static final int LOCK_WINDOW = 16;
    private static final int CAP_EQ_DIVISOR = 32;
    private static final int SNAP_TOL_DIVISOR = 8;
    static final int CATCHUP_DIVISOR = 8;
    private static final int GATE_THRESHOLD_DIVISOR = 32;
    private static final int LOCK_TOL_DIVISOR = 8;
    private static final int MARGIN_MAX_DIVISOR = 4;
    private static final int STALL_MULTIPLE = 2;
    private static final int TARGET_RESET_PERIODS = 2;

    private static final int CPU_SLOTS = 16;
    private static final int OVERRUN_SLOTS = 128;

    private final LongSupplier clock;
    private final PacerSleeper sleeper;
    private LongConsumer idleWork;

    private long refreshPeriodNanos;
    private boolean tearFree = true;
    private boolean vsyncOn = true;
    private int capHz;

    private long cadenceNanos;
    private long targetNanos;
    private boolean haveTarget;
    private long startDeadlineNanos;
    private long pEffPrevNanos;
    private boolean havePEffPrev;
    private long frameStartNanos;
    private long lastPresentCallNanos;
    private boolean havePresent;
    private long lastPresentIntervalNanos;

    private final long[] cpuRing = new long[CPU_SLOTS];
    private final long[] residualRing = new long[OVERRUN_SLOTS];
    private final long[] idleRing = new long[OVERRUN_SLOTS];
    private int cpuSlot;
    private int residualSlot;
    private int idleSlot;

    private boolean locked;
    private boolean probing;
    private boolean probeExhausted;
    private long probeLeadNanos;
    private boolean haveCandidate;
    private long candidateEndNanos;
    private int framesSinceCandidate;
    private int consistentPairs;
    private int inconsistentPairs;

    private boolean pendingSample;
    private long pendingDurationNanos;
    private long pendingEndNanos;
    private long pendingGpuWaitNanos;
    private long lastGateEndNanos;
    private boolean haveGateEnd;

    private long cpuHighNanos;
    private long marginNanos;
    private long slackNanos;
    private long lastGpuWaitNanos;
    private long lastIdleOvershootNanos;

    private boolean frameDriverPaced;
    private boolean frameProbing;
    private boolean frameAnchor;
    private long frameWakeLateNanos;

    PacerCore(LongSupplier clock, PacerSleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    void setIdleWork(LongConsumer work) {
        this.idleWork = work;
    }

    void invalidate() {
        dropLock();
    }

    void setRefreshPeriodNanos(long periodNanos) {
        final long p = Math.max(periodNanos, 0L);
        if (p == refreshPeriodNanos) return;
        refreshPeriodNanos = p;
        dropLock();
    }

    void setTearFree(boolean value) {
        if (value == tearFree) return;
        tearFree = value;
        dropLock();
    }

    void setVsyncOn(boolean value) {
        if (value == vsyncOn) return;
        vsyncOn = value;
        dropLock();
    }

    void setCap(int hz) {
        final int cap = Math.max(hz, 0);
        if (cap == capHz) return;
        capHz = cap;
        armProbe();
    }

    void onGpuWait(long nanos) {
        if (nanos > 0L) pendingGpuWaitNanos += nanos;
    }

    void onGateSample(long durationNanos, long endNanos) {
        if (durationNanos <= 0L) return;
        pendingSample = true;
        pendingDurationNanos = durationNanos;
        pendingEndNanos = endNanos;
    }

    long beginFrame(long now) {
        final boolean sample = pendingSample && (!haveGateEnd || pendingEndNanos > lastGateEndNanos);
        final long sampleEnd = sample ? pendingEndNanos : 0L;
        final long sampleDuration = sample ? pendingDurationNanos : 0L;
        if (sample) {
            lastGateEndNanos = pendingEndNanos;
            haveGateEnd = true;
        }
        pendingSample = false;

        if (sample && (!havePresent || sampleEnd > lastPresentCallNanos)) {
            pEffPrevNanos = sampleEnd;
            havePEffPrev = true;
        } else if (havePresent) {
            pEffPrevNanos = lastPresentCallNanos;
            havePEffPrev = true;
        }
        lastGpuWaitNanos = pendingGpuWaitNanos;
        pendingGpuWaitNanos = 0L;

        final long c = cadencePeriodNanos(capHz, refreshPeriodNanos, tearFree);
        final boolean candidate = sample && vsyncOn && tearFree && refreshPeriodNanos > 0L
            && sampleDuration > gateThresholdNanos(c);
        updateLock(candidate, sampleEnd);

        cadenceNanos = c;
        frameWakeLateNanos = 0L;
        if (c == 0L) {
            probing = false;
            frameProbing = false;
            frameDriverPaced = false;
            frameAnchor = false;
            haveTarget = false;
            armProbe();
            startDeadlineNanos = now;
            slackNanos = 0L;
            marginNanos = 0L;
            lastIdleOvershootNanos = 0L;
            return now;
        }

        probing = vsyncOn && !locked && !probeExhausted && c == refreshPeriodNanos;
        final long step = probing ? c - c / PROBE_LEAD_DIVISOR : c;
        if (probing) {
            probeLeadNanos += c - step;
            probeExhausted = probeLeadNanos >= PROBE_LEAD_PERIODS * c;
        }

        frameAnchor = !haveTarget;
        if (frameAnchor) {
            targetNanos = now;
            haveTarget = true;
        } else if (locked && candidate) {
            long m = (now - sampleEnd) / c + 1L;
            if (m < 1L) m = 1L;
            targetNanos = sampleEnd + m * c;
        } else {
            long advanced = targetNanos + step;
            if (havePEffPrev) {
                final long floor = pEffPrevNanos + c - c / CATCHUP_DIVISOR;
                if (floor > advanced) advanced = floor;
            }
            targetNanos = advanced;
        }
        if (targetNanos - now > TARGET_RESET_PERIODS * c) targetNanos = now + c;

        frameDriverPaced = driverPaced();
        frameProbing = probing;
        cpuHighNanos = Math.max(0L, Longs.max(cpuRing));
        marginNanos = clampMargin(MARGIN_BASE_NANOS + Longs.max(residualRing), c);
        final long justInTime = targetNanos - cpuHighNanos - marginNanos;
        startDeadlineNanos = frameDriverPaced || frameAnchor ? now : justInTime;

        final long idleDeadline = frameDriverPaced || frameAnchor ? justInTime : startDeadlineNanos;
        final LongConsumer work = idleWork;
        lastIdleOvershootNanos = 0L;
        if (work != null && idleDeadline - now - Longs.max(idleRing) > IDLE_MIN_BUDGET_NANOS) {
            work.accept(idleDeadline);
            final long idleEnd = clock.getAsLong();
            lastIdleOvershootNanos = Math.max(0L, idleEnd - idleDeadline);
            idleRing[idleSlot] = lastIdleOvershootNanos;
            idleSlot = (idleSlot + 1) % OVERRUN_SLOTS;
            now = idleEnd;
        }

        slackNanos = startDeadlineNanos - now;
        if (now < startDeadlineNanos) {
            now = sleeper.sleepUntil(startDeadlineNanos, now);
            if (now > startDeadlineNanos) frameWakeLateNanos = now - startDeadlineNanos;
        }
        return now;
    }

    void markFrameStart(long now) {
        frameStartNanos = now;
    }

    long beforePresent(long now, long firstClearBlockNanos) {
        final long c = cadenceNanos;
        if (c > 0L) {
            final long cpu = now - frameStartNanos - firstClearBlockNanos;
            if (cpu <= c * STALL_MULTIPLE) {
                cpuRing[cpuSlot] = Math.min(cpu, c);
                cpuSlot = (cpuSlot + 1) % CPU_SLOTS;
                residualRing[residualSlot] = cpu - cpuHighNanos - (startDeadlineNanos - frameStartNanos);
                residualSlot = (residualSlot + 1) % OVERRUN_SLOTS;
            } else {
                armProbe();
            }

            if (!frameDriverPaced && !frameProbing && !frameAnchor) {
                if (targetNanos - now > PRESENT_SLOP_NANOS) {
                    now = sleeper.sleepUntil(targetNanos, now);
                    final long late = now - targetNanos;
                    if (late > frameWakeLateNanos) frameWakeLateNanos = late;
                }
            }
            sleeper.noteFrame(slackNanos, frameWakeLateNanos);
        }

        lastPresentIntervalNanos = havePresent ? now - lastPresentCallNanos : 0L;
        lastPresentCallNanos = now;
        havePresent = true;
        return now;
    }

    boolean paced() {
        return cadenceNanos > 0L;
    }

    boolean locked() {
        return locked;
    }

    boolean probing() {
        return probing;
    }

    private boolean driverPaced() {
        return locked && cadenceNanos > 0L && cadenceNanos == refreshPeriodNanos;
    }


    int effectiveCapHz() {
        return cadenceNanos == 0L ? 0 : RenderBackend.hzFromPeriod(cadenceNanos);
    }

    long marginNanos() {
        return marginNanos;
    }


    long slackNanos() {
        return slackNanos;
    }

    long lastGpuWaitNanos() {
        return lastGpuWaitNanos;
    }

    long lastIdleOvershootNanos() {
        return lastIdleOvershootNanos;
    }

    long lastPresentIntervalNanos() {
        return lastPresentIntervalNanos;
    }

    static long cadencePeriodNanos(int capHz, long refreshPeriodNanos, boolean tearFree) {
        final long cap = Math.max(capHz, 0);
        if (tearFree && refreshPeriodNanos > 0L) {
            if (cap == 0L || cap * refreshPeriodNanos * CAP_EQ_DIVISOR >= NANOS_PER_SECOND * (CAP_EQ_DIVISOR - 1)) {
                return refreshPeriodNanos;
            }
            final long capPeriod = NANOS_PER_SECOND / cap;
            long k = (capPeriod + refreshPeriodNanos / 2) / refreshPeriodNanos;
            if (k < 1L) k = 1L;
            final long snapped = k * refreshPeriodNanos;
            return Math.abs(snapped - capPeriod) <= capPeriod / SNAP_TOL_DIVISOR ? snapped : capPeriod;
        }
        return cap > 0L ? NANOS_PER_SECOND / cap : 0L;
    }

    static long gateThresholdNanos(long cadenceNanos) {
        return Math.max(GATE_THRESHOLD_FLOOR_NANOS, cadenceNanos / GATE_THRESHOLD_DIVISOR);
    }

    static long clampMargin(long value, long cadenceNanos) {
        final long ceiling = Math.min(cadenceNanos / MARGIN_MAX_DIVISOR, MARGIN_CEILING_NANOS);
        return Math.min(Math.max(value, MARGIN_MIN_NANOS), ceiling);
    }

    private void updateLock(boolean candidate, long sampleEnd) {
        if (!candidate) {
            if (framesSinceCandidate < Integer.MAX_VALUE) framesSinceCandidate++;
            if (locked && framesSinceCandidate >= LOCK_FRAMES) dropLock();
            return;
        }
        if (haveCandidate && framesSinceCandidate <= LOCK_WINDOW) {
            final long delta = sampleEnd - candidateEndNanos;
            final long k = (delta + refreshPeriodNanos / 2) / refreshPeriodNanos;
            if (k >= 1L && Math.abs(delta - k * refreshPeriodNanos) <= refreshPeriodNanos / LOCK_TOL_DIVISOR) {
                inconsistentPairs = 0;
                if (++consistentPairs >= LOCK_PAIRS) locked = true;
            } else {
                consistentPairs = 0;
                if (++inconsistentPairs >= LOCK_WINDOW) {
                    dropLock();
                    probeExhausted = true;
                }
            }
        }
        candidateEndNanos = sampleEnd;
        haveCandidate = true;
        framesSinceCandidate = 0;
    }

    private void armProbe() {
        probeLeadNanos = 0L;
        probeExhausted = false;
    }

    private void dropLock() {
        armProbe();
        locked = false;
        haveCandidate = false;
        candidateEndNanos = 0L;
        framesSinceCandidate = 0;
        consistentPairs = 0;
        inconsistentPairs = 0;
    }


}
