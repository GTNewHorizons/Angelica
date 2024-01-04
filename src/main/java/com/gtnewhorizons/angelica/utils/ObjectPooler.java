package com.gtnewhorizons.angelica.utils;

import java.util.ArrayList;
import java.util.function.Supplier;

public class ObjectPooler<T> {
    private final Supplier<T> instanceSupplier;
    private final ArrayList<T> availableInstances;

    public ObjectPooler(Supplier<T> instanceSupplier) {
        this.instanceSupplier = instanceSupplier;
        this.availableInstances = new ArrayList<>();
    }

    public T getInstance() {
        if(this.availableInstances.isEmpty()) {
            return this.instanceSupplier.get();
        }
        return this.availableInstances.remove(this.availableInstances.size() - 1);
    }

    public void releaseInstance(T instance) {
        this.availableInstances.add(instance);
    }
}
