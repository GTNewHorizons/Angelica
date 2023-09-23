package speiger.src.collections.ints.maps.impl.misc;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.IntPredicate;

import speiger.src.collections.ints.collections.IntBidirectionalIterator;
import speiger.src.collections.ints.functions.IntConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectIntConsumer;
import speiger.src.collections.ints.functions.consumer.IntIntConsumer;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.functions.function.IntObjectUnaryOperator;
import speiger.src.collections.ints.functions.function.IntIntUnaryOperator;
import speiger.src.collections.ints.lists.IntListIterator;
import speiger.src.collections.ints.maps.abstracts.AbstractInt2ObjectMap;
import speiger.src.collections.ints.maps.interfaces.Int2ObjectMap;
import speiger.src.collections.ints.maps.interfaces.Int2ObjectOrderedMap;
import speiger.src.collections.ints.sets.AbstractIntSet;
import speiger.src.collections.ints.sets.IntOrderedSet;
import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;

import speiger.src.collections.objects.collections.ObjectBidirectionalIterator;
import speiger.src.collections.objects.lists.ObjectListIterator;
import speiger.src.collections.objects.sets.AbstractObjectSet;
import speiger.src.collections.objects.sets.ObjectOrderedSet;
import speiger.src.collections.utils.HashUtil;

/**
 * A Very Specific Type Specific implementation of a ArrayMap.
 * This type of map is for very specific use cases that usaully would have lead to Tupled Lists otherwise.
 * It also does not allow duplication (except for array constructors) and checks from last to first.
 * It is not designed to be used as a HashMap replacement due to the poor performance it would cause.
 * @note in this implementation SubMaps do NOT keep track of parent changes fully. For performance reasons it will just have a start/end index and not values
 * Anything within that range will be updated appropiatly a shrink/growth of elements will break SubMaps in some ways. This can be useful but be careful
 * @note this implementation does not shrink and only grows.
 * @param <V> the keyType of elements maintained by this Collection
 */
public class Int2ObjectArrayMap<V> extends AbstractInt2ObjectMap<V> implements Int2ObjectOrderedMap<V>
{
	/** The Backing keys array */
	protected transient int[] keys;
	/** The Backing values array */
	protected transient V[] values;
	/** Amount of Elements stored in the ArrayMap */
	protected int size = 0;
	/** KeySet cache */
	protected IntOrderedSet keySet;
	/** Values cache */
	protected ObjectCollection<V> valuesC;
	/** EntrySet cache */
	protected FastOrderedSet<V> entrySet;

	/**
	 * Default Constructor
	 */
	public Int2ObjectArrayMap() {
		this(HashUtil.DEFAULT_MIN_CAPACITY);
	}

	/**
	 * Constructor that defines the minimum capacity
	 * @param minCapacity the minimum capacity the HashMap is allowed to be.
	 * @throws IllegalStateException if the minimum capacity is negative
	 */
	public Int2ObjectArrayMap(int minCapacity) {
		if(minCapacity < 0)	throw new IllegalStateException("Minimum Capacity is negative. This is not allowed");
		keys = new int[minCapacity];
		values = (V[])new Object[minCapacity];
	}

	/**
	 * Helper constructor that allow to create a map from boxed values (it will unbox them)
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @throws IllegalStateException if the keys and values do not match in length
	 */
	public Int2ObjectArrayMap(Integer[] keys, V[] values) {
		this(keys, values, keys.length);
	}

	/**
	 * Helper constructor that allow to create a map from boxed values (it will unbox them) with a custom length
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @param length the amount of values that should be pulled from the array
	 * @throws IllegalStateException if the keys and values do not match in length
	 */
	public Int2ObjectArrayMap(Integer[] keys, V[] values, int length) {
		this(length);
		if(keys.length != values.length) throw new IllegalStateException("Input Arrays are not equal size");
		putAll(keys, values, 0, length);
	}

	/**
	 * Helper constructor that allow to create a map from unboxed values
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @throws IllegalStateException if the keys and values do not match in lenght
	 */
	public Int2ObjectArrayMap(int[] keys, V[] values) {
		this(keys, values, keys.length);
	}

	/**
	 * Helper constructor that allow to create a map from unboxed values
	 * @param keys the keys that should be put into the map
	 * @param values the values that should be put into the map.
	 * @param length the amount of values that should be pulled from the array
	 * @throws IllegalStateException if the keys and values do not match in lenght
	 */
	public Int2ObjectArrayMap(int[] keys, V[] values, int length) {
		this(length);
		if(keys.length != values.length) throw new IllegalStateException("Input Arrays are not equal size");
		putAll(keys, values, 0, length);
	}

	/**
	 * A Helper constructor that allows to create a Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
	 */
	public Int2ObjectArrayMap(Map<? extends Integer, ? extends V> map) {
		this(map.size());
		putAll(map);
	}

	/**
	 * A Type Specific Helper function that allows to create a new Map with exactly the same values as the provided map.
	 * @param map the values that should be present in the map
 	 */
	public Int2ObjectArrayMap(Int2ObjectMap<V> map) {
		this(map.size());
		for(ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(map);iter.hasNext();size++) {
			Int2ObjectMap.Entry<V> entry = iter.next();
			keys[size] = entry.getIntKey();
			values[size] = entry.getValue();
		}
	}

	@Override
	public V put(int key, V value) {
		int index = findIndex(key);
		if(index < 0) {
			insertIndex(size++, key, value);
			return getDefaultReturnValue();
		}
		V oldValue = values[index];
		values[index] = value;
		return oldValue;
	}

	@Override
	public V putIfAbsent(int key, V value) {
		int index = findIndex(key);
		if(index < 0) {
			insertIndex(size++, key, value);
			return getDefaultReturnValue();
		}
		else if(Objects.equals(values[index], getDefaultReturnValue())) {
			V oldValue = values[index];
			values[index] = value;
			return oldValue;
		}
		return values[index];
	}

	@Override
	public V putAndMoveToFirst(int key, V value) {
		int index = findIndex(key);
		if(index < 0) {
			insertIndex(0, key, value);
			size++;
			return getDefaultReturnValue();
		}
		V lastValue = values[index];
		values[index] = value;
		moveIndexToFirst(index);
		return lastValue;
	}

	@Override
	public V putAndMoveToLast(int key, V value) {
		int index = findIndex(key);
		if(index < 0) {
			insertIndex(size++, key, value);
			return getDefaultReturnValue();
		}
		V lastValue = values[index];
		values[index] = value;
		moveIndexToLast(index);
		return lastValue;
	}

	@Override
	public boolean moveToFirst(int key) {
		int index = findIndex(key);
		if(index > 0) {
			moveIndexToFirst(index);
			return true;
		}
		return false;
	}

	@Override
	public boolean moveToLast(int key) {
		int index = findIndex(key);
		if(index >= 0 && index < size-1) {
			moveIndexToLast(index);
			return true;
		}
		return false;
	}

	@Override
	public boolean containsKey(int key) {
		return findIndex(key) >= 0;
	}

	@Override
	public boolean containsKey(Object key) {
		return findIndex(key) >= 0;
	}

	@Override
	public boolean containsValue(Object value) {
		return findValue(value) >= 0;
	}

	@Override
	public V get(int key) {
		int index = findIndex(key);
		return index < 0 ? getDefaultReturnValue() : values[index];
	}

	@Override
	public V getOrDefault(int key, V defaultValue) {
		int index = findIndex(key);
		return index < 0 ? defaultValue : values[index];
	}

	@Override
	public V getAndMoveToFirst(int key) {
		int index = findIndex(key);
		if(index >= 0) {
			V value = values[index];
			moveIndexToFirst(index);
			return value;
		}
		return getDefaultReturnValue();
	}

	@Override
	public V getAndMoveToLast(int key) {
		int index = findIndex(key);
		if(index >= 0) {
			V value = values[index];
			moveIndexToLast(index);
			return value;
		}
		return getDefaultReturnValue();
	}

	@Override
	public int firstIntKey() {
		if(size <= 0) throw new NoSuchElementException();
		return keys[0];
	}

	@Override
	public int lastIntKey() {
		if(size <= 0) throw new NoSuchElementException();
		return keys[size-1];
	}

	@Override
	public V firstValue() {
		if(size <= 0) throw new NoSuchElementException();
		return values[0];
	}

	@Override
	public V lastValue() {
		if(size <= 0) throw new NoSuchElementException();
		return values[size-1];
	}

	@Override
	public int pollFirstIntKey() {
		if(size == 0) throw new NoSuchElementException();
		int result = keys[0];
		removeIndex(0);
		return result;
	}

	@Override
	public int pollLastIntKey() {
		if(size == 0) throw new NoSuchElementException();
		int result = keys[size-1];
		removeIndex(size-1);
		return result;
	}

	@Override
	public V remove(int key) {
		int index = findIndex(key);
		if(index < 0) return getDefaultReturnValue();
		V value = values[index];
		removeIndex(index);
		return value;
	}

	@Override
	public V removeOrDefault(int key, V defaultValue) {
		int index = findIndex(key);
		if(index < 0) return defaultValue;
		V value = values[index];
		removeIndex(index);
		return value;
	}

	@Override
	public boolean remove(int key, V value) {
		int index = findIndex(key, value);
		if(index < 0) return false;
		removeIndex(index);
		return true;
	}

	@Override
	public V remove(Object key) {
		int index = findIndex(key);
		if(index < 0) return getDefaultReturnValue();
		V value = values[index];
		removeIndex(index);
		return value;
	}

	@Override
	public boolean remove(Object key, Object value) {
		int index = findIndex(key, value);
		if(index < 0) return false;
		removeIndex(index);
		return true;
	}

	@Override
	public void forEach(IntObjectConsumer<V> action) {
		if(size() <= 0) return;
		for(int i = 0;i<size;i++)
			action.accept(keys[i], values[i]);
	}

	@Override
	public IntOrderedSet keySet() {
		if(keySet == null) keySet = new KeySet();
		return keySet;
	}

	@Override
	public ObjectCollection<V> values() {
		if(valuesC == null) valuesC = new Values();
		return valuesC;
	}

	@Override
	public ObjectOrderedSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet() {
		if(entrySet == null) entrySet = new MapEntrySet();
		return entrySet;
	}

	@Override
	public boolean replace(int key, V oldValue, V newValue) {
		int index = findIndex(key);
		if(index < 0 || values[index] != oldValue) return false;
		values[index] = newValue;
		return true;
	}

	@Override
	public V replace(int key, V value) {
		int index = findIndex(key);
		if(index < 0) return getDefaultReturnValue();
		V oldValue = values[index];
		values[index] = value;
		return oldValue;
	}

	@Override
	public V compute(int key, IntObjectUnaryOperator<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index == -1) {
			V newValue = mappingFunction.apply(key, getDefaultReturnValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insertIndex(size++, key, newValue);
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
	public V computeIfAbsent(int key, IntFunction<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index == -1) {
			V newValue = mappingFunction.apply(key);
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insertIndex(size++, key, newValue);
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
	public V supplyIfAbsent(int key, ObjectSupplier<V> valueProvider) {
		Objects.requireNonNull(valueProvider);
		int index = findIndex(key);
		if(index == -1) {
			V newValue = valueProvider.get();
			if(Objects.equals(newValue, getDefaultReturnValue())) return newValue;
			insertIndex(size++, key, newValue);
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
	public V computeIfPresent(int key, IntObjectUnaryOperator<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int index = findIndex(key);
		if(index == -1 || Objects.equals(values[index], getDefaultReturnValue())) return getDefaultReturnValue();
		V newValue = mappingFunction.apply(key, values[index]);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			removeIndex(index);
			return newValue;
		}
		values[index] = newValue;
		return newValue;
	}

	@Override
	public V merge(int key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		Objects.requireNonNull(value);
		int index = findIndex(key);
		V newValue = index == -1 || Objects.equals(values[index], getDefaultReturnValue()) ? value : mappingFunction.apply(values[index], value);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			if(index >= 0)
				removeIndex(index);
		}
		else if(index == -1) insertIndex(size++, key, newValue);
		else values[index] = newValue;
		return newValue;
	}

	@Override
	public void mergeAll(Int2ObjectMap<V> m, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(Int2ObjectMap.Entry<V> entry : getFastIterable(m)) {
			int key = entry.getIntKey();
			int index = findIndex(key);
			V newValue = index == -1 || Objects.equals(values[index], getDefaultReturnValue()) ? entry.getValue() : mappingFunction.apply(values[index], entry.getValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) {
				if(index >= 0)
					removeIndex(index);
			}
			else if(index == -1) insertIndex(size++, key, newValue);
			else values[index] = newValue;
		}
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear() {
		Arrays.fill(keys, 0, size, 0);
		Arrays.fill(values, 0, size, null);
		size = 0;
	}

	public Int2ObjectArrayMap<V> copy() {
		Int2ObjectArrayMap<V> map = new Int2ObjectArrayMap<>();
		map.size = size;
		map.keys = Arrays.copyOf(keys, keys.length);
		map.values = Arrays.copyOf(values, keys.length);
		return map;
	}

	protected void moveIndexToFirst(int index) {
		if(index == 0) return;
		int key = keys[index];
		V value = values[index];
		System.arraycopy(keys, 0, keys, 1, index);
		System.arraycopy(values, 0, values, 1, index);
		keys[0] = key;
		values[0] = value;
	}

	protected void moveIndexToLast(int index) {
		if(index == size-1) return;
		int key = keys[index];
		V value = values[index];
		System.arraycopy(keys, index+1, keys, index, size-index-1);
		System.arraycopy(values, index+1, values, index, size-index-1);
		keys[size-1] = key;
		values[size-1] = value;
	}

	protected void grow(int newSize) {
		if(newSize < keys.length) return;
		newSize = Math.max(newSize, keys.length == 0 ? 2 : keys.length * 2);
		keys = Arrays.copyOf(keys, newSize);
		values = Arrays.copyOf(values, newSize);
	}

	protected void insertIndex(int index, int key, V value) {
		grow(size+1);
		if(index != size) {
			System.arraycopy(keys, index, keys, index+1, size-index);
			System.arraycopy(values, index, values, index+1, size-index);
		}
		keys[index] = key;
		values[index] = value;
	}

	protected void removeRange(int from, int to) {
		if(from < 0 || from >= size) throw new IllegalStateException("From Element ");
		int length = to - from;
		if(length <= 0) return;
		if(to != size) {
			System.arraycopy(keys, to, keys, from, size - to);
			System.arraycopy(values, to, values, from, size - to);
		}
		for(int i = 0;i<length;i++) {
			keys[i+to] = 0;
			values[i+to] = null;
		}
		size -= length;
	}

	protected void removeIndex(int index) {
		if(index == size-1) {
			size--;
			keys[size] = 0;
			values[size] = null;
			return;
		}
		System.arraycopy(keys, index+1, keys, index, size-index-1);
		System.arraycopy(values, index+1, values, index, size-index-1);
		size--;
		keys[size] = 0;
		values[size] = null;
	}

	protected int findIndex(int key, V value) {
		for(int i = size-1;i>=0;i--)
			if(keys[i] == key && Objects.equals(values[i], value)) return i;
		return -1;
	}

	protected int findIndex(int key) {
		for(int i = size-1;i>=0;i--)
			if(keys[i] == key) return i;
		return -1;
	}

	protected int findIndex(Object key, Object value) {
		for(int i = size-1;i>=0;i--)
			if(Objects.equals(key, Integer.valueOf(keys[i])) && Objects.equals(value, values[i])) return i;
		return -1;
	}

	protected int findIndex(Object key) {
		for(int i = size-1;i>=0;i--)
			if(Objects.equals(key, Integer.valueOf(keys[i]))) return i;
		return -1;
	}

	protected int findValue(Object value) {
		for(int i = size-1;i>=0;i--)
			if(Objects.equals(value, values[i])) return i;
		return -1;
	}

	private class MapEntrySet extends AbstractObjectSet<Int2ObjectMap.Entry<V>> implements FastOrderedSet<V> {
		@Override
		public boolean addAndMoveToFirst(Int2ObjectMap.Entry<V> o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAndMoveToLast(Int2ObjectMap.Entry<V> o) { throw new UnsupportedOperationException(); }

		@Override
		public boolean moveToFirst(Int2ObjectMap.Entry<V> o) {
			return Int2ObjectArrayMap.this.moveToFirst(o.getIntKey());
		}

		@Override
		public boolean moveToLast(Int2ObjectMap.Entry<V> o) {
			return Int2ObjectArrayMap.this.moveToLast(o.getIntKey());
		}

		@Override
		public Int2ObjectMap.Entry<V> first() {
			return new BasicEntry<>(firstIntKey(), firstValue());
		}

		@Override
		public Int2ObjectMap.Entry<V> last() {
			return new BasicEntry<>(lastIntKey(), lastValue());
		}

		@Override
		public Int2ObjectMap.Entry<V> pollFirst() {
			BasicEntry<V> entry = new BasicEntry<>(firstIntKey(), firstValue());
			pollFirstIntKey();
			return entry;
		}

		@Override
		public Int2ObjectMap.Entry<V> pollLast() {
			BasicEntry<V> entry = new BasicEntry<>(lastIntKey(), lastValue());
			pollLastIntKey();
			return entry;
		}

		@Override
		public ObjectBidirectionalIterator<Int2ObjectMap.Entry<V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public ObjectBidirectionalIterator<Int2ObjectMap.Entry<V>> iterator(Int2ObjectMap.Entry<V> fromElement) {
			return new EntryIterator(fromElement.getIntKey());
		}

		@Override
		public ObjectBidirectionalIterator<Int2ObjectMap.Entry<V>> fastIterator() {
			return new FastEntryIterator();
		}

		@Override
		public ObjectBidirectionalIterator<Int2ObjectMap.Entry<V>> fastIterator(int fromElement) {
			return new FastEntryIterator(fromElement);
		}

		@Override
		public MapEntrySet copy() { throw new UnsupportedOperationException(); }

		@Override
		public void forEach(Consumer<? super Int2ObjectMap.Entry<V>> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++)
				action.accept(new BasicEntry<>(keys[i], values[i]));
		}

		@Override
		public void fastForEach(Consumer<? super Int2ObjectMap.Entry<V>> action) {
			Objects.requireNonNull(action);
			if(size() <= 0) return;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				action.accept(entry);
			}
		}

		@Override
		public void forEachIndexed(IntObjectConsumer<Int2ObjectMap.Entry<V>> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++) {
				action.accept(i, new BasicEntry<>(keys[i], values[i]));
			}
		}

		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, Int2ObjectMap.Entry<V>> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++) {
				action.accept(input, new BasicEntry<>(keys[i], values[i]));
			}
		}

		@Override
		public boolean matchesAny(Predicate<Int2ObjectMap.Entry<V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return false;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				if(filter.test(entry)) return true;
			}
			return false;
		}

		@Override
		public boolean matchesNone(Predicate<Int2ObjectMap.Entry<V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				if(filter.test(entry)) return false;
			}
			return true;
		}

		@Override
		public boolean matchesAll(Predicate<Int2ObjectMap.Entry<V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return true;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				if(!filter.test(entry)) return false;
			}
			return true;
		}

		@Override
		public <E> E reduce(E identity, BiFunction<E, Int2ObjectMap.Entry<V>, E> operator) {
			Objects.requireNonNull(operator);
			E state = identity;
			for(int i = 0;i<size;i++) {
				state = operator.apply(state, new BasicEntry<>(keys[i], values[i]));
			}
			return state;
		}

		@Override
		public Int2ObjectMap.Entry<V> reduce(ObjectObjectUnaryOperator<Int2ObjectMap.Entry<V>, Int2ObjectMap.Entry<V>> operator) {
			Objects.requireNonNull(operator);
			Int2ObjectMap.Entry<V> state = null;
			boolean empty = true;
			for(int i = 0;i<size;i++) {
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
		public Int2ObjectMap.Entry<V> findFirst(Predicate<Int2ObjectMap.Entry<V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return null;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				if(filter.test(entry)) return entry;
			}
			return null;
		}

		@Override
		public int count(Predicate<Int2ObjectMap.Entry<V>> filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return 0;
			int result = 0;
			BasicEntry<V> entry = new BasicEntry<>();
			for(int i = 0;i<size;i++) {
				entry.set(keys[i], values[i]);
				if(filter.test(entry)) result++;
			}
			return result;
		}

		@Override
		@Deprecated
		public boolean contains(Object o) {
			if(o instanceof Map.Entry) {
				if(o instanceof Int2ObjectMap.Entry) {
					Int2ObjectMap.Entry<V> entry = (Int2ObjectMap.Entry<V>)o;
					int index = Int2ObjectArrayMap.this.findIndex(entry.getIntKey());
					if(index >= 0) return Objects.equals(entry.getValue(), Int2ObjectArrayMap.this.values[index]);
				}
				else {
					Map.Entry<?, ?> entry = (Map.Entry<?, ?>)o;
					int index = Int2ObjectArrayMap.this.findIndex(entry.getKey());
					if(index >= 0) return Objects.equals(entry.getValue(), Int2ObjectArrayMap.this.values[index]);
				}
			}
			return false;
		}

		@Override
		@Deprecated
		public boolean remove(Object o) {
			if(o instanceof Map.Entry) {
				if(o instanceof Int2ObjectMap.Entry) {
					Int2ObjectMap.Entry<V> entry = (Int2ObjectMap.Entry<V>)o;
					return Int2ObjectArrayMap.this.remove(entry.getIntKey(), entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)o;
				return Int2ObjectArrayMap.this.remove(entry.getKey(), entry.getValue());
			}
			return false;
		}

		@Override
		public int size() {
			return Int2ObjectArrayMap.this.size();
		}

		@Override
		public void clear() {
			Int2ObjectArrayMap.this.clear();
		}
	}

	private class KeySet extends AbstractIntSet implements IntOrderedSet {
		@Override
		public boolean contains(int e) { return containsKey(e); }

		@Override
		public boolean remove(int o) {
			int oldSize = size;
			Int2ObjectArrayMap.this.remove(o);
			return size != oldSize;
		}

		@Override
		public boolean add(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAndMoveToFirst(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAndMoveToLast(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean moveToFirst(int o) { return Int2ObjectArrayMap.this.moveToFirst(o); }
		@Override
		public boolean moveToLast(int o) { return Int2ObjectArrayMap.this.moveToLast(o); }
		@Override
		public IntListIterator iterator() { return new KeyIterator(); }
		@Override
		public IntBidirectionalIterator iterator(int fromElement) { return new KeyIterator(fromElement); }
		@Override
		public int size() { return Int2ObjectArrayMap.this.size(); }
		@Override
		public void clear() { Int2ObjectArrayMap.this.clear(); }
		@Override
		public int firstInt() { return firstIntKey(); }
		@Override
		public int pollFirstInt() { return pollFirstIntKey(); }
		@Override
		public int lastInt() { return lastIntKey(); }
		@Override
		public int pollLastInt() { return pollLastIntKey(); }

		@Override
		public KeySet copy() { throw new UnsupportedOperationException(); }

		@Override
		public void forEach(IntConsumer action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;action.accept(keys[i++]));
		}

		@Override
		public void forEachIndexed(IntIntConsumer action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;action.accept(i, keys[i++]));
		}

		@Override
		public <E> void forEach(E input, ObjectIntConsumer<E> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;action.accept(input, keys[i++]));
		}

		@Override
		public boolean matchesAny(IntPredicate filter) {
			Objects.requireNonNull(filter);
			if(size() <= 0) return false;
			for(int i = 0;i<size;i++) {
				if(filter.test(keys[i])) return true;
			}
			return false;
		}

		@Override
		public boolean matchesNone(IntPredicate filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(filter.test(keys[i])) return false;
			}
			return true;
		}

		@Override
		public boolean matchesAll(IntPredicate filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(!filter.test(keys[i])) return false;
			}
			return true;
		}

		@Override
		public int reduce(int identity, IntIntUnaryOperator operator) {
			Objects.requireNonNull(operator);
			int state = identity;
			for(int i = 0;i<size;i++) {
				state = operator.applyAsInt(state, keys[i]);
			}
			return state;
		}

		@Override
		public int reduce(IntIntUnaryOperator operator) {
			Objects.requireNonNull(operator);
			int state = 0;
			boolean empty = true;
			for(int i = 0;i<size;i++) {
				if(empty) {
					empty = false;
					state = keys[i];
					continue;
				}
				state = operator.applyAsInt(state, keys[i]);
			}
			return state;
		}

		@Override
		public int findFirst(IntPredicate filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(filter.test(keys[i])) return keys[i];
			}
			return 0;
		}

		@Override
		public int count(IntPredicate filter) {
			Objects.requireNonNull(filter);
			int result = 0;
			for(int i = 0;i<size;i++) {
				if(filter.test(keys[i])) result++;
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
			return Int2ObjectArrayMap.this.size();
		}

		@Override
		public void clear() {
			Int2ObjectArrayMap.this.clear();
		}

		@Override
		public void forEach(Consumer<? super V> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;action.accept(values[i++]));
		}

		@Override
		public void forEachIndexed(IntObjectConsumer<V> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;action.accept(i, values[i++]));
		}

		@Override
		public boolean matchesAny(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(filter.test(values[i])) return true;
			}
			return false;
		}

		@Override
		public boolean matchesNone(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(filter.test(values[i])) return false;
			}
			return true;
		}

		@Override
		public boolean matchesAll(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			for(int i = 0;i<size;i++) {
				if(!filter.test(values[i])) return false;
			}
			return true;
		}

		@Override
		public <E> E reduce(E identity, BiFunction<E, V, E> operator) {
			Objects.requireNonNull(operator);
			E state = identity;
			for(int i = 0;i<size;i++) {
				state = operator.apply(state, values[i]);
			}
			return state;
		}

		@Override
		public V reduce(ObjectObjectUnaryOperator<V, V> operator) {
			Objects.requireNonNull(operator);
			V state = null;
			boolean empty = true;
			for(int i = 0;i<size;i++) {
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
			for(int i = 0;i<size;i++) {
				if(filter.test(values[i])) return values[i];
			}
			return null;
		}

		@Override
		public int count(Predicate<V> filter) {
			Objects.requireNonNull(filter);
			int result = 0;
			for(int i = 0;i<size;i++) {
				if(filter.test(values[i])) result++;
			}
			return result;
		}
	}

	private class FastEntryIterator extends MapIterator implements ObjectListIterator<Int2ObjectMap.Entry<V>> {
		MapEntry entry = new MapEntry();

		public FastEntryIterator() {}
		public FastEntryIterator(int from) {
			index = findIndex(from);
		}

		@Override
		public Int2ObjectMap.Entry<V> next() {
			entry.index = nextEntry();
			return entry;
		}

		@Override
		public Int2ObjectMap.Entry<V> previous() {
			entry.index = previousEntry();
			return entry;
		}

		@Override
		public void set(Int2ObjectMap.Entry<V> e) { throw new UnsupportedOperationException(); }
		@Override
		public void add(Int2ObjectMap.Entry<V> e) { throw new UnsupportedOperationException(); }
	}

	private class EntryIterator extends MapIterator implements ObjectListIterator<Int2ObjectMap.Entry<V>> {
		MapEntry entry = null;

		public EntryIterator() {}
		public EntryIterator(int from) {
			index = findIndex(from);
			if(index == -1) throw new NoSuchElementException();
		}

		@Override
		public Int2ObjectMap.Entry<V> next() {
			return entry = new MapEntry(nextEntry());
		}

		@Override
		public Int2ObjectMap.Entry<V> previous() {
			return entry = new MapEntry(previousEntry());
		}

		@Override
		public void remove() {
			super.remove();
			if(entry != null && entry.index != -1) {
				entry.index = -1;
			}
		}

		@Override
		public void set(Int2ObjectMap.Entry<V> e) { throw new UnsupportedOperationException(); }
		@Override
		public void add(Int2ObjectMap.Entry<V> e) { throw new UnsupportedOperationException(); }
	}

	private class KeyIterator extends MapIterator implements IntListIterator {
		public KeyIterator() {}
		public KeyIterator(int element) {
			index = findIndex(element);
			if(index == -1) throw new NoSuchElementException();
		}

		@Override
		public int previousInt() {
			return keys[previousEntry()];
		}

		@Override
		public int nextInt() {
			return keys[nextEntry()];
		}

		@Override
		public void set(int e) { throw new UnsupportedOperationException(); }

		@Override
		public void add(int e) { throw new UnsupportedOperationException(); }
	}

	private class ValueIterator extends MapIterator implements ObjectListIterator<V> {
		@Override
		public V previous() {
			return values[previousEntry()];
		}

		@Override
		public V next() {
			return values[nextEntry()];
		}

		@Override
		public void set(V e) { throw new UnsupportedOperationException(); }

		@Override
		public void add(V e) { throw new UnsupportedOperationException(); }
	}

	private class MapIterator {
		int index;
		int lastReturned = -1;

		public boolean hasNext() {
			return index < size;
		}

		public boolean hasPrevious() {
			return index > 0;
		}

		public int nextIndex() {
			return index;
		}

		public int previousIndex() {
			return index-1;
		}

		public void remove() {
			if(lastReturned == -1) throw new IllegalStateException();
			removeIndex(lastReturned);
			if(lastReturned < index)
				index--;
			lastReturned = -1;
		}

		public int previousEntry() {
			if(!hasPrevious()) throw new NoSuchElementException();
			index--;
			return (lastReturned = index);
		}

		public int nextEntry() {
			if(!hasNext()) throw new NoSuchElementException();
			lastReturned = index;
			return index++;
		}

		public int skip(int amount) {
			if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
			int steps = Math.min(amount, size() - index);
			index += steps;
			if(steps > 0) lastReturned = Math.min(index-1, size()-1);
			return steps;
		}

		public int back(int amount) {
			if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
			int steps = Math.min(amount, index);
			index -= steps;
			if(steps > 0) lastReturned = Math.min(index, size()-1);
			return steps;
		}
	}

	private class MapEntry implements Int2ObjectMap.Entry<V>, Map.Entry<Integer, V> {
		int index = -1;

		public MapEntry() {}
		public MapEntry(int index) {
			this.index = index;
		}

		@Override
		public int getIntKey() {
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
				if(obj instanceof Int2ObjectMap.Entry) {
					Int2ObjectMap.Entry<V> entry = (Int2ObjectMap.Entry<V>)obj;
					return keys[index] == entry.getIntKey() && Objects.equals(values[index], entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
				Object key = entry.getKey();
				Object value = entry.getValue();
				return key instanceof Integer && keys[index] == ((Integer)key).intValue() && Objects.equals(values[index], value);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(keys[index]) ^ Objects.hashCode(values[index]);
		}

		@Override
		public String toString() {
			return Integer.toString(keys[index]) + "=" + Objects.toString(values[index]);
		}
	}
}
