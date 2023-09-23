package org.embeddedt.archaicfix.helpers;

import speiger.src.collections.objects.lists.ObjectArrayList;

/** An ObjectArrayList that has a getter that returns null instead of throwing an exception when the index is out of
 * bounds. */
public class UnexpectionalObjectArrayList<T> extends ObjectArrayList<T> {
    public T getOrNull(int index) {
        if(index >= 0 && index < size()) {
            return data[index];
        } else {
            return null;
        }
    }
}
