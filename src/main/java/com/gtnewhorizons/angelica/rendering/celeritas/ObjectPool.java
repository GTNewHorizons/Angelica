package com.gtnewhorizons.angelica.rendering.celeritas;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class ObjectPool<T> {
    private final ConcurrentLinkedDeque<T> free = new ConcurrentLinkedDeque<>();
    private final AtomicInteger freeCount = new AtomicInteger();
    private final Supplier<T> factory;
    private final int maxFree;

    ObjectPool(Supplier<T> factory, int maxFree) {
        this.factory = factory;
        this.maxFree = maxFree;
    }

    T acquire() {
        final T t = free.pollFirst();
        if (t == null) return factory.get();
        freeCount.decrementAndGet();
        return t;
    }

    void release(T t) {
        if (freeCount.incrementAndGet() > maxFree) {
            freeCount.decrementAndGet();
            return;
        }
        free.offerFirst(t);
    }
}
