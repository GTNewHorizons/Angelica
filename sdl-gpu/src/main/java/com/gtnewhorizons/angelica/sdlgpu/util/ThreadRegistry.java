package com.gtnewhorizons.angelica.sdlgpu.util;

import java.util.Arrays;

public final class ThreadRegistry<T> {

    private final T[] empty;
    private final Object lock = new Object();

    private volatile T[] entries;
    private volatile T sole;

    public ThreadRegistry(T[] empty) {
        this.empty = empty;
        this.entries = empty;
    }

    public T sole() {
        return sole;
    }

    public T[] snapshot() {
        return entries;
    }

    public int size() {
        return entries.length;
    }

    public void add(T entry) {
        synchronized (lock) {
            final T[] prev = entries;
            final T[] next = Arrays.copyOf(prev, prev.length + 1);
            next[prev.length] = entry;
            publish(next);
        }
    }

    public boolean remove(T entry) {
        synchronized (lock) {
            final T[] prev = entries;
            int idx = -1;
            for (int i = 0; i < prev.length; i++) {
                if (prev[i] == entry) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) return false;
            final T[] next = Arrays.copyOf(prev, prev.length - 1);
            System.arraycopy(prev, idx + 1, next, idx, prev.length - idx - 1);
            publish(next);
        }
        return true;
    }

    public void clear() {
        synchronized (lock) {
            publish(empty);
        }
    }

    private void publish(T[] next) {
        entries = next;
        sole = next.length == 1 ? next[0] : null;
    }
}
