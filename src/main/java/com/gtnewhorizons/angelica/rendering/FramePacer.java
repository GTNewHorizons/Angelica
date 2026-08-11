package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.VSyncMode;

public final class FramePacer {

    private static final int MIN_PLAUSIBLE_REFRESH_HZ = 20;
    private static final int MAX_PLAUSIBLE_REFRESH_HZ = 1000;
    private static final long REFRESH_POLL_NANOS = 1_000_000_000L;

    private static long lastFrameTime;
    private static long fpsLimitOverhead;

    private static long lastRefreshPollNanos;
    private static int cachedRefreshHz;
    private static boolean warnedUnknownRefresh;

    private static VSyncMode lastVSyncMode = VSyncMode.ON;
    private static int lastCapHz;
    private static int lastRefreshHz;

    private FramePacer() {}

    public static int plausibleRefreshHz(int hz) {
        return hz >= MIN_PLAUSIBLE_REFRESH_HZ && hz <= MAX_PLAUSIBLE_REFRESH_HZ ? hz : 0;
    }

    public static int pacingCeilingHz(VSyncMode mode, int capHz, int refreshHz) {
        if (mode.blocksOnVBlank()) return 0;
        final int cap = Math.max(capHz, 0);
        if (mode != VSyncMode.MAILBOX) return cap;
        final int refresh = plausibleRefreshHz(refreshHz);
        if (cap == 0) return refresh;
        return refresh == 0 ? cap : Math.min(cap, refresh);
    }

    public static long sleepNanosFor(long targetNanos, long lastWorkNanos, long overheadNanos) {
        return targetNanos - lastWorkNanos - overheadNanos;
    }

    public static int updateCeiling(int capHz) {
        lastVSyncMode = GLStateManager.getEffectiveVSyncMode();
        lastCapHz = Math.max(capHz, 0);
        lastRefreshHz = refreshHz();
        return pacingCeilingHz(lastVSyncMode, lastCapHz, lastRefreshHz);
    }

    public static long pace(int ceilingHz) {
        if (ceilingHz > 0) {
            final long time = System.nanoTime();
            final long targetNanos = 1_000_000_000L / ceilingHz;

            final long sleepNanos = sleepNanosFor(targetNanos, time - lastFrameTime, fpsLimitOverhead);
            if (sleepNanos > 0) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) sleepNanos % 1_000_000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                long overhead = System.nanoTime() - time - sleepNanos;
                if (overhead < 0 || overhead > targetNanos / 2) overhead = 0;
                fpsLimitOverhead = overhead;
            }
        }

        final long time = System.nanoTime();
        final long period = time - lastFrameTime;
        lastFrameTime = time;
        return period;
    }

    private static int refreshHz() {
        final long now = System.nanoTime();
        if (lastRefreshPollNanos == 0L || now - lastRefreshPollNanos >= REFRESH_POLL_NANOS) {
            lastRefreshPollNanos = now;
            cachedRefreshHz = GLStateManager.getDisplayRefreshRateHz();
        }
        final int hz = plausibleRefreshHz(cachedRefreshHz);

        if (hz == 0 && lastVSyncMode == VSyncMode.MAILBOX && !warnedUnknownRefresh) {
            warnedUnknownRefresh = true;
            AngelicaMod.LOGGER.warn("Display refresh rate reported as {}Hz; Mailbox will not be paced to the refresh rate. The Max Framerate setting still applies.", cachedRefreshHz);
        }
        return hz;
    }

    public static String debugIndicator(VSyncMode mode, int refreshHz, int capHz) {
        final boolean vsync = mode.blocksOnVBlank();
        final boolean mailbox = mode == VSyncMode.MAILBOX;

        final boolean showCap = capHz > 0 && !vsync;
        if (!vsync && !mailbox && !showCap) return null;

        final StringBuilder sb = new StringBuilder(" [");
        if (vsync || mailbox) {
            sb.append(vsync ? "vsync" : "mailbox");
            if (refreshHz > 0) sb.append(' ').append(refreshHz);
            if (showCap) sb.append(", ");
        }
        if (showCap) sb.append("cap ").append(capHz);
        return sb.append(']').toString();
    }

    public static String debugIndicator() {
        return debugIndicator(lastVSyncMode, lastRefreshHz, lastCapHz);
    }

    public static VSyncMode lastVSyncMode() { return lastVSyncMode; }

    public static int lastCapHz() { return lastCapHz; }

    public static int lastRefreshHz() { return lastRefreshHz; }
}
