package speiger.src.collections.objects.maps.interfaces;

import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;



import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;
import speiger.src.collections.objects.functions.function.UnaryOperator;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;

import speiger.src.collections.objects.maps.impl.hash.Object2ObjectOpenHashMap;

import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.objects.sets.ObjectSet;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.utils.HashUtil;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Type Specific Map that reduces memory overhead due to boxing/unboxing of Primitives
 * and some extra helper functions.
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface Object2ObjectMap<T, V> extends Map<T, V>, UnaryOperator<T, V>
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
	public Object2ObjectMap<T, V> setDefaultReturnValue(V v);

	/**
	 * A Function that does a shallow clone of the Map itself.
	 * This function is more optimized then a copy constructor since the Map does not have to be unsorted/resorted.
	 * It can be compared to Cloneable but with less exception risk
	 * @return a Shallow Copy of the Map
	 * @note Wrappers and view Maps will not support this feature
	 */
	public Object2ObjectMap<T, V> copy();

	/**
	 * Type Specific method to reduce boxing/unboxing of values
	 * @param key the key that should be inserted,
	 * @param value the value that should be inserted
	 * @return the last present value or default return value.
	 * @see Map#put(Object, Object)
	 */
	public V put(T key, V value);

	/**
	 * A Helper method that allows to put int a Object2ObjectMap.Entry into a map.
	 * @param entry then Entry that should be inserted.
	 * @return the last present value or default return value.
	 */
	public default V put(Entry<T, V> entry) {
		return put(entry.getKey(), entry.getValue());
	}

	/**
	 * Type Specific array method to bulk add elements into a map without creating a wrapper and increasing performances
	 * @param keys the keys that should be added
	 * @param values the values that should be added
	 * @see Map#putAll(Map)
	 * @throws IllegalStateException if the arrays are not the same size
	 */
	public default void putAll(T[] keys, V[] values) {
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
	public void putAll(T[] keys, V[] values, int offset, int size);

	/**
	 * Type Specific method to reduce boxing/unboxing of values
	 * @param key the key that should be inserted,
	 * @param value the value that should be inserted
	 * @return the last present value or default return value.
	 * @see Map#putIfAbsent(Object, Object)
	 */
	public V putIfAbsent(T key, V value);

	/**
	 * Type-Specific bulk put method put elements into the map if not present.
	 * @param m elements that should be added if not present.
	 */
	public void putAllIfAbsent(Object2ObjectMap<T, V> m);

	/**
	 * Type Specific function for the bull putting of values
	 * @param m the elements that should be inserted
	 */
	public void putAll(Object2ObjectMap<T, V> m);

	/**
	 * Type Specific remove function to reduce boxing/unboxing
	 * @param key the element that should be removed
	 * @return the value that was removed or default return value
	 */
	public V rem(T key);

	/**
	 * @see Map#remove(Object)
	 * @param key the element that should be removed
	 * @return the value that was removed or default return value
	 * @note in some implementations key does not have to be T but just have to support equals with T.
	 */
	@Override
	public default V remove(Object key) {
		return rem((T)key);
	}

	/**
	 * Type-Specific Remove function with a default return value if wanted.
	 * @see Map#remove(Object, Object)
 	 * @param key the element that should be removed
 	 * @param defaultValue the value that should be returned if the entry doesn't exist
	 * @return the value that was removed or default value
	 */
	public V remOrDefault(T key, V defaultValue);
	/**
	 * A Type Specific replace method to replace an existing value
	 * @param key the element that should be searched for
	 * @param oldValue the expected value to be replaced
	 * @param newValue the value to replace the oldValue with.
	 * @return true if the value got replaced
	 * @note this fails if the value is not present even if it matches the oldValue
	 */
	public boolean replace(T key, V oldValue, V newValue);
	/**
	 * A Type Specific replace method to reduce boxing/unboxing replace an existing value
	 * @param key the element that should be searched for
	 * @param value the value to replace with.
	 * @return the present value or default return value
	 * @note this fails if the value is not present
	 */
	public V replace(T key, V value);

	/**
	 * Type-Specific bulk replace method. Could be seen as putAllIfPresent
	 * @param m elements that should be replaced.
	 */
	public void replaceObjects(Object2ObjectMap<T, V> m);
	/**
	 * A Type Specific mass replace method to reduce boxing/unboxing
	 * @param mappingFunction operation to replace all values
	 */
	public void replaceObjects(ObjectObjectUnaryOperator<T, V> mappingFunction);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value
	 * @return the result of the computation
	 */
	public V compute(T key, ObjectObjectUnaryOperator<T, V> mappingFunction);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value if not present
	 * @return the result of the computed value or present value
	 */
	public V computeIfAbsent(T key, UnaryOperator<T, V> mappingFunction);

	/**
	 * A Supplier based computeIfAbsent function to fill the most used usecase of this function
	 * @param key the key that should be computed
	 * @param valueProvider the value if not present
	 * @return the result of the computed value or present value
	 */
	public V supplyIfAbsent(T key, ObjectSupplier<V> valueProvider);
	/**
	 * A Type Specific compute method to reduce boxing/unboxing
	 * @param key the key that should be computed
	 * @param mappingFunction the operator that should generate the value if present
	 * @return the result of the default return value or present value
	 * @note if not present then compute is not executed
	 */
	public V computeIfPresent(T key, ObjectObjectUnaryOperator<T, V> mappingFunction);
	/**
	 * A Type Specific merge method to reduce boxing/unboxing
	 * @param key the key that should be be searched for
	 * @param value the value that should be merged with
	 * @param mappingFunction the operator that should generate the new Value
	 * @return the result of the merge
	 * @note if the result matches the default return value then the key is removed from the map
	 */
	public V merge(T key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction);
	/**
	 * A Bulk method for merging Maps.
	 * @param m the entries that should be bulk added
	 * @param mappingFunction the operator that should generate the new Value
	 * @note if the result matches the default return value then the key is removed from the map
	 */
	public void mergeAll(Object2ObjectMap<T, V> m, ObjectObjectUnaryOperator<V, V> mappingFunction);

	@Override
	public default V apply(T key) {
		return getObject(key);
	}
	/**
	 * A Type Specific get method to reduce boxing/unboxing
	 * @param key the key that is searched for
	 * @return the searched value or default return value
	 */
	public V getObject(T key);

	@Override
	public default void replaceAll(BiFunction<? super T, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		replaceObjects(mappingFunction instanceof ObjectObjectUnaryOperator ? (ObjectObjectUnaryOperator<T, V>)mappingFunction : (K, V) -> mappingFunction.apply(K, V));
	}

	@Override
	public default V compute(T key, BiFunction<? super T, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return compute(key, mappingFunction instanceof ObjectObjectUnaryOperator ? (ObjectObjectUnaryOperator<T, V>)mappingFunction : (K, V) -> mappingFunction.apply(K, V));
	}

	@Override
	public default V computeIfAbsent(T key, Function<? super T, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return computeIfAbsent(key, mappingFunction instanceof UnaryOperator ? (UnaryOperator<T, V>)mappingFunction : K -> mappingFunction.apply(K));
	}

	@Override
	public default V computeIfPresent(T key, BiFunction<? super T, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		return computeIfPresent(key, mappingFunction instanceof ObjectObjectUnaryOperator ? (ObjectObjectUnaryOperator<T, V>)mappingFunction : (K, V) -> mappingFunction.apply(K, V));
	}

	@Override
	public default V merge(T key, V value, BiFunction<? super V, ? super V, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		Objects.requireNonNull(value);
		return merge(key, value, mappingFunction instanceof ObjectObjectUnaryOperator ? (ObjectObjectUnaryOperator<V, V>)mappingFunction : (K, V) -> mappingFunction.apply(K, V));
	}

	/**
	 * Type Specific forEach method to reduce boxing/unboxing
	 * @param action processor of the values that are iterator over
	 */
	public void forEach(ObjectObjectConsumer<T, V> action);

	@Override
	public default void forEach(BiConsumer<? super T, ? super V> action) {
		Objects.requireNonNull(action);
		forEach(action instanceof ObjectObjectConsumer ? (ObjectObjectConsumer<T, V>)action : (K, V) -> action.accept(K, V));
	}

	@Override
	public ObjectSet<T> keySet();
	@Override
	public ObjectCollection<V> values();
	@Override
	public ObjectSet<Map.Entry<T, V>> entrySet();
	/**
	 * Type Sensitive EntrySet to reduce boxing/unboxing and optionally Temp Object Allocation.
	 * @return a EntrySet of the collection
	 */
	public ObjectSet<Entry<T, V>> object2ObjectEntrySet();

	/**
	 * Fast Entry set that allows for a faster Entry Iterator by recycling the Entry Object and just exchanging 1 internal value
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public interface FastEntrySet<T, V> extends ObjectSet<Entry<T, V>>
	{
		/**
		 * Fast iterator that recycles the given Entry object to improve speed and reduce object allocation
		 * @return a Recycling ObjectIterator of the given set
		 */
		public ObjectIterator<Entry<T, V>> fastIterator();
		/**
		 * Fast for each that recycles the given Entry object to improve speed and reduce object allocation
		 * @param action the action that should be applied to each given entry
		 */
		public default void fastForEach(Consumer<? super Entry<T, V>> action) {
			forEach(action);
		}
	}

	/**
	 * Type Specific Map Entry that reduces boxing/unboxing
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public interface Entry<T, V> extends Map.Entry<T, V>
	{
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
		 * @param <T> the keyType of elements maintained by this Collection
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder
		 */
		public <T, V> BuilderCache<T, V> start() {
			return new BuilderCache<T, V>();
		}

		/**
		 * Starts a Map Builder that allows you to create maps as Constants a lot easier
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param size the expected minimum size of Elements in the Map, default is 16
		 * @param <T> the keyType of elements maintained by this Collection
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder
		 */
		public <T, V> BuilderCache<T, V> start(int size) {
			return new BuilderCache<T, V>(size);
		}

		/**
		 * Starts a Map builder and puts in the Key and Value into it
		 * Keys and Values are stored as Array and then inserted using the putAllMethod when the mapType is choosen
		 * @param key the key that should be added
		 * @param value the value that should be added
		 * @param <T> the keyType of elements maintained by this Collection
		 * @param <V> the keyType of elements maintained by this Collection
		 * @return a MapBuilder with the key and value stored in it.
		 */
		public <T, V> BuilderCache<T, V> put(T key, V value) {
			return new BuilderCache<T, V>().put(key, value);
		}

		/**
		* Helper function to unify code
		* @param <T> the keyType of elements maintained by this Collection
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap
		*/
		public <T, V> Object2ObjectOpenHashMap<T, V> map() {
			return new Object2ObjectOpenHashMap<>();
		}

		/**
		* Helper function to unify code
		* @param size the minimum capacity of the Map
		* @param <T> the keyType of elements maintained by this Collection
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap with a mimimum capacity
		*/
		public <T, V> Object2ObjectOpenHashMap<T, V> map(int size) {
			return new Object2ObjectOpenHashMap<>(size);
		}

		/**
		* Helper function to unify code
		* @param keys the keys that should be inserted
		* @param values the values that should be inserted
		* @param <T> the keyType of elements maintained by this Collection
		* @param <V> the keyType of elements maintained by this Collection
		* @throws IllegalStateException if the keys and values do not match in length
		* @return a OpenHashMap thats contains the injected values
		*/
		public <T, V> Object2ObjectOpenHashMap<T, V> map(T[] keys, V[] values) {
			return new Object2ObjectOpenHashMap<>(keys, values);
		}

		/**
		* Helper function to unify code
		* @param map that should be cloned
		* @param <T> the keyType of elements maintained by this Collection
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap thats copies the contents of the provided map
		*/
		public <T, V> Object2ObjectOpenHashMap<T, V> map(Object2ObjectMap<T, V> map) {
			return new Object2ObjectOpenHashMap<>(map);
		}

		/**
		* Helper function to unify code
		* @param map that should be cloned
		* @param <T> the keyType of elements maintained by this Collection
		* @param <V> the keyType of elements maintained by this Collection
		* @return a OpenHashMap thats copies the contents of the provided map
		* @note the map will be unboxed
		*/
		public <T, V> Object2ObjectOpenHashMap<T, V> map(Map<? extends T, ? extends V> map) {
			return new Object2ObjectOpenHashMap<>(map);
		}


	}

	/**
	 * Builder Cache for allowing to buildMaps
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public static class BuilderCache<T, V>
	{
		T[] keys;
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
			keys = (T[])new Object[initialSize];
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
		public BuilderCache<T, V> put(T key, V value) {
			ensureSize(size+1);
			keys[size] = key;
			values[size] = value;
			size++;
			return this;
		}

		/**
		 * Helper function to add a Entry into the Map
		 * @param entry the Entry that should be added
		 * @return self
		 */
		public BuilderCache<T, V> put(Entry<T, V> entry) {
			return put(entry.getKey(), entry.getValue());
		}

		/**
		 * Helper function to add a Map to the Map
		 * @param map that should be added
		 * @return self
		 */
		public BuilderCache<T, V> putAll(Object2ObjectMap<T, V> map) {
			return putAll(map.object2ObjectEntrySet());
		}

		/**
		 * Helper function to add a Map to the Map
		 * @param map that should be added
		 * @return self
		 */
		public BuilderCache<T, V> putAll(Map<? extends T, ? extends V> map) {
			for(Map.Entry<? extends T, ? extends V> entry : map.entrySet())
				put(entry.getKey(), entry.getValue());
			return this;
		}

		/**
		 * Helper function to add a Collection of Entries to the Map
		 * @param c that should be added
		 * @return self
		 */
		public BuilderCache<T, V> putAll(ObjectIterable<Entry<T, V>> c) {
			if(c instanceof Collection)
				ensureSize(size+((Collection<Entry<T, V>>)c).size());

			for(Entry<T, V> entry : c)
				put(entry);

			return this;
		}

		private <E extends Object2ObjectMap<T, V>> E putElements(E e){
			e.putAll(keys, values, 0, size);
			return e;
		}

		/**
		 * Builds the Keys and Values into a Hash Map
		 * @return a Object2ObjectOpenHashMap
		 */
		public Object2ObjectOpenHashMap<T, V> map() {
			return putElements(new Object2ObjectOpenHashMap<>(size));
		}

	}
}
