package org.embeddedt.embeddium.impl.util.collections;

import java.util.Set;

public class SetUtil {
    @SuppressWarnings("unchecked")
    public static <T> Set<T> copyOf(Set<T> set) {
        if (set.isEmpty()) {
            return Set.of();
        } else {
            return (Set<T>)Set.of(set.toArray());
        }
    }
}
