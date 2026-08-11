package com.gtnewhorizons.angelica.rendering.celeritas;

/** Decides when the section-age uniform upload can be skipped */
public final class SectionAgeMath {

    public static final long STEP_NANOS = 100_000_000L;

    private SectionAgeMath() {}

    public static long quantize(long timestampNanos) {
        return timestampNanos - Math.floorMod(timestampNanos, STEP_NANOS);
    }

    public static long[] newState() {
        return new long[] { Long.MIN_VALUE, Long.MIN_VALUE, 0L };
    }

    public static boolean canSkip(long[] state, long newestLoadTime, long quantizedTimestamp) {
        return state[0] == newestLoadTime && (state[2] == 1L || state[1] == quantizedTimestamp);
    }

    public static void record(long[] state, long newestLoadTime, long quantizedTimestamp, boolean allSaturated) {
        state[0] = newestLoadTime;
        state[1] = quantizedTimestamp;
        state[2] = allSaturated ? 1L : 0L;
    }
}
