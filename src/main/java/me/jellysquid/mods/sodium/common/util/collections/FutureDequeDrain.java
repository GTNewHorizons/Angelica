package me.jellysquid.mods.sodium.common.util.collections;

import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public class FutureDequeDrain<T> implements Iterator<T> {
    private final Deque<CompletableFuture<T>> deque;
    private T next = null;

    public FutureDequeDrain(Deque<CompletableFuture<T>> deque) {
        this.deque = deque;
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }

        findNext();

        return next != null;
    }

    private void findNext() {
        while (!deque.isEmpty()) {
            CompletableFuture<T> future = deque.remove();

            try {
                next = future.join();
                return;
            } catch (CancellationException e) {
                // no-op
            }
        }
    }
    
    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        T result = next;
        next = null;

        return result;
    }
}
