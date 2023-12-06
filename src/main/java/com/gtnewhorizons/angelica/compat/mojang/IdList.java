package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

@Deprecated
public class IdList <T> implements IndexedIterable<T> {
    protected int nextId;
    protected final IdentityHashMap<T, Integer> idMap;
    protected final List<T> list;

    public IdList() {
        this(512);
    }

    public IdList(int initialSize) {
        this.list = Lists.newArrayListWithExpectedSize(initialSize);
        this.idMap = new IdentityHashMap(initialSize);
    }

    public void set(T value, int id) {
        this.idMap.put(value, id);

        while(this.list.size() <= id) {
            this.list.add(null);
        }

        this.list.set(id, value);
        if (this.nextId <= id) {
            this.nextId = id + 1;
        }

    }

    public void add(T value) {
        this.set(value, this.nextId);
    }

    public int getRawId(T entry) {
        Integer integer = (Integer)this.idMap.get(entry);
        return integer == null ? -1 : integer;
    }

    @Nullable
    public final T get(int index) {
        return index >= 0 && index < this.list.size() ? this.list.get(index) : null;
    }

    public Iterator<T> iterator() {
        return Iterators.filter(this.list.iterator(), Predicates.notNull());
    }

    public int size() {
        return this.idMap.size();
    }
}
