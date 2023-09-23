package speiger.src.collections.objects.maps.impl.hash;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;
import speiger.src.collections.objects.functions.function.UnaryOperator;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.maps.abstracts.AbstractObject2ObjectMap;
import speiger.src.collections.objects.maps.interfaces.Object2ObjectMap;
import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.sets.AbstractObjectSet;
import speiger.src.collections.objects.sets.ObjectSet;
import speiger.src.collections.utils.HashUtil;
import speiger.src.collections.utils.ITrimmable;

/**
 * A Type Specific Custom implementation of the HashMap
 * Instead of using Wrapper Object Arrays for storing keys and values there is dedicated arrays for storing keys and values.
 * Extra to that there is a couple quality of life functions provided
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
public class Object2ObjectOpenHashMap<T, V> extends AbstractObject2ObjectMap<T, V> implements ITrimmable
{
	/** The Backing keys array */
	protected transient T[] keys;
	/** The Backing values array */
	protected transient V[] values;
	/** If a null value is present */
	protected transient boolean containsNull;
	/** Minimum array size the HashMap will be */
	protected transient int minCapacity;
	/** Index of the Null Value */
	protected transient int nullIndex;
	/** Maximum amount of Values that can be stored before the array gets expanded usually 75% */
	protected transient int maxFill;
	/** Max Index that is allowed to be searched through nullIndex - 1 */
	protected transient int mask;
	/** EntrySet cache */
	protected transient FastEntrySet<T, V> entrySet;
	/** KeySet cache */
	protected transient ObjectSet<T> keySet;
	/** Values cache */
	protected transient ObjectCollection<V> valuesC;

	/** Amount of Elements stored in the HashMap */
	protected int size;
	/** How full the Arrays are allowed to get before resize */
	protected final float loadFactor;

	/**
	 * Default Constructor
	 */
	public Object2ObjectOpenHashMap() {
		this(HashUtil.DEFAULT_MIN_CAPACITY, HashUtil.DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Constructor that defines the minimum capacity
	 * @param minCapacity the minimum capacity the HashMap is allowed to be.
	 * @throws IllegalStateException if the minimum capacity is negative
	 */
	public Object2ObjectOpenHashMap(int minCapacity) {
		this(minCapacity, HashUtil.DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Constructor that defines the minimum capacity and load factor
	 * @param minCapacity the minimum capacity the HashMap is allowed to be.
	 * @param loadFactor the percentage of how full the backing array can be before they resize
	 * @throws IllegalStateException if the minimum capacity is negative
	 * @throws IllegalStateException if the loadfactor is either below/equal to 0 or above/equal to 1
	 */
	public Object2ObjectOpenHashMap(int minCapacity, float loadFactor) {
		if(minCapacity < 0)	throw new IllegalStateException("Minimum Capacity is negative. This is not allowed");
		if(loadFactor <= 0 || loadFactor >= 1F) throw new IllegalStateException("Load Factor is not between 0 and 1");
		this.loadFactor = loadFactor;
		this.minCapacity = nullIndex = HashUtil.arraySize(minCapacity, loadFactor);
		mask = nullIndex - 1;
		maxFill = Math.min((int)Math.ceil(nullIndex * loadFactor), nullIndex - 1);
		keys = (T[])new Object[nullIndex + 1];
		values = (V[])new Object[nullIndex + 1];
	}

	/**
	 * Helper constructor that allow to create a map from unboxed values
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @throws IllegalStateException if the keys and values do not match in lenght
	 */
	public Object2ObjectOpenHashMap(T[] keys, V[] values) {
		this(keys, values, HashUtil.DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Helper constructor that allow to create a map from unboxed values
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @param loadFactor the percentage of how full the backing array can be before they resize
	 * @throws IllegalStateException if the keys and values do not match in lenght
	 * @throws IllegalStateException if the loadfactor is either below/equal to 0 or above/equal to 1
	 */
	public Object2ObjectOpenHashMap(T[] keys, V[] values, float loadFactor) {
		this(keys.length, loadFactor);
		if(keys.length != values.length) throw new IllegalStateException("Input Arrays are not equal size");
		for(int i = 0,m=keys.length;i<m;i++) put(keys[i], values[i]);
	}

	/**
	 * A Helper constructor that allows to create a Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
	 */
	public Object2ObjectOpenHashMap(Map<? extends T, ? extends V> map) {
		this(map, HashUtil.DEFAULT_LOAD_FACTOR);
	}

	/**
	 * A Helper constructor that allows to create a Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
	 * @param loadFactor the percentage of how full the backing array can be before they resize
	 * @throws IllegalStateException if the loadfactor is either below/equal to 0 or above/equal to 1
	 */
	public Object2ObjectOpenHashMap(Map<? extends T, ? extends V> map, float loadFactor) {
		this(map.size(), loadFactor);
		putAll(map);
	}

	/**
	 * A Type Specific Helper function that allows to create a new Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
 	 */
	public Object2ObjectOpenHashMap(Object2ObjectMap<T, V> map) {
		this(map, HashUtil.DEFAULT_LOAD_FACTOR);
	}

	/**
	 * A Type Specific Helper function that allows to create a new Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
	 * @param loadFactor the percentage of how full the backing array can be before they resize
	 * @throws IllegalStateException if the loadfactor is either below/equal to 0 or above/equal to 1
 	 */
	public Object2ObjectOpenHashMap(Object2ObjectMap<T, V> map, float loadFactor) {
		this(map.size(), loadFactor);
		putAll(map);
	}

	@Override
	public V put(T key, V value) {
		int slot = findIndex(key);
		if(slot < 0) {
			insert(-slot-1, key, value);
			return getDefaultReturnValue();
		}
		V oldValue = values[slot];
		values[slot] = value;
		return oldValue;
	}

	@Override
	public V putIfAbsent(T key, V value) {
		int slot = findIndex(key);
		if(slot < 0) {
			insert(-slot-1, key, value);
			return getDefaultReturnValue();
		}
		else if(Objects.equals(values[slot], getDefaultReturnValue())) {
			V oldValue = values[slot];
			values[slot] = value;
			return oldValue;
		}
		return values[slot];
	}

	@Override
	public boolean containsKey(Object key) {
		return findIndex(key) >= 0;
	}

	@Override
	public boolean containsValue(Object value) {
		if(containsNull && Objects.equals(values[nullIndex], value)) return true;
		for(int i = nullIndex-1;i >= 0;i--)
			if(keys[i] != null && Objects.equals(value, values[i])) return true;
		return false;
	}

	@Override
	public V rem(T key) {
		int slot = findIndex(key);
		if(slot < 0) return getDefaultReturnValue();
		return removeIndex(slot);
	}

	@Override
	public V remOrDefault(T key, V defaultValue) {
		int slot = findIndex(key);
		if(slot < 0) return defaultValue;
		return removeIndex(slot);
	}

	@Override
	public V remove(Object key) {
		int slot = findIndex(key);
		if(slot < 0) return getDefaultReturnValue();
		return removeIndex(slot);
	}

	@Override
	public boolean remove(Object key, Object value) {
		Objects.requireNonNull(value);
		if(key == null) {
			if(containsNull && Objects.equals(value, values[nullIndex])) {
				removeNullIndex();
				return true;
			}
			return false;
		}
		int pos = HashUtil.mix(key.hashCode()) & mask;
		T current = keys[pos];
		if(current == null) return false;
		if(Objects.equals(key, current) && Objects.equals(value, values[pos])) {
			removeIndex(pos);
			return true;
		}
		while(true) {
			if((current = keys[pos = (++pos & mask)]) == null) return false;
			else if(Objects.equals(key, current) && Objects.equals(value, values[pos])){
				removeIndex(pos);
				return true;
			}
		}
	}

	@Override
	public V getObject(T key) {
		int slot = findIndex(key);
		return slot < 0 ? getDefaultReturnValue() : values[slot];
	}

	@Override
	public V get(Object key) {
		int slot = findIndex(key);
		return slot < 0 ? getDefaultReturnValue() : values[slot];
	}

	@Override
	public V getOrDefault(Object key, V defaultValue) {
		int slot = findIndex(key);
		return slot < 0 ? defaultValue : values[slot];
	}

	@Override
	public Object2ObjectOpenHashMap<T, V> copy() {
		Object2ObjectOpenHashMap<T, V> map = new Object2ObjectOpenHashMap<>(0, loadFactor);
		map.minCapacity = minCapacity;
		map.mask = mask;
		map.maxFill = maxFill;
		map.nullIndex = nullIndex;
		map.containsNull = containsNull;
		map.size = size;
		map.keys = Arrays.copyOf(keys, keys.length);
		map.values = Arrays.copyOf(values, values.length);
		return map;
	}

	@Override
	public ObjectSet<Object2ObjectMap.Entry<T, V>> object2ObjectEntrySet() {
		if(entrySet == null) entrySet = new MapEntrySet();
		return entrySet;
	}

	@Override
	public ObjectSet<T> keySet() {
		if(keySet == null) keySet = new KeySet();
		return keySet;
	}

	@Override
	public ObjectCollection<V> values() {
		if(valuesC == null) valuesC = new Values();
		return valuesC;
	}

	@Override
	public void forEach(ObjectObjectConsumer<T, V> action) {
		if(size() <= 0) return;
		if(containsNull) action.accept(keys[nullIndex], values[nullIndex]);
		for(int i = nullIndex-1;i>=0;i--) {
			if(keys[i] != null) action.accept(keys[i], values[i]);
		}
	}

	@Override
	public boolean replace(T key, V oldValue, V newValue) {
		int index = findIndex(key);
		if(index < 0 || values[index] != oldValue) return false;
		values[index] = newValue;
		return true;
	}

	@Override
	public V replace(T key, V value) {
		int index = findIndex(key);
		if(index < 0) return getDefaultReturnValue();
		V oldValue = values[index];
		values[index] = value;
		return oldValue;
	}

	@Override
	public V compute(T key, ObjectObjectUnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index < 0) {
			V newValue = mappingFunction.apply(key, getDefaultReturnValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insert(-index-1, key, newValue);
			return newValue;
		}
		V newValue = mappingFunction.apply(key, values[index]);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			removeIndex(index);
			return newValue;
		}
		values[index] = newValue;
		return newValue;
	}

	@Override
	public V computeIfAbsent(T key, UnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index < 0) {
			V newValue = mappingFunction.apply(key);
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insert(-index-1, key, newValue);
			return newValue;
		}
		V newValue = values[index];
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			newValue = mappingFunction.apply(key);
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			values[index] = newValue;
		}
		return newValue;
	}

	@Override
	public V supplyIfAbsent(T key, ObjectSupplier<V> valueProvider) {
		Objects.requireNonNull(valueProvider);
		int index = findIndex(key);
		if(index < 0) {
			V newValue = valueProvider.get();
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insert(-index-1, key, newValue);
			return newValue;
		}
		V newValue = values[index];
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			newValue = valueProvider.get();
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			values[index] = newValue;
		}
		return newValue;
	}

	@Override
	public V computeIfPresent(T key, ObjectObjectUnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index < 0 || Objects.equals(values[index], getDefaultReturnValue())) return getDefaultReturnValue();
		V newValue = mappingFunction.apply(key, values[index]);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			removeIndex(index);
			return newValue;
		}
		values[index] = newValue;
		return newValue;
	}

	@Override
	public V merge(T key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		Objects.requireNonNull(value);
		int index = findIndex(key);
		V newValue = index < 0 || Objects.equals(values[index], getDefaultReturnValue()) ? value : mappingFunction.apply(values[index], value);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			if(index >= 0)
				removeIndex(index);
		}
		else if(index < 0) insert(-index-1, key, newValue);
		else values[index] = newValue;
		return newValue;
	}

	@Override
	public void mergeAll(Object2ObjectMap<T, V> m, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(Object2ObjectMap.Entry<T, V> entry : getFastIterable(m)) {
			T key = entry.getKey();
			int index = findIndex(key);
			V newValue = index < 0 || Objects.equals(values[index], getDefaultReturnValue()) ? entry.getValue() : mappingFunction.apply(values[index], entry.getValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) {
				if(index >= 0)
					removeIndex(index);
			}
			else if(index < 0) insert(-index-1, key, newValue);
			else values[index] = newValue;
		}
	}

	@Override
	public int size() { return size; }

	@Override
	public void clear() {
		if(size == 0) return;
		size = 0;
		containsNull = false;
		Arrays.fill(keys, null);
		Arrays.fill(values, null);
	}

	@Override
	public boolean trim(int size) {
		int request = Math.max(minCapacity, HashUtil.nextPowerOfTwo((int)Math.ceil(size / loadFactor)));
		if(request >= nullIndex || this.size >= Math.min((int)Math.ceil(request * loadFactor), request - 1)) return false;
		try {
			rehash(request);
		}
		catch(OutOfMemoryError noMemory) { return false; }
		return true;
	}

	@Override
	public void clearAndTrim(int size) {
		int request = Math.max(minCapacity, HashUtil.nextPowerOfTwo((int)Math.ceil(size / loadFactor)));
		if(request >= nullIndex) {
			clear();
			return;
		}
		nullIndex = request;
		mask = request-1;
		maxFill = Math.min((int)Math.ceil(nullIndex * loadFactor), nullIndex - 1);
		keys = (T[])new Object[request + 1];
		values = (V[])new Object[request + 1];
		this.size = 0;
		containsNull = false;
	}

	protected int findIndex(Object key) {
		if(key == null) return containsNull ? nullIndex : -(nullIndex + 1);
		int pos = HashUtil.mix(key.hashCode()) & mask;
		T current = keys[pos];
		if(current != null) {
			if(Objects.equals(key, current)) return pos;
			while((current = keys[pos = (++pos & mask)]) != null)
				if(Objects.equals(key, current)) return pos;
		}
		return -(pos + 1);
	}

	protected V removeIndex(int pos) {
		if(pos == nullIndex) return containsNull ? removeNullIndex() : getDefaultReturnValue();
		V value = values[pos];
		keys[pos] = null;
		values[pos] = null;
		size--;
		onNodeRemoved(pos);
		shiftKeys(pos);
		if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
		return value;
	}

	protected V removeNullIndex() {
		V value = values[nullIndex];
		containsNull = false;
		keys[nullIndex] = null;
		values[nullIndex] = null;
		size--;
		onNodeRemoved(nullIndex);
		if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
		return value;
	}

	protected void insert(int slot, T key, V value) {
		if(slot == nullIndex) containsNull = true;
		keys[slot] = key;
		values[slot] = value;
		onNodeAdded(slot);
		if(size++ >= maxFill) rehash(HashUtil.arraySize(size+1, loadFactor));
	}

	protected void rehash(int newSize) {
		int newMask = newSize - 1;
		T[] newKeys = (T[])new Object[newSize + 1];
		V[] newValues = (V[])new Object[newSize + 1];
		for(int i = nullIndex, pos = 0, j = (size - (containsNull ? 1 : 0));j-- != 0;) {
			while(true) {
				if(--i < 0) throw new ConcurrentModificationException("Map was modified during rehash");
				if(keys[i] != null) break;
			}
			if(newKeys[pos = HashUtil.mix(Objects.hashCode(keys[i])) & newMask] != null)
				while(newKeys[pos = (++pos & newMask)] != null);
			newKeys[pos] = keys[i];
			newValues[pos] = values[i];
		}
		newValues[newSize] = values[nullIndex];
		nullIndex = newSize;
		mask = newMask;
		maxFill = Math.min((int)Math.ceil(nullIndex * loadFactor), nullIndex - 1);
		keys = newKeys;
		values = newValues;
	}

	protected void onNodeAdded(int pos) {

	}

	protected void onNodeRemoved(int pos) {

	}

	protected void onNodeMoved(int from, int to) {

	}

	protected void shiftKeys(int startPos) {
		int slot, last;
		T current;
		while(true) {
			startPos = ((last = startPos) + 1) & mask;
			while(true){
				if((current = keys[startPos]) == null) {
					keys[last] = null;
					values[last] = null;
					return;
				}
				slot = HashUtil.mix(Objects.hashCode(current)) & mask;
				if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
				startPos = ++startPos & mask;
			}
			keys[last] = current;
			values[last] = values[startPos];
			onNodeMoved(startPos, last);
		}
	}

	protected class MapEntry implements Object2ObjectMap.Entry<T, V>, Map.Entry<T, V> {
		public int index = -1;

		public MapEntry() {}
		public MapEntry(int index) {
			this.index = index;
		}

		@Override
		public T getKey() {
			return keys[index];
		}

		@Override
		public V getValue() {
			return values[index];
		}

		@Override
		public V setValue(V value) {
			V oldValue = values[index];
			values[index] = value;
			return oldValue;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj instanceof Map.Entry) {
				if(obj instanceof Object2ObjectMap.Entry) {
					Object2ObjectMap.Entry<T, V> entry = (Object2ObjectMap.Entry<T, V>)obj;
					return Objects.equals(keys[index], entry.getKey()) && Objects.equals(values[index], entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
				Object key = entry.getKey();
				Object value = entry.getValue();
				return Objects.equals(keys[index], key) && Objects.equals(values[index], value);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(keys[index]) ^ Objects.hashCode(values[index]);
		}

		@Override
		public String toString() {
			return Objects.toString(keys[index]) + "=" + Objects.toString(values[index]);
		}
	}

	private final class MapEntrySet extends AbstractObjectSet<Object2ObjectMap.Entry<T, V>> implements FastEntrySet<T, V> {
		@Override
		public ObjectIterator<Object2ObjectMap.Entry<T, V>> fastIterator() {
			return new FastEntryIterator();
		}

		@Override
		public ObjectIterator<Object2ObjectMap.Entry<T, V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public void forEach(Consumer<? super Object2ObjectMap.Entry<T, V>> action) {
			if(containsNull) action.accept(new BasicEntry<>(keys[nullIndex], values[nullIndex]));
			for(int i = nullIndex-1;i>=0;i--)
				if(keys[i] != null) action.accept(new BasicEntry<>(keys[i], values[i]));
		}

		@Override
		public void fastForEach(Consumer<? super Object2ObjectMap.Entry<T, V>> action) {
			BasicEntry<T, V> entry = new BasicEntry<>();
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				action.accept(entry);
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					action.accept(entry);
				}
			}
		}

		@Override
		public void forEachIndexed(IntObjectConsumer<Object2ObjectMap.Entry<T, V>> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(0, new BasicEntry<>(keys[nullIndex], values[nullIndex]));
			for(int i = nullIndex-1, index = containsNull ? 1 : 0;i>=0;i--) {
				if(keys[i] != null) action.accept(index++, new BasicEntry<>(keys[i], values[i]));
			}
		}

		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, Object2ObjectMap.Entry<T, V>> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(input, new BasicEntry<>(keys[nullIndex], values[nullIndex]));
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) action.accept(input, new BasicEntry<>(keys[i], values[i]));
			}
		}

		@Override
		public boolean matchesAny(Predicate<Object2ObjectMap.Entry<T, V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return false;
			BasicEntry<T, V> entry = new BasicEntry<>();
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				if(filter.test(entry)) return true;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					if(filter.test(entry)) return true;
				}
			}
			return false;
		}

		@Override
		public boolean matchesNone(Predicate<Object2ObjectMap.Entry<T, V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			BasicEntry<T, V> entry = new BasicEntry<>();
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				if(filter.test(entry)) return false;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					if(filter.test(entry)) return false;
				}
			}
			return true;
		}

		@Override
		public boolean matchesAll(Predicate<Object2ObjectMap.Entry<T, V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			BasicEntry<T, V> entry = new BasicEntry<>();
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				if(!filter.test(entry)) return false;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					if(!filter.test(entry)) return false;
				}
			}
			return true;
		}

		@Override
		public <E> E reduce(E identity, BiFunction<E, Object2ObjectMap.Entry<T, V>, E> operator) {
			Objects.requireNonNull(operator);
			E state = identity;
			if(containsNull) state = operator.apply(state, new BasicEntry<>(keys[nullIndex], values[nullIndex]));
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				state = operator.apply(state, new BasicEntry<>(keys[i], values[i]));
			}
			return state;
		}

		@Override
		public Object2ObjectMap.Entry<T, V> reduce(ObjectObjectUnaryOperator<Object2ObjectMap.Entry<T, V>, Object2ObjectMap.Entry<T, V>> operator) {
			Objects.requireNonNull(operator);
			Object2ObjectMap.Entry<T, V> state = null;
			boolean empty = true;
			if(containsNull) {
				state = new BasicEntry<>(keys[nullIndex], values[nullIndex]);
				empty = false;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				if(empty) {
					empty = false;
					state = new BasicEntry<>(keys[i], values[i]);
					continue;
				}
				state = operator.apply(state, new BasicEntry<>(keys[i], values[i]));
			}
			return state;
		}

		@Override
		public Object2ObjectMap.Entry<T, V> findFirst(Predicate<Object2ObjectMap.Entry<T, V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return null;
			BasicEntry<T, V> entry = new BasicEntry<>();
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				if(filter.test(entry)) return entry;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					if(filter.test(entry)) return entry;
				}
			}
			return null;
		}

		@Override
		public int count(Predicate<Object2ObjectMap.Entry<T, V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return 0;
			BasicEntry<T, V> entry = new BasicEntry<>();
			int result = 0;
			if(containsNull) {
				entry.set(keys[nullIndex], values[nullIndex]);
				if(filter.test(entry)) result++;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) {
					entry.set(keys[i], values[i]);
					if(filter.test(entry)) result++;
				}
			}
			return result;
		}

		@Override
		public int size() {
			return Object2ObjectOpenHashMap.this.size();
		}

		@Override
		public void clear() {
			Object2ObjectOpenHashMap.this.clear();
		}

		@Override
		public boolean contains(Object o) {
			if(o instanceof Map.Entry) {
				if(o instanceof Object2ObjectMap.Entry) {
					Object2ObjectMap.Entry<T, V> entry = (Object2ObjectMap.Entry<T, V>)o;
					int index = Object2ObjectOpenHashMap.this.findIndex(entry.getKey());
					if(index >= 0) return Objects.equals(entry.getValue(), Object2ObjectOpenHashMap.this.values[index]);
				}
				else {
					Map.Entry<?, ?> entry = (Map.Entry<?, ?>)o;
					int index = Object2ObjectOpenHashMap.this.findIndex(entry.getKey());
					if(index >= 0) return Objects.equals(entry.getValue(), Object2ObjectOpenHashMap.this.values[index]);
				}
			}
			return false;
		}

		@Override
		public boolean remove(Object o) {
			if(o instanceof Map.Entry) {
				if(o instanceof Object2ObjectMap.Entry) {
					Object2ObjectMap.Entry<T, V> entry = (Object2ObjectMap.Entry<T, V>)o;
					return Object2ObjectOpenHashMap.this.remove(entry.getKey(), entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)o;
				return Object2ObjectOpenHashMap.this.remove(entry.getKey(), entry.getValue());
			}
			return false;
		}
	}

	private final class KeySet extends AbstractObjectSet<T> {
		@Override
		public boolean contains(Object e) {
			return containsKey(e);
		}

		@Override
		public boolean remove(Object o) {
			int oldSize = size;
			Object2ObjectOpenHashMap.this.remove(o);
			return size != oldSize;
		}

		@Override
		public boolean add(T o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectIterator<T> iterator() {
			return new KeyIterator();
		}

		@Override
		public int size() {
			return Object2ObjectOpenHashMap.this.size();
		}

		@Override
		public void clear() {
			Object2ObjectOpenHashMap.this.clear();
		}

		@Override
		public KeySet copy() { throw new UnsupportedOperationException(); }

		@Override
		public void forEach(Consumer<? super T> action) {
			if(containsNull) action.accept(keys[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--)
				if(keys[i] != null) action.accept(keys[i]);
		}

		@Override
		public void forEachIndexed(IntObjectConsumer<T> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(0, keys[nullIndex]);
			for(int i = nullIndex-1, index = containsNull ? 1 : 0;i>=0;i--) {
				if(keys[i] != null) action.accept(index++, keys[i]);
			}
		}

		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, T> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(input, keys[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) action.accept(input, keys[i]);
			}
		}

		@Override
		public boolean matchesAny(Predicate<T> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return false;
			if(containsNull && filter.test(keys[nullIndex])) return true;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(keys[i])) return true;
			}
			return false;
		}

		@Override
		public boolean matchesNone(Predicate<T> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			if(containsNull && filter.test(keys[nullIndex])) return false;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(keys[i])) return false;
			}
			return true;
		}

		@Override
		public boolean matchesAll(Predicate<T> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			if(containsNull && !filter.test(keys[nullIndex])) return false;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && !filter.test(keys[i])) return false;
			}
			return true;
		}

		@Override
		public <E> E reduce(E identity, BiFunction<E, T, E> operator) {
			Objects.requireNonNull(operator);
			E state = identity;
			if(containsNull) state = operator.apply(state, keys[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				state = operator.apply(state, keys[i]);
			}
			return state;
		}

		@Override
		public T reduce(ObjectObjectUnaryOperator<T, T> operator) {
			Objects.requireNonNull(operator);
			T state = null;
			boolean empty = true;
			if(containsNull) {
				state = keys[nullIndex];
				empty = false;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				if(empty) {
					empty = false;
					state = keys[i];
					continue;
				}
				state = operator.apply(state, keys[i]);
			}
			return state;
		}

		@Override
		public T findFirst(Predicate<T> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return null;
			if(containsNull && filter.test(keys[nullIndex])) return keys[nullIndex];
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(keys[i])) return keys[i];
			}
			return null;
		}

		@Override
		public int count(Predicate<T> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return 0;
			int result = 0;
			if(containsNull && filter.test(keys[nullIndex])) result++;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(keys[i])) result++;
			}
			return result;
		}
	}

	private class Values extends AbstractObjectCollection<V> {
		@Override
		public boolean contains(Object e) {
			return containsValue(e);
		}

		@Override
		public boolean add(V o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectIterator<V> iterator() {
			return new ValueIterator();
		}

		@Override
		public int size() {
			return Object2ObjectOpenHashMap.this.size();
		}

		@Override
		public void clear() {
			Object2ObjectOpenHashMap.this.clear();
		}

		@Override
		public void forEach(Consumer<? super V> action) {
			if(containsNull) action.accept(values[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--)
				if(keys[i] != null) action.accept(values[i]);
		}

		@Override
		public void forEachIndexed(IntObjectConsumer<V> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(0, values[nullIndex]);
			for(int i = nullIndex-1, index = containsNull ? 1 : 0;i>=0;i--) {
				if(keys[i] != null) action.accept(index++, values[i]);
			}
		}

		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, V> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			if(containsNull) action.accept(input, values[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) action.accept(input, values[i]);
			}
		}

		@Override
		public boolean matchesAny(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return false;
			if(containsNull && filter.test(values[nullIndex])) return true;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(values[i])) return true;
			}
			return false;
		}

		@Override
		public boolean matchesNone(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			if(containsNull && filter.test(values[nullIndex])) return false;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(values[i])) return false;
			}
			return true;
		}

		@Override
		public boolean matchesAll(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			if(containsNull && !filter.test(values[nullIndex])) return false;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && !filter.test(values[i])) return false;
			}
			return true;
		}

		@Override
		public <E> E reduce(E identity, BiFunction<E, V, E> operator) {
			Objects.requireNonNull(operator);
			E state = identity;
			if(containsNull) state = operator.apply(state, values[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				state = operator.apply(state, values[i]);
			}
			return state;
		}

		@Override
		public V reduce(ObjectObjectUnaryOperator<V, V> operator) {
			Objects.requireNonNull(operator);
			V state = null;
			boolean empty = true;
			if(containsNull) {
				state = values[nullIndex];
				empty = false;
			}
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] == null) continue;
				if(empty) {
					empty = false;
					state = values[i];
					continue;
				}
				state = operator.apply(state, values[i]);
			}
			return state;
		}

		@Override
		public V findFirst(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return null;
			if(containsNull && filter.test(values[nullIndex])) return values[nullIndex];
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(values[i])) return values[i];
			}
			return null;
		}

		@Override
		public int count(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return 0;
			int result = 0;
			if(containsNull && filter.test(values[nullIndex])) result++;
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null && filter.test(values[i])) result++;
			}
			return result;
		}
	}

	private class FastEntryIterator extends MapIterator implements ObjectIterator<Object2ObjectMap.Entry<T, V>> {
		MapEntry entry = new MapEntry();
		@Override
		public Object2ObjectMap.Entry<T, V> next() {
			entry.index = nextEntry();
			return entry;
		}
	}

	private class EntryIterator extends MapIterator implements ObjectIterator<Object2ObjectMap.Entry<T, V>> {
		MapEntry entry;
		@Override
		public Object2ObjectMap.Entry<T, V> next() {
			return entry = new MapEntry(nextEntry());
		}

		@Override
		public void remove() {
			super.remove();
			entry.index = -1;
		}
	}

	private class KeyIterator extends MapIterator implements ObjectIterator<T> {
		@Override
		public T next() {
			return keys[nextEntry()];
		}
	}

	private class ValueIterator extends MapIterator implements ObjectIterator<V> {
		@Override
		public V next() {
			return values[nextEntry()];
		}
	}

	private class MapIterator {
		int pos = nullIndex;
		int returnedPos = -1;
		int lastReturned = -1;
		int nextIndex = Integer.MIN_VALUE;
		boolean returnNull = containsNull;
		T[] wrapped = null;
		int wrappedIndex = 0;

		public boolean hasNext() {
			if(nextIndex == Integer.MIN_VALUE) {
				if(returnNull) {
					returnNull = false;
					nextIndex = nullIndex;
				}
				else
				{
					while(true) {
						if(--pos < 0) {
							if(wrapped == null || wrappedIndex <= -pos - 1) break;
							nextIndex = -pos - 1;
							break;
						}
						if(keys[pos] != null){
							nextIndex = pos;
							break;
						}
					}
				}
			}
			return nextIndex != Integer.MIN_VALUE;
		}

		public int nextEntry() {
			if(!hasNext()) throw new NoSuchElementException();
			returnedPos = pos;
			if(nextIndex < 0){
				lastReturned = Integer.MAX_VALUE;
				int value = findIndex(wrapped[nextIndex]);
				if(value < 0) throw new IllegalStateException("Entry ["+nextIndex+"] was removed during Iteration");
				nextIndex = Integer.MIN_VALUE;
				return value;
			}
			int value = (lastReturned = nextIndex);
			nextIndex = Integer.MIN_VALUE;
			return value;
		}

		public void remove() {
			if(lastReturned == -1) throw new IllegalStateException();
			if(lastReturned == nullIndex) {
				containsNull = false;
				keys[nullIndex] = null;
				values[nullIndex] = null;
			}
			else if(returnedPos >= 0) shiftKeys(returnedPos);
			else {
				Object2ObjectOpenHashMap.this.remove(wrapped[-returnedPos - 1]);
				lastReturned = -1;
				return;
			}
			size--;
			lastReturned = -1;
		}

		private void shiftKeys(int startPos) {
			int slot, last;
			T current;
			while(true) {
				startPos = ((last = startPos) + 1) & mask;
				while(true){
					if((current = keys[startPos]) == null) {
						keys[last] = null;
						values[last] = null;
						return;
					}
					slot = HashUtil.mix(Objects.hashCode(current)) & mask;
					if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
					startPos = ++startPos & mask;
				}
				if(startPos < last) addWrapper(keys[startPos]);
				keys[last] = current;
				values[last] = values[startPos];
			}
		}

		private void addWrapper(T value) {
			if(wrapped == null) wrapped = (T[])new Object[2];
			else if(wrappedIndex >= wrapped.length) {
				T[] newArray = (T[])new Object[wrapped.length * 2];
				System.arraycopy(wrapped, 0, newArray, 0, wrapped.length);
				wrapped = newArray;
			}
			wrapped[wrappedIndex++] = value;
		}
	}
}
