package com.gtnewhorizons.angelica.rendering.celeritas;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

final class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> free = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;

    ObjectPool(Supplier<T> factory) {
        this.factory = factory;
    }

    T acquire() {
        final T t = free.poll();
        return t != null ? t : factory.get();
    }

    void release(T t) {
        free.add(t);
    }
}
