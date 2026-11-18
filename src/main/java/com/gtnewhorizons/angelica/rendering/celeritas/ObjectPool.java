package com.gtnewhorizons.angelica.rendering.celeritas;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class ObjectPool<T> {
    static final long TRIM_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final ConcurrentLinkedDeque<T> free = new ConcurrentLinkedDeque<>();
    private final AtomicInteger freeCount = new AtomicInteger();
    private final AtomicInteger inUse = new AtomicInteger();
    private final Supplier<T> factory;
    private final int maxFree;

    private boolean started;
    private long windowStart;
    private int windowPeak;
    private int previousPeak;

    ObjectPool(Supplier<T> factory, int maxFree) {
        this.factory = factory;
        this.maxFree = maxFree;
    }

    T acquire() {
        inUse.incrementAndGet();
        final T t = free.pollFirst();
        if (t == null) return factory.get();
        freeCount.decrementAndGet();
        return t;
    }

    void release(T t) {
        inUse.decrementAndGet();
        if (freeCount.incrementAndGet() > maxFree) {
            freeCount.decrementAndGet();
            return;
        }
        free.offerFirst(t);
    }

    int freeCount() {
        return freeCount.get();
    }

    void trim(long nowNanos, int minFree) {
        final int used = inUse.get();
        if (used > windowPeak) windowPeak = used;
        if (!started) {
            started = true;
            windowStart = nowNanos;
            return;
        }
        if (nowNanos - windowStart < TRIM_WINDOW_NANOS) return;
        final int peak = Math.max(windowPeak, previousPeak);
        previousPeak = windowPeak;
        windowPeak = used;
        windowStart = nowNanos;
        final int excess = freeCount.get() - Math.max(minFree, peak + (peak >> 1) - used);
        for (int i = (excess + 1) >> 1; i > 0 && free.pollLast() != null; i--) {
            freeCount.decrementAndGet();
        }
    }
}
