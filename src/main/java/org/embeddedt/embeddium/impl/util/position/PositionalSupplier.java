package org.embeddedt.embeddium.impl.util.position;

@FunctionalInterface
public interface PositionalSupplier<T> {
    T getAt(int x, int y, int z);
}
