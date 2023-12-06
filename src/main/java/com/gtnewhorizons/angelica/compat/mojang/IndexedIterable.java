package com.gtnewhorizons.angelica.compat.mojang;

import javax.annotation.Nullable;

@Deprecated
public interface IndexedIterable <T> extends Iterable<T> {
    int getRawId(T entry);

    @Nullable
    T get(int index);
}
