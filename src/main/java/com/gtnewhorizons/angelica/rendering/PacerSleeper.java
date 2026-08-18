package com.gtnewhorizons.angelica.rendering;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class PacerSleeper {

    private static final long MIN_PARK_NANOS = 1_000_000L;
    private static final long MAX_OVERSHOOT_NANOS = 4_000_000L;
    static final int SLOTS = 10;

    static final long MIN_SPIN_FLOOR_NANOS = 20_000L;
    private static final long MAX_SPIN_FLOOR_NANOS = 500_000L;

    private final LongSupplier clock;
    private final LongConsumer parker;
    private final boolean spinWait;
    private final long[] overshoots = new long[SLOTS];
    private long overshootSum;
    private long spinFloorNanos = MIN_SPIN_FLOOR_NANOS;
    private int offset;

    long lastSpinNanos;
    int lastParks;

    private long frames;
    private long slackSum;
    private long parkTotal;
    private long lateFrames;
    private long lateMax;

    PacerSleeper(LongSupplier clock, LongConsumer parker, boolean spinWait) {
        this.clock = clock;
        this.parker = parker;
        this.spinWait = spinWait;
    }

    long spinFloorNanos() {
        return spinFloorNanos;
    }

    long sleepUntil(long deadlineNanos, long now) {
        resetLastFrame();

        long spinFloor = spinFloorNanos;
        while (true) {
            final long remaining = deadlineNanos - now;
            if (remaining <= spinFloor) break;
            if (Thread.currentThread().isInterrupted()) return clock.getAsLong();

            long request = remaining - avgOvershootNanos() - spinFloor;
            if (request <= 0L) {
                if (remaining <= MIN_PARK_NANOS) break;
                request = MIN_PARK_NANOS;
            }

            final long before = now;
            parker.accept(request);
            now = clock.getAsLong();
            record(Math.min(Math.max(0L, now - before - request), MAX_OVERSHOOT_NANOS));
            spinFloor = spinFloorNanos;
            lastParks++;
        }

        if (spinWait) {
            final long spinStart = now;
            while (now < deadlineNanos) {
                if (deadlineNanos - now > spinFloor) Thread.yield();
                else Thread.onSpinWait();
                now = clock.getAsLong();
            }
            lastSpinNanos = now - spinStart;
        }
        return now;
    }

    void noteFrame(long slackNanos, long wakeLateNanos, boolean slept) {
        if (!slept) resetLastFrame();

        frames++;
        slackSum += slackNanos;
        parkTotal += lastParks;
        if (slackNanos < 0L && -slackNanos > lateMax) lateMax = -slackNanos;
        if (slackNanos < 0L || wakeLateNanos > 0L) lateFrames++;
    }

    private void resetLastFrame() {
        lastSpinNanos = 0L;
        lastParks = 0;
    }

    void resetStats() {
        frames = 0L;
        slackSum = 0L;
        parkTotal = 0L;
        lateFrames = 0L;
        lateMax = 0L;
    }

    String summary(String config) {
        if (frames == 0L) return "pacer: " + config + " no paced frames";
        return "pacer: " + config + " frames=" + frames + " parks=" + parkTotal + " slackUs avg=" + slackSum / frames / 1000L + " floorUs=" + spinFloorNanos / 1000L
            + " overshootUs est=" + avgOvershootNanos() / 1000L + " lateFrames=" + lateFrames + " lateMaxUs=" + lateMax / 1000L;
    }

    long avgOvershootNanos() {
        return overshootSum / SLOTS;
    }

    private void record(long nanos) {
        overshootSum += nanos - overshoots[offset];
        overshoots[offset] = nanos;
        offset = (offset + 1) % SLOTS;

        long max = 0L;
        for (final long o : overshoots) {
            if (o > max) max = o;
        }
        final long jitter = max - avgOvershootNanos();
        spinFloorNanos = Math.max(MIN_SPIN_FLOOR_NANOS, Math.min(MAX_SPIN_FLOOR_NANOS, jitter));
    }
}
