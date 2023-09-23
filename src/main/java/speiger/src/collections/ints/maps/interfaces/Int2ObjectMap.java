package speiger.src.collections.ints.maps.interfaces;

import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;


import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.functions.function.IntObjectUnaryOperator;

import speiger.src.collections.ints.maps.impl.misc.Int2ObjectArrayMap;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.ints.sets.IntSet;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.sets.ObjectSet;
import speiger.src.collections.utils.HashUtil;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Type Specific Map that reduces memory overhead due to boxing/unboxing of Primitives
 * and some extra helper functions.
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface Int2ObjectMap<V> extends Map<Integer, V>, IntFunction<V>
{
	/**
	 * Helper Class that allows to create Maps without requiring a to type out the entire implementation or know it.
	 * @return a MapBuilder
	 */
	public static MapBuilder builder() {
		return MapBuilder.INSTANCE;
	}

	/**
	 * Method to see what the default return value is.
	 * @return default return value
	 */
	public V getDefaultReturnValue();
	/**
	 * Method to define the default return value if a requested key isn't present
	 * @param v value that should be the default return value
	 * @return itself
	 */
	public Int2ObjectMap<V> setDefaultReturnValue(V v);

	/**
	 * A Function that does a shallow clone of the Map itself.
	 * This function is more optimized then a copy constructor since the Map does not have to be unsorted/resorted.
	 * It can be compared to Cloneable but with less exception risk
	 * @return a Shallow Copy of the Map
	 * @note Wrappers and view Maps will not support this feature
	 */
	public Int2ObjectMap<V> copy();

	/**
	 * Type Specific method to reduce boxing/unboxing of values
	 * @param key the key that should be inserted,
	 * @param value the value that should be inserted
	 * @return the last present value or default return value.
	 * @see Map#put(Object, Object)
	 */
	public V put(int key, V value);

	/**
	 * A Helper method that allows to put int a Int2ObjectMap.Entry into a map.
	 * @param entry then Entry that should be inserted.
	 * @return the last present value or default return value.
	 */
	public default V put(Entry<V> entry) {
		return put(entry.getIntKey(), entry.getValue());
	}

	/**
	 * A Helper method that allows to put int a Map.Entry into a map.
	 * @param entry then Entry that should be inserted.
	 * @return the last present value or default return	value.
	 */
	public default V put(Map.Entry<Integer, V> entry) {
		return put(entry.getKey(), entry.getValue());
	}

	/**
	 * Type Specific array method to bulk add elements into a map without creating a wrapper and increasing performances
	 * @param keys the keys that should be added
	 * @param values the values that should be added
	 * @see Map#putAll(Map)
	 * @throws IllegalStateException if the arrays are not the same size
	 */
	public default void putAll(int[] keys, V[] values) {
		if(keys.length != values.length) throw new IllegalStateException("Array sizes do not match");
		putAll(keys, values, 0, keys.length);
	}

	/**
	 * Type Specific array method to bulk add elements into a map without creating a wrapper and increasing performances
	 * @param keys the keys that should be added
	 * @param values the values that should be added
	 * @param offset where the to start in the array
	 * @param size how many elements should be added
	 * @see Map#putAll(Map)
	 * @throws IllegalStateException if the arrays are not within the range
	 */
	public void putAll(int[] keys, V[] values, int offset, int size);

	/**
	 * Type Specific Object array method to bulk add elements into a map without creating a wrapper and increasing performances
	 * @param keys the keys that should be added
	 * @param values the values that should be added
	 * @see Map#putAll(Map)
	 * @throws IllegalStateException if the arrays are not the same size
	 */
	public default void putAll(Integer[] keys, V[] values) {
		if(keys.length != values.length) throw new IllegalStateException("Array sizes do not match");
		putAll(keys, values, 0, keys.length);
	}

	/**
	 * Type Specific Object array method to bulk add elements into a map without creating a wrapper and increasing performances
	 * @param keys the keys that should be added
	 * @param values the values that should be added
	 * @param offset where the to start in the array
	 * @param size how many elements should be added
	 * @see Map#putAll(Map)
	 * @throws IllegalStateException if the arrays are not within the range
	 */
	public void putAll(Integer[] keys, V[] values, int offset, int size);

	/**
	 * Type Specific method to reduce boxing/unboxing of values
	 * @param key the key that should be inserted,
	 * @param value the value that should be inserted
	 * @return the last present value or default return value.
	 * @see Map#putIfAbsent(Object, Object)
	 */
	public V putIfAbsent(int key, V value);

	/**
	 * Type-Specific bulk put method put elements into the map if not present.
	 * @param m elements that should be added if not present.
	 */
	public void putAllIfAbsent(Int2ObjectMap<V> m);

	/**
	 * Type Specific function for the bull putting of values
	 * @param m the elements that should be inserted
	 */
	public void putAll(Int2ObjectMap<V> m);

	/**
	 * Type Specific method to reduce boxing/unboxing of values
	 * @param key element that is searched for
	 * @return if the key is present
	 */
	public boolean containsKey(int key);

	/**
	 * @see Map#containsKey(Object)
	 * @param key that is searched for.
	 * @return true if found
	 * @note in some implementations key does not have to be Integer but just have to support equals with Integer.
	 */
	@Override
	public default boolean containsKey(Object key) {
		return key instanceof Integer && containsKey(((Integer)key).intValue());
	}

	/**
	 * Type Specific remove function to reduce boxing/unboxing
	 * @param key the element that should be removed
	 * @return the value that was removed or default return value
	 */
	public V remove(int key);

	/**
	 * @see Map#remove(Object)
	 * @param key the element that should be removed
	 * @return the value that was removed or default return value
	 * @note in some implementations key does not have to be Integer but just have to support equals with Integer.
	 */
	@Override
	public default V remove(Object key) {
		return key instanceof Integer ? remove(((Integer)key).intValue()) : getDefaultReturnValue();
	}

	/**
	 * Type Specific remove function to reduce boxing/unboxing
 	 * @param key the element that should be removed
 	 * @param value the expected value that should be found
 	 * @return true if the key and value was found and removed
	 * @see Map#remove(Object, Object)
	 */
	public boolean remove(int key, V value);

	/**
 	 * @see Map#remove(Object, Object)
 	 * @param key the element that should be removed
 	 * @param value the expected value that should be found
 	 * @return true if the key and value was found and removed
 	 */
	@Override
	public default boolean remove(Object key, Object value) {
		return key instanceof Integer && remove(((Integer)key).intValue(), (V)value);
	}

	/**
	 * Type-Specific Remove function with a default return value if wanted.
	 * @see Map#remove(Object, Object)
 	 * @param key the element that should be removed
 	 * @param defaultValue the value that should be returned if the entry doesn't exist
	 * @return the value that was removed or default value
	 */
	public V removeOrDefault(int key, V defaultValue);
	/**
	 * A Type Specific replace method to replace an existing value
	 * @param key the element that should be searched for
	 * @param oldValue the expected value to be replaced
	 * @param newValue the value to replace the oldValue with.
	 * @return true if the value got replaced
	 * @note this fails if the value is not present even if it matches the oldValue
	 */
	public boolean replace(int key, V oldValue, V newValue);
	/**
	 * A Type Specific replace method to reduce boxing/unboxing replace an existing value
	 * @param key the element that should be searched for
	 * @param value the value to replace with.
	 * @return the present value or default return value
	 * @note this fails if the value is not present
	 */
	public V replace(int key, V value);

	/**
	 * Type-Specific bulk replace method. Could be seen as putAllIfPresent
	 * @param m elements that should be replaced.
	 */
	public void replaceObjects(Int2ObjectMap<V> m);
	/**
	 * A Type Specific mass replace method to reduce boxing/unboxing
	 * @param mappingFunction operation to replace all values
	 */
	public void replaceObjects(IntObjectUnaryOperator<V> mappingFunction);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value
	 * @return the result of the computation
	 */
	public V compute(int key, IntObjectUnaryOperator<V> mappingFunction);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value if not present
	 * @return the result of the computed value or present value
	 */
	public V computeIfAbsent(int key, IntFunction<V> mappingFunction);

	/**
	 * A Supplier based computeIfAbsent function to fill the most used usecase of this function
	 * @param key the key that should be computed
	 * @param valueProvider the value if not present
	 * @return the result of the computed value or present value
	 */
	public V supplyIfAbsent(int key, ObjectSupplier<V> valueProvider);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value if present
	 * @return the result of the default return value or present value
	 * @note if not present then compute is not executed
	 */
	public V computeIfPresent(int key, IntObjectUnaryOperator<V> mappingFunction);
	/**
	 * A Type Specific merge method to reduce boxing/unboxing
	 * @param key the key that should be be searched for
	 * @param value the value that should be merged with
	 * @param mappingFunction the operator that should generate the new Value
	 * @return the result of the merge
	 * @note if the result matches the default return value then the key is removed from the map
	 */
	public V merge(int key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction);
	/**
	 * A Bulk method for merging Maps.
	 * @param m the entries that should be bulk added
	 * @param mappingFunction the operator that should generate the new Value
	 * @note if the result matches the default return value then the key is removed from the map
	 */
	public void mergeAll(Int2ObjectMap<V> m, ObjectObjectUnaryOperator<V, V> mappingFunction);

	@Override
	@Deprecated
	public default boolean replace(Integer key, V oldValue, V newValue) {
		return replace(key.intValue(), oldValue, newValue);
	}

	@Override
	@Deprecated
	public default V replace(Integer key, V value) {
		return replace(key.intValue(), value);
	}

	@Override
	public default V apply(int key) {
		return get(key);
	}
	/**
	 * A Type Specific get method to reduce boxing/unboxing
	 * @param key the key that is searched for
	 * @return the searched value or default return value
	 */
	public V get(int key);

	/**
	 * A Type Specific getOrDefault method to reduce boxing/unboxing
	 * @param key the key that is searched for
	 * @param defaultValue the value that should be returned if the key is not present
	 * @return the searched value or defaultValue value
	 */
	public V getOrDefault(int key, V defaultValue);

	@Override
	@Deprecated
	public default V get(Object key) {
		return key instanceof Integer ? get(((Integer)key).intValue()) : getDefaultReturnValue();
	}

	@Override
	@Deprecated
	public default V getOrDefault(Object key, V defaultValue) {
		V value = key instanceof Integer ? get(((Integer)key).intValue()) : getDefaultReturnValue();
		return !Objects.equals(value, getDefaultReturnValue()) || containsKey(key) ? value : defaultValue;
	}

	@Override
	@Deprecated
	public default void replaceAll(BiFunction<? super Integer, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		replaceObjects(mappingFunction instanceof IntObjectUnaryOperator ? (IntObjectUnaryOperator<V>)mappingFunction : (K, V) -> mappingFunction.apply(Integer.valueOf(K), V));
	}

	@Override
	@Deprecated
	public default V compute(Integer key, BiFunction<? super Integer, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return compute(key.intValue(), mappingFunction instanceof IntObjectUnaryOperator ? (IntObjectUnaryOperator<V>)mappingFunction : (K, V) -> mappingFunction.apply(Integer.valueOf(K), V));
	}

	@Override
	@Deprecated
	public default V computeIfAbsent(Integer key, Function<? super Integer, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return computeIfAbsent(key.intValue(), mappingFunction instanceof IntFunction ? (IntFunction<V>)mappingFunction : K -> mappingFunction.apply(Integer.valueOf(K)));
	}

	@Override
	@Deprecated
	public default V computeIfPresent(Integer key, BiFunction<? super Integer, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return computeIfPresent(key.intValue(), mappingFunction instanceof IntObjectUnaryOperator ? (IntObjectUnaryOperator<V>)mappingFunction : (K, V) -> mappingFunction.apply(Integer.valueOf(K), V));
	}

	@Override
	@Deprecated
	public default V merge(Integer key, V value, BiFunction<? super V, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		Objects.requireNonNull(value);
		return merge(key.intValue(), value, mappingFunction instanceof ObjectObjectUnaryOperator ? (ObjectObjectUnaryOperator<V, V>)mappingFunction : (K, V) -> mappingFunction.apply(K, V));
	}

	/**
	 * Type Specific forEach method to reduce boxing/unboxing
	 * @param action processor of the values that are iterator over
	 */
	public void forEach(IntObjectConsumer<V> action);

	@Override
	@Deprecated
	public default void forEach(BiConsumer<? super Integer, ? super V> action) {
		Objects.requireNonNull(action);
		forEach(action instanceof IntObjectConsumer ? (IntObjectConsumer<V>)action : (K, V) -> action.accept(Integer.valueOf(K), V));
	}

	@Override
	public IntSet keySet();
	@Override
	public ObjectCollection<V> values();
	@Override
	@Deprecated
	public ObjectSet<Map.Entry<Integer, V>> entrySet();
	/**
	 * Type Sensitive EntrySet to reduce boxing/unboxing and optionally Temp Object Allocation.
	 * @return a EntrySet of the collection
	 */
	public ObjectSet<Entry<V>> int2ObjectEntrySet();

	@Override
	@Deprecated
	public default V put(Integer key, V value) {
		return put(key.intValue(), value);
	}

	@Override
	@Deprecated
	public default V putIfAbsent(Integer key, V value) {
		return put(key.intValue(), value);
	}
	/**
	 * Fast Entry set that allows for a faster Entry Iterator by recycling the Entry Object and just exchanging 1 internal value
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public interface FastEntrySet<V> extends ObjectSet<Entry<V>>
	{
		/**
		 * Fast iterator that recycles the given Entry object to improve speed and reduce object allocation
		 * @return a Recycling ObjectIterator of the given set
		 */
		public ObjectIterator<Entry<V>> fastIterator();
		/**
		 * Fast for each that recycles the given Entry object to improve speed and reduce object allocation
		 * @param action the action that should be applied to each given entry
		 */
		public default void fastForEach(Consumer<? super Entry<V>> action) {
			forEach(action);
		}
	}

	/**
	 * Type Specific Map Entry that reduces boxing/unboxing
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public interface Entry<V> extends Map.Entry<Integer, V>
	{
		/**
		 * Type Specific getKey method that reduces boxing/unboxing
		 * @return the key of a given Entry
		 */
		public int getIntKey();
		public default Integer getKey() { return Integer.valueOf(getIntKey()); }

	}

	/**
	 * Helper class that reduces the method spam of the Map Class.
	 */
	public static final class MapBuilder
	{
		static final MapBuilder INSTANCE = new MapBuilder();

		/**
		 * Starts a Map Builder that allows you to create maps as Constants a lot easier
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder
		 */
		public <V> BuilderCache<V> start() {
			return new BuilderCache<V>();
		}

		/**
		 * Starts a Map Builder that allows you to create maps as Constants a lot easier
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param size the expected minimum size of Elements in the Map, default is 16
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder
		 */
		public <V> BuilderCache<V> start(int size) {
			return new BuilderCache<V>(size);
		}

		/**
		 * Starts a Map builder and puts in the Key and Value into it
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param key the key that should be added
		 * @param value the value that should be added
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder with the key and value stored in it.
		 */
		public <V> BuilderCache<V> put(int key, V value) {
			return new BuilderCache<V>().put(key, value);
		}

		/**
		 * Starts a Map builder and puts in the Key and Value into it
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param key the key that should be added
		 * @param value the value that should be added
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder with the key and value stored in it.
		 */
		public <V> BuilderCache<V> put(Integer key, V value) {
			return new BuilderCache<V>().put(key, value);
		}

		/**
		* Helper function to unify code
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap() {
			return new Int2ObjectArrayMap<>();
		}

		/**
		* Helper function to unify code
		* @param size the minimum capacity of the Map
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap with a mimimum capacity
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap(int size) {
			return new Int2ObjectArrayMap<>(size);
		}

		/**
		* Helper function to unify code
		* @param keys the keys that should be inserted
		* @param values the values that should be inserted
		* @param <V> the keyType of elements maintained by this Collection
		* @throws IllegalStateException if the keys and values do not match in length
		* @return a OpenHashMap thats contains the injected values
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap(int[] keys, V[] values) {
			return new Int2ObjectArrayMap<>(keys, values);
		}

		/**
		* Helper function to unify code
		* @param keys the keys that should be inserted
		* @param values the values that should be inserted
		* @param <V> the keyType of elements maintained by this Collection
		* @throws IllegalStateException if the keys and values do not match in length
		* @return a OpenHashMap thats contains the injected values
		* @note the keys and values will be unboxed
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap(Integer[] keys, V[] values) {
			return new Int2ObjectArrayMap<>(keys, values);
		}

		/**
		* Helper function to unify code
		* @param map that should be cloned
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap thats copies the contents of the provided map
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap(Int2ObjectMap<V> map) {
			return new Int2ObjectArrayMap<>(map);
		}

		/**
		* Helper function to unify code
		* @param map that should be cloned
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap thats copies the contents of the provided map
		* @note the map will be unboxed
		*/
		public <V> Int2ObjectArrayMap<V> arrayMap(Map<? extends Integer, ? extends V> map) {
			return new Int2ObjectArrayMap<>(map);
		}

	}

	/**
	 * Builder Cache for allowing to buildMaps
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public static class BuilderCache<V>
	{
		int[] keys;
		V[] values;
		int size;

		/**
		 * Default Constructor
		 */
		public BuilderCache() {
			this(HashUtil.DEFAULT_MIN_CAPACITY);
		}

		/**
		 * Constructor providing a Minimum Capcity
		 * @param initialSize the requested start capacity
		 */
		public BuilderCache(int initialSize) {
			if(initialSize < 0) throw new IllegalStateException("Minimum Capacity is negative. This is not allowed");
			keys = new int[initialSize];
			values = (V[])new Object[initialSize];
		}

		private void ensureSize(int newSize) {
			if(keys.length >= newSize) return;
			newSize = (int)Math.max(Math.min((long)keys.length + (keys.length >> 1), SanityChecks.MAX_ARRAY_SIZE), newSize);
			keys = Arrays.copyOf(keys, newSize);
			values = Arrays.copyOf(values, newSize);
		}

		/**
		 * Helper function to add a Entry into the Map
		 * @param key the key that should be added
		 * @param value the value that should be added
		 * @return self
		 */
		public BuilderCache<V> put(int key, V value) {
			ensureSize(size+1);
			keys[size] = key;
			values[size] = value;
			size++;
			return this;
		}

		/**
		 * Helper function to add a Entry into the Map
		 * @param key the key that should be added
		 * @param value the value that should be added
		 * @return self
		 */
		public BuilderCache<V> put(Integer key, V value) {
			return put(key.intValue(), value);
		}

		/**
		 * Helper function to add a Entry into the Map
		 * @param entry the Entry that should be added
		 * @return self
		 */
		public BuilderCache<V> put(Entry<V> entry) {
			return put(entry.getIntKey(), entry.getValue());
		}

		/**
		 * Helper function to add a Map to the Map
		 * @param map that should be added
		 * @return self
		 */
		public BuilderCache<V> putAll(Int2ObjectMap<V> map) {
			return putAll(map.int2ObjectEntrySet());
		}

		/**
		 * Helper function to add a Map to the Map
		 * @param map that should be added
		 * @return self
		 */
		public BuilderCache<V> putAll(Map<? extends Integer, ? extends V> map) {
			for(Map.Entry<? extends Integer, ? extends V> entry : map.entrySet())
				put(entry.getKey(), entry.getValue());
			return this;
		}

		/**
		 * Helper function to add a Collection of Entries to the Map
		 * @param c that should be added
		 * @return self
		 */
		public BuilderCache<V> putAll(ObjectIterable<Entry<V>> c) {
			if(c instanceof Collection)
				ensureSize(size+((Collection<Entry<V>>)c).size());

			for(Entry<V> entry : c)
				put(entry);

			return this;
		}

		/**
		 * Builds the Keys and Values into a Array Map
		 * @return a Int2ObjectArrayMap
		 */
		public Int2ObjectArrayMap<V> arrayMap() {
			return new Int2ObjectArrayMap<>(keys, values, size);
		}

	}
}
