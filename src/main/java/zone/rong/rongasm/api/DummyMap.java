package zone.rong.rongasm.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Used as a ImmutableMap-like structure, but with the methods not throwing UnsupportedOperationException
 */
public class DummyMap<K, V> implements Map<K, V> {

    private static final DummyMap INSTANCE = new DummyMap<>();
    private static final Set SET_INSTANCE = Collections.newSetFromMap(INSTANCE);

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> of() {
        return (DummyMap<K, V>) INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public static <K> Set<K> asSet() {
        return (Set<K>) SET_INSTANCE;
    }

    public int size() { return 0; }

    public boolean isEmpty() { return true; }

    public boolean containsKey(Object key) { return false; }

    public boolean containsValue(Object value) { return false; }

    public V get(Object key) { return null; }

    public V put(K key, V value) { return value; }

    public V remove(Object key) { return null; }

    public void putAll(Map m) { }

    public void clear() { }

    public Set keySet() { return Collections.emptySet(); }

    public Collection values() { return Collections.emptySet(); }

    public Set entrySet() { return Collections.emptySet(); }

}
