package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;

import java.util.concurrent.locks.LockSupport;

public final class FramePacer {

    public interface IdleWork {
        void run(long deadlineNanos);
    }

    private static final int MIN_PLAUSIBLE_REFRESH_HZ = 20;
    private static final int MAX_PLAUSIBLE_REFRESH_HZ = 1000;
    private static final long IDLE_WORK_MIN_BUDGET_NANOS = 500_000L;
    private static final long GATE_BLOCK_MIN_NANOS = 100_000L;
    private static final int GATE_STREAK_FRAMES = 60;
    static final int GATE_SETTLE_FRAMES = 120;
    static final int PACING_HEADROOM_PERCENT = 5;

    private static final Tracy.ZoneId Z_PACER_WAIT = Tracy.zoneId("pacerBackpressure", Tracy.COLOR_SWAP);
    private static final Tracy.ZoneId Z_PACER_IDLE = Tracy.zoneId("pacerIdleWork", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_PACER_SLEEP = Tracy.zoneId("pacerSleep", Tracy.COLOR_SWAP);
    private static final long P_FRAME_GATE_US = Tracy.plotHandle("frameGateUs");
    private static final long P_SLACK_US = Tracy.plotHandle("pacer.slackUs");
    private static final long P_SPIN_US = Tracy.plotHandle("pacer.spinUs");
    private static final long P_CEILING_HZ = Tracy.plotHandle("pacer.ceilingHz");
    private static final long P_GATE_ACTIVE = Tracy.plotHandle("pacer.gateActive");

    private static long deadline;
    private static long lastFrameTime;

    private static boolean warnedUncapped;
    private static boolean pacedLastFrame;
    private static boolean statsActive;

    private static VSyncMode lastVSyncMode = VSyncMode.ON;
    private static int lastCapHz;
    private static int lastRefreshHz;

    private static IdleWork idleWork;
    private static PacerSleeper sleeper = newSleeper();
    private static int gateStreak;
    private static Boolean settledBlocking;
    private static boolean candidateBlocking;
    private static int candidateFrames;
    private static Boolean loggedBlocking;
    private static VSyncMode loggedMode;
    private static int loggedRefreshHz;
    private static int loggedCapHz;

    private static long lastSeenGateEnd;

    private FramePacer() {}

    private static PacerSleeper newSleeper() {
        return new PacerSleeper(System::nanoTime, LockSupport::parkNanos, true);
    }

    public static void setIdleWork(IdleWork work) {
        idleWork = work;
    }

    public static boolean pacedLastFrame() {
        return pacedLastFrame;
    }

    public static void beginStats() {
        sleeper.resetStats();
        statsActive = true;
    }

    public static String endStats() {
        statsActive = false;
        final String summary = sleeper.summary(configLine());
        Tracy.message(summary);
        return summary;
    }

    static boolean statsActive() {
        return statsActive;
    }

    static String configLine(VSyncMode mode, int refreshHz, int capHz, int ceilingHz) {
        return "cfg=[" + mode + " refresh=" + refreshHz + "Hz cap=" + capHz + " ceiling=" + ceilingHz + "Hz]";
    }

    private static String configLine() {
        return configLine(lastVSyncMode, lastRefreshHz, lastCapHz,
            pacingCeilingHz(lastVSyncMode, lastCapHz, lastRefreshHz, gateActive()));
    }

    public static int plausibleRefreshHz(int hz) {
        return hz >= MIN_PLAUSIBLE_REFRESH_HZ && hz <= MAX_PLAUSIBLE_REFRESH_HZ ? hz : 0;
    }

    public static int withHeadroom(int hz, int percent) {
        if (hz <= 0) return 0;
        return (int) Math.max(hz + 1L, Math.round(hz * (100 + percent) / 100.0));
    }

    public static int pacingCeilingHz(VSyncMode mode, int capHz, int refreshHz, boolean gateActive) {
        final int cap = Math.max(capHz, 0);
        final int backstop = backstopHz(mode, refreshHz, gateActive);
        if (cap == 0) return backstop;
        if (backstop == 0) return cap;
        return Math.min(cap, backstop);
    }

    static int backstopHz(VSyncMode mode, int refreshHz, boolean gateActive) {
        if (!mode.tearFree()) return 0;
        final int refresh = plausibleRefreshHz(refreshHz);
        if (mode == VSyncMode.ON && gateActive) return withHeadroom(refresh, PACING_HEADROOM_PERCENT);
        return refresh;
    }

    static boolean observeGate(long gateNanos, long thresholdNanos) {
        if (gateNanos > thresholdNanos) gateStreak = GATE_STREAK_FRAMES;
        else if (gateStreak > 0) gateStreak--;
        return gateStreak > 0;
    }

    static boolean gateActive() {
        return gateStreak > 0;
    }

    static Boolean settleGateBlocking(boolean observed) {
        if (settledBlocking != null && observed == settledBlocking) {
            candidateFrames = 0;
        } else if (candidateFrames == 0 || observed != candidateBlocking) {
            candidateBlocking = observed;
            candidateFrames = 1;
        } else if (++candidateFrames >= GATE_SETTLE_FRAMES) {
            settledBlocking = observed;
            candidateFrames = 0;
        }
        return settledBlocking;
    }

    static boolean vsyncNotHonoured(VSyncMode mode, boolean gateBlocking) {
        return mode == VSyncMode.ON && !gateBlocking;
    }

    static boolean pacingStatusChanged(boolean blocking, VSyncMode mode, int refreshHz, int capHz) {
        final Boolean settled = settleGateBlocking(blocking);
        if (settled == null) return false;
        if (settled.equals(loggedBlocking) && mode == loggedMode && refreshHz == loggedRefreshHz && capHz == loggedCapHz) {
            return false;
        }
        loggedBlocking = settled;
        loggedMode = mode;
        loggedRefreshHz = refreshHz;
        loggedCapHz = capHz;
        return true;
    }

    static String pacingLine(VSyncMode mode, int refreshHz, int capHz, boolean gateBlocking) {
        final StringBuilder sb = new StringBuilder("Frame pacing: ");
        sb.append(switch (mode) {
            case OFF -> "vsync off";
            case MAILBOX -> "mailbox";
            case ON -> "vsync";
        });
        if (mode.tearFree() && refreshHz > 0) sb.append(' ').append(refreshHz).append("Hz");
        if (capHz > 0) sb.append(", cap ").append(capHz);
        if (vsyncNotHonoured(mode, gateBlocking)) sb.append(", driver not blocking, paced by wall clock");
        else if (gateBlocking) sb.append(", hardware paced");
        return sb.toString();
    }

    static long gateBlockThresholdNanos(long targetNanos) {
        return Math.max(GATE_BLOCK_MIN_NANOS, targetNanos / 32);
    }

    public static long nextDeadline(long previousDeadline, long now, long targetNanos) {
        if (previousDeadline == 0L || now - previousDeadline > 2 * targetNanos) return now + targetNanos;
        return previousDeadline + targetNanos;
    }

    public static long endFrame(int capHz, Runnable renderAheadWait) {
        lastVSyncMode = GLStateManager.getEffectiveVSyncMode();
        lastCapHz = Math.max(capHz, 0);
        lastRefreshHz = refreshHz();
        return pace(pacingCeilingHz(lastVSyncMode, lastCapHz, lastRefreshHz, gateActive()), renderAheadWait);
    }

    public static long pace(int ceilingHz, Runnable renderAheadWait) {
        long now = System.nanoTime();
        long phaseEnd;

        final long targetNanos = ceilingHz > 0 ? 1_000_000_000L / ceilingHz : 0L;
        final long thresholdNanos = gateBlockThresholdNanos(targetNanos);
        long frameGateNanos = 0L;

        pacedLastFrame = ceilingHz > 0;
        Tracy.plotInt(P_CEILING_HZ, ceilingHz);

        if (ceilingHz > 0) {
            frameGateNanos = GLStateManager.lastFrameGateNanos();
            final long gateEnd = GLStateManager.lastFrameGateEndNanos();
            Tracy.plotInt(P_FRAME_GATE_US, frameGateNanos / 1000);

            if (gateEnd != lastSeenGateEnd) {
                lastSeenGateEnd = gateEnd;
                if (frameGateNanos > thresholdNanos && GLStateManager.gateAnchorsNextFrameStart()) {
                    deadline = gateEnd;
                }
            }
            deadline = nextDeadline(deadline, now, targetNanos);

            final IdleWork work = idleWork;
            if (work != null && deadline - now > IDLE_WORK_MIN_BUDGET_NANOS) {
                Tracy.beginZone(Z_PACER_IDLE);
                try {
                    work.run(deadline);
                } finally {
                    Tracy.endZone();
                }
                phaseEnd = System.nanoTime();
                now = phaseEnd;
            }

            final long slackNanos = deadline - now;
            Tracy.plotInt(P_SLACK_US, slackNanos / 1000);

            final boolean slept = now < deadline;
            if (slept) {
                Tracy.beginZone(Z_PACER_SLEEP);
                try {
                    now = sleeper.sleepUntil(deadline, now);
                } finally {
                    Tracy.endZone();
                }
            }
            if (Tracy.ENABLED || statsActive) {
                sleeper.noteFrame(slackNanos, slept ? now - deadline : 0L, slept);
                Tracy.plotInt(P_SPIN_US, sleeper.lastSpinNanos / 1000);
            }
        } else {
            deadline = 0L;
        }

        Tracy.beginZone(Z_PACER_WAIT);
        try {
            if (renderAheadWait != null) renderAheadWait.run();
        } finally {
            Tracy.endZone();
        }
        phaseEnd = System.nanoTime();
        final long waitNanos = phaseEnd - now;
        now = phaseEnd;
        if (ceilingHz > 0 && waitNanos > thresholdNanos) deadline = now;
        final boolean gateBlocking = observeGate(ceilingHz > 0 ? Math.max(frameGateNanos, waitNanos) : 0L, thresholdNanos);
        Tracy.plotInt(P_GATE_ACTIVE, gateBlocking ? 1 : 0);

        GLStateManager.pumpDisplayMessages();

        if (pacingStatusChanged(gateBlocking, lastVSyncMode, lastRefreshHz, lastCapHz)) {
            final boolean blocking = settledBlocking;
            final String line = pacingLine(lastVSyncMode, lastRefreshHz, lastCapHz, blocking);
            final boolean notHonoured = vsyncNotHonoured(lastVSyncMode, blocking);
            if (notHonoured) AngelicaMod.LOGGER.warn(line);
            else AngelicaMod.LOGGER.info(line);
            Tracy.message(line, notHonoured ? TracyBackend.SEVERITY_WARNING : TracyBackend.SEVERITY_INFO);
        }

        final long period = lastFrameTime == 0L ? 0L : now - lastFrameTime;
        lastFrameTime = now;
        return period;
    }

    private static int refreshHz() {
        final int reported = GLStateManager.getDisplayRefreshRateHz();
        final int hz = plausibleRefreshHz(reported);

        if (hz == 0 && lastCapHz == 0 && lastVSyncMode.tearFree() && !warnedUncapped) {
            warnedUncapped = true;
            AngelicaMod.LOGGER.warn("Display refresh rate reported as {}Hz, so the frame rate cannot be bounded if the driver ignores vsync. Set Max Framerate to bound it.", reported);
        }
        return hz;
    }

    public static String debugIndicator(VSyncMode mode, int refreshHz, int capHz, boolean gateActive) {
        final int cap = Math.max(capHz, 0);
        final boolean showCap = cap > 0 && cap == pacingCeilingHz(mode, cap, refreshHz, gateActive);
        if (!mode.tearFree() && !showCap) return null;

        final StringBuilder sb = new StringBuilder(" [");
        if (mode.tearFree()) {
            sb.append(mode == VSyncMode.MAILBOX ? "mailbox" : "vsync");
            final int refresh = plausibleRefreshHz(refreshHz);
            if (refresh > 0) sb.append(' ').append(refresh);
            if (showCap) sb.append(", ");
        }
        if (showCap) sb.append("cap ").append(cap);
        return sb.append(']').toString();
    }

    public static String debugIndicator() {
        return debugIndicator(lastVSyncMode, lastRefreshHz, lastCapHz, gateActive());
    }
}
