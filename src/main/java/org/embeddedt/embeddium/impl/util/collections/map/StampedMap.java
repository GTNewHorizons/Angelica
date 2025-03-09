package org.embeddedt.embeddium.impl.util.collections.map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 * An efficient StampedLock-based wrapper around a given map. Common operations like get/put are efficient, but collection-based
 * operations like entrySet/keySet/values are slow as they must allocate a copy.
 */
public class StampedMap<K, V> implements Map<K, V> {
    private final Map<K, V> backingMap;
    private final StampedLock lock;

    public StampedMap(Map<K, V> backingMap) {
        this.backingMap = backingMap;
        this.lock = new StampedLock();
    }

    @Override
    public int size() {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.size();
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.containsKey(key);
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.containsValue(value);
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public V get(Object key) {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.get(key);
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public @Nullable V put(K key, V value) {
        long stamp = this.lock.writeLock();
        try {
            return this.backingMap.put(key, value);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public V remove(Object key) {
        long stamp = this.lock.writeLock();
        try {
            return this.backingMap.remove(key);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        long stamp = this.lock.writeLock();
        try {
            this.backingMap.putAll(m);
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public void clear() {
        long stamp = this.lock.writeLock();
        try {
            this.backingMap.clear();
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    @Override
    public @NotNull Set<K> keySet() {
        long readStamp = this.lock.readLock();
        try {
            return Set.copyOf(this.backingMap.keySet());
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public @NotNull Collection<V> values() {
        return List.of();
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.entrySet().stream().map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue())).collect(Collectors.toSet());
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public boolean equals(Object obj) {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.equals(obj);
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }

    @Override
    public int hashCode() {
        long readStamp = this.lock.readLock();
        try {
            return this.backingMap.hashCode();
        } finally {
            this.lock.unlockRead(readStamp);
        }
    }
}
