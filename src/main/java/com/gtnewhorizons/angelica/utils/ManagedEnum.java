package com.gtnewhorizons.angelica.utils;

public class ManagedEnum<T extends Enum<?>> {

    private final T[] allValues;
    private T value;

    public ManagedEnum(T initialValue) {
        if (initialValue == null) throw new IllegalArgumentException();
        value = initialValue;
        @SuppressWarnings("unchecked")
        T[] allValues = (T[]) value.getClass().getEnumConstants();
        this.allValues = allValues;
    }

    public boolean is(T value) {
        return this.value == value;
    }

    public T next() {
        value = allValues[(value.ordinal() + 1) % allValues.length];
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void set(int ordinal) {
        this.value = allValues[Math.min(Math.max(0, ordinal), allValues.length - 1)];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ManagedEnum<?> that = (ManagedEnum<?>) o;

        return value == that.value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
