package com.gtnewhorizons.angelica.glsm.states;

public interface ISettableState<T> extends Cloneable {
    T set(T state);
    default void set(ISettableState<?> value) {
        set((T) value);
    }

    boolean sameAs(Object state);

    T copy();

}

