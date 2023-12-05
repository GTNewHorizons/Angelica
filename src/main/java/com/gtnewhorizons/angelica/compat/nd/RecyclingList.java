package com.gtnewhorizons.angelica.compat.nd;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RecyclingList<T> {
    // Adapted from Neodymium

    private Supplier<T> constructor;

    int nextIndex;
    private final List<T> list;

    public RecyclingList(Supplier<T> constructor) {
        this.constructor = constructor;
        this.list = new ArrayList<>();
    }

    public T get(int i) {
        while(list.size() <= i) {
            list.add(constructor.get());
        }
        return list.get(i);
    }

    public T next() {
        return get(nextIndex++);
    }

    public void remove() {
        if(nextIndex == 0) {
            throw new IllegalStateException("Tried to remove from empty list");
        }
        nextIndex--;
    }

    public boolean isEmpty() {
        return nextIndex == 0;
    }

    public void reset() {
        nextIndex = 0;
    }

    public List<T> getAsList() {
        return list.subList(0, nextIndex);
    }
}
