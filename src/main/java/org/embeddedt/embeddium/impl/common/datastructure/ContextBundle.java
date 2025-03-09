package org.embeddedt.embeddium.impl.common.datastructure;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class ContextBundle<UNIVERSE> {
    private final Object[] context;
    private long populatedEntries;
    private static final ConcurrentHashMap<Class<?>, AtomicInteger> ID_TRACKING = new ConcurrentHashMap<>();
    private static final ContextBundle<?> EMPTY = new ContextBundle<>(null);

    private static AtomicInteger idTrackerForUniverse(Class<?> clz) {
        return ID_TRACKING.computeIfAbsent(clz, k -> new AtomicInteger(0));
    }

    public ContextBundle(Class<UNIVERSE> universe) {
        this.context = universe != null ? new Object[idTrackerForUniverse(universe).get()] : null;
        this.populatedEntries = 0;
    }

    @SuppressWarnings("unchecked")
    public static <T> ContextBundle<T> empty() {
        return (ContextBundle<T>)EMPTY;
    }

    public <T> void setContext(Key<UNIVERSE, T> key, T value) {
        if (context == null) {
            throw new IllegalStateException("Attempted to modify empty context!");
        }
        context[key.id] = value;
        this.populatedEntries |= (1L << key.id);
    }

    public <T> void removeContext(Key<UNIVERSE, T> key) {
        if (context == null) {
            throw new IllegalStateException("Attempted to modify empty context!");
        }
        context[key.id] = null;
        this.populatedEntries &= ~(1L << key.id);
    }

    public <T> void mapContext(Key<UNIVERSE, T> key, Function<T, T> mapper) {
        var old = getContext(key);
        var newVal = mapper.apply(old);
        if (newVal == null || Objects.equals(newVal, key.defaultValue)) {
            removeContext(key);
        } else if (newVal != old) {
            setContext(key, newVal);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getContext(Key<UNIVERSE, T> key) {
        var ctx = context;
        if (ctx == null) {
            return key.defaultValue;
        }
        var value = ctx[key.id];
        if (value == null) {
            return key.defaultValue;
        } else {
            return (T)value;
        }
    }

    public boolean hasContext(Key<UNIVERSE, ?> key) {
        return (this.populatedEntries & (1L << key.id)) != 0;
    }

    public boolean hasAnyContext() {
        return this.populatedEntries != 0;
    }

    public long getPopulatedIds() {
        return this.populatedEntries;
    }

    public record Key<UNIVERSE, T>(T defaultValue, int id) {
        @Deprecated
        @ApiStatus.Internal
        public Key(T defaultValue, int id) {
            this.defaultValue = defaultValue;
            this.id = id;
        }

        public Key(Class<UNIVERSE> universe, @Nullable T defaultValue) {
            this(defaultValue, idTrackerForUniverse(universe).getAndIncrement());
            if (this.id >= 64) {
                throw new IllegalStateException("Maximum number of keys for universe " + universe + " exceeded.");
            }
        }

        public Key(Class<UNIVERSE> universe) {
            this(universe, null);
        }
    }
}
