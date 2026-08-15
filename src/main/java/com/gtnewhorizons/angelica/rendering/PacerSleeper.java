package com.gtnewhorizons.angelica.rendering;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class PacerSleeper {

    private static final long SPIN_FLOOR_NANOS = 100_000L;
    private static final long MIN_PARK_NANOS = 1_000_000L;
    private static final long MAX_OVERSHOOT_NANOS = 4_000_000L;
    private static final int SLOTS = 10;

    private final LongSupplier clock;
    private final LongConsumer parker;
    private final boolean spinWait;
    private final long[] overshoots = new long[SLOTS];
    private long overshootSum;
    private int offset;

    PacerSleeper(LongSupplier clock, LongConsumer parker, boolean spinWait) {
        this.clock = clock;
        this.parker = parker;
        this.spinWait = spinWait;
    }

    long sleepUntil(long deadlineNanos, long now) {
        while (true) {
            final long remaining = deadlineNanos - now;
            if (remaining <= SPIN_FLOOR_NANOS) break;
            if (Thread.currentThread().isInterrupted()) return clock.getAsLong();

            long request = remaining - avgOvershootNanos() - SPIN_FLOOR_NANOS;
            if (request <= 0L) {
                if (remaining <= MIN_PARK_NANOS) break;
                request = MIN_PARK_NANOS;
            }

            final long before = now;
            parker.accept(request);
            now = clock.getAsLong();
            record(Math.min(Math.max(0L, now - before - request), MAX_OVERSHOOT_NANOS));
        }

        if (spinWait) {
            while (now < deadlineNanos) {
                Thread.yield();
                now = clock.getAsLong();
            }
        }
        return now;
    }

    long avgOvershootNanos() {
        return overshootSum / SLOTS;
    }

    private void record(long nanos) {
        overshootSum += nanos - overshoots[offset];
        overshoots[offset] = nanos;
        offset = (offset + 1) % SLOTS;
    }
}
