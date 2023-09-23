package speiger.src.collections.ints.maps.abstracts;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

import speiger.src.collections.ints.collections.IntIterator;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.functions.function.IntObjectUnaryOperator;
import speiger.src.collections.ints.maps.interfaces.Int2ObjectMap;
import speiger.src.collections.ints.sets.AbstractIntSet;
import speiger.src.collections.ints.sets.IntSet;
import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.objects.sets.ObjectSet;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Base Implementation of a Type Specific Map to reduce boxing/unboxing
 * @param <V> the keyType of elements maintained by this Collection
 */
public abstract class AbstractInt2ObjectMap<V> extends AbstractMap<Integer, V> implements Int2ObjectMap<V>
{
	protected V defaultReturnValue = null;
	
	@Override
	public V getDefaultReturnValue() {
		return defaultReturnValue;
	}
	
	@Override
	public AbstractInt2ObjectMap<V> setDefaultReturnValue(V v) {
		defaultReturnValue = v;
		return this;
	}
	
	protected ObjectIterable<Int2ObjectMap.Entry<V>> getFastIterable(Int2ObjectMap<V> map) {
		return map.int2ObjectEntrySet();
	}
	
	protected ObjectIterator<Int2ObjectMap.Entry<V>> getFastIterator(Int2ObjectMap<V> map) {
		return map.int2ObjectEntrySet().iterator();
	}
	
	@Override
	public Int2ObjectMap<V> copy() { 
		throw new UnsupportedOperationException();
	}
	
	@Override
	@Deprecated
	public V put(Integer key, V value) {
		return put(key.intValue(), value);
	}
	
	@Override
	public void putAll(Int2ObjectMap<V> m) {
		for(ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(m);iter.hasNext();) {
			Int2ObjectMap.Entry<V> entry = iter.next();
			put(entry.getIntKey(), entry.getValue());
		}
	}
	
	@Override
	public void putAll(Map<? extends Integer, ? extends V> m)
	{
		if(m instanceof Int2ObjectMap) putAll((Int2ObjectMap<V>)m);
		else super.putAll(m);
	}
	
	@Override
	public void putAll(int[] keys, V[] values, int offset, int size) {
		SanityChecks.checkArrayCapacity(keys.length, offset, size);
		SanityChecks.checkArrayCapacity(values.length, offset, size);
		for(int i = 0;i<size;i++) put(keys[i], values[i]);
	}
	
	@Override
	public void putAll(Integer[] keys, V[] values, int offset, int size) {
		SanityChecks.checkArrayCapacity(keys.length, offset, size);
		SanityChecks.checkArrayCapacity(values.length, offset, size);
		for(int i = 0;i<size;i++) put(keys[i], values[i]);		
	}
	
	@Override
	public void putAllIfAbsent(Int2ObjectMap<V> m) {
		for(Int2ObjectMap.Entry<V> entry : getFastIterable(m))
			putIfAbsent(entry.getIntKey(), entry.getValue());
	}
	
	
	@Override
	public boolean containsKey(int key) {
		for(IntIterator iter = keySet().iterator();iter.hasNext();)
			if(iter.nextInt() == key) return true;
		return false;
	}
	
	@Override
	public boolean containsValue(Object value) {
		for(ObjectIterator<V> iter = values().iterator();iter.hasNext();)
			if(Objects.equals(value, iter.next())) return true;
		return false;
	}
	
	@Override
	public boolean replace(int key, V oldValue, V newValue) {
		V curValue = get(key);
		if (!Objects.equals(curValue, oldValue) || (Objects.equals(curValue, getDefaultReturnValue()) && !containsKey(key))) {
			return false;
		}
		put(key, newValue);
		return true;
	}

	@Override
	public V replace(int key, V value) {
		V curValue;
		if (!Objects.equals((curValue = get(key)), getDefaultReturnValue()) || containsKey(key)) {
			curValue = put(key, value);
		}
		return curValue;
	}
	
	@Override
	public void replaceObjects(Int2ObjectMap<V> m) {
		for(Int2ObjectMap.Entry<V> entry : getFastIterable(m))
			replace(entry.getIntKey(), entry.getValue());
	}
	
	@Override
	public void replaceObjects(IntObjectUnaryOperator<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(this);iter.hasNext();) {
			Int2ObjectMap.Entry<V> entry = iter.next();
			entry.setValue(mappingFunction.apply(entry.getIntKey(), entry.getValue()));
		}
	}

	@Override
	public V compute(int key, IntObjectUnaryOperator<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value = get(key);
		V newValue = mappingFunction.apply(key, value);
		if(Objects.equals(newValue, getDefaultReturnValue())) {
			if(!Objects.equals(value, getDefaultReturnValue()) || containsKey(key)) {
				remove(key);
				return getDefaultReturnValue();
			}
			return getDefaultReturnValue();
		}
		put(key, newValue);
		return newValue;
	}
	
	@Override
	public V computeIfAbsent(int key, IntFunction<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value;
		if((value = get(key)) == getDefaultReturnValue() || !containsKey(key)) {
			V newValue = mappingFunction.apply(key);
			if(!Objects.equals(newValue, getDefaultReturnValue())) {
				put(key, newValue);
				return newValue;
			}
		}
		return value;
	}
	
	@Override
	public V supplyIfAbsent(int key, ObjectSupplier<V> valueProvider) {
		Objects.requireNonNull(valueProvider);
		V value;
		if((value = get(key)) == getDefaultReturnValue() || !containsKey(key)) {
			V newValue = valueProvider.get();
			if(!Objects.equals(newValue, getDefaultReturnValue())) {
				put(key, newValue);
				return newValue;
			}
		}
		return value;
	}
	
	@Override
	public V computeIfPresent(int key, IntObjectUnaryOperator<V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value;
		if(!Objects.equals((value = get(key)), getDefaultReturnValue()) || containsKey(key)) {
			V newValue = mappingFunction.apply(key, value);
			if(!Objects.equals(newValue, getDefaultReturnValue())) {
				put(key, newValue);
				return newValue;
			}
			remove(key);
		}
		return getDefaultReturnValue();
	}

	@Override
	public V merge(int key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V oldValue = get(key);
		V newValue = Objects.equals(oldValue, getDefaultReturnValue()) ? value : mappingFunction.apply(oldValue, value);
		if(Objects.equals(newValue, getDefaultReturnValue())) remove(key);
		else put(key, newValue);
		return newValue;
	}
	
	@Override
	public void mergeAll(Int2ObjectMap<V> m, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(Int2ObjectMap.Entry<V> entry : getFastIterable(m)) {
			int key = entry.getIntKey();
			V oldValue = get(key);
			V newValue = Objects.equals(oldValue, getDefaultReturnValue()) ? entry.getValue() : mappingFunction.apply(oldValue, entry.getValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) remove(key);
			else put(key, newValue);
		}
	}
	
	@Override
	public V get(Object key) {
		return key instanceof Integer ? get(((Integer)key).intValue()) : getDefaultReturnValue();
	}
	
	@Override
	public V getOrDefault(Object key, V defaultValue) {
		return key instanceof Integer ? getOrDefault(((Integer)key).intValue(), defaultValue) : getDefaultReturnValue();
	}
	
	@Override
	public V getOrDefault(int key, V defaultValue) {
		V value = get(key);
		return !Objects.equals(value, getDefaultReturnValue()) || containsKey(key) ? value : defaultValue;
	}
	
	
	@Override
	public V remove(Object key) {
		return key instanceof Integer ? remove(((Integer)key).intValue()) : getDefaultReturnValue();
	}
	
	@Override
	public void forEach(IntObjectConsumer<V> action) {
		Objects.requireNonNull(action);
		for(ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(this);iter.hasNext();) {
			Int2ObjectMap.Entry<V> entry = iter.next();
			action.accept(entry.getIntKey(), entry.getValue());
		}
	}

	@Override
	public IntSet keySet() {
		return new AbstractIntSet() {
			@Override
			public boolean remove(int o) {
				return !Objects.equals(AbstractInt2ObjectMap.this.remove(o), getDefaultReturnValue());
			}
			
			@Override
			public boolean add(int o) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public IntIterator iterator() {
				return new IntIterator() {
					ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(AbstractInt2ObjectMap.this);
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public int nextInt() {
						return iter.next().getIntKey();
					}
					
					@Override
					public void remove() {
						iter.remove();
					}
				};
			}
			
			@Override
			public int size() {
				return AbstractInt2ObjectMap.this.size();
			}
			
			@Override
			public void clear() {
				AbstractInt2ObjectMap.this.clear();
			}
		};
	}

	@Override
	public ObjectCollection<V> values() {
		return new AbstractObjectCollection<V>() {
			@Override
			public boolean add(V o) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public int size() {
				return AbstractInt2ObjectMap.this.size();
			}
			
			@Override
			public void clear() {
				AbstractInt2ObjectMap.this.clear();
			}
			
			@Override
			public ObjectIterator<V> iterator() {
				return new ObjectIterator<V>() {
					ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(AbstractInt2ObjectMap.this);
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}
					
					@Override
					public V next() {
						return iter.next().getValue();
					}
					
					@Override
					public void remove() {
						iter.remove();
					}
				};
			}
		};
	}
	
	@Override
	@SuppressWarnings("rawtypes")
	public ObjectSet<Map.Entry<Integer, V>> entrySet() {
		return (ObjectSet)int2ObjectEntrySet();
	}

	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(o instanceof Map) {
			if(size() != ((Map<?, ?>)o).size()) return false;
			if(o instanceof Int2ObjectMap) return int2ObjectEntrySet().containsAll(((Int2ObjectMap<V>)o).int2ObjectEntrySet());
			return int2ObjectEntrySet().containsAll(((Map<?, ?>)o).entrySet());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		ObjectIterator<Int2ObjectMap.Entry<V>> iter = getFastIterator(this);
		while(iter.hasNext()) hash += iter.next().hashCode();
		return hash;
	}
	
	/**
	 * A Simple Type Specific Entry class to reduce boxing/unboxing
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public static class BasicEntry<V> implements Int2ObjectMap.Entry<V> {
		protected int key;
		protected V value;
		
		/**
		 * A basic Empty constructor
		 */
		public BasicEntry() {}
		/**
		 * A Boxed Constructor for supporting java variants
		 * @param key the key of a entry
		 * @param value the value of a entry
		 */
		public BasicEntry(Integer key, V value) {
			this.key = key.intValue();
			this.value = value;
		}
		
		/**
		 * A Type Specific Constructor
		 * @param key the key of a entry
		 * @param value the value of a entry 
		 */
		public BasicEntry(int key, V value) {
			this.key = key;
			this.value = value;
		}
		
		/**
		 * A Helper method for fast replacing values
		 * @param key the key that should be replaced
		 * @param value the value that should be replaced
		 */
		public void set(int key, V value) {
			this.key = key;
			this.value = value;
		}
		
		@Override
		public int getIntKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof Map.Entry) {
				if(obj instanceof Int2ObjectMap.Entry) {
					Int2ObjectMap.Entry<V> entry = (Int2ObjectMap.Entry<V>)obj;
					return key == entry.getIntKey() && Objects.equals(value, entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
				Object key = entry.getKey();
				Object value = entry.getValue();
				return key instanceof Integer && this.key == ((Integer)key).intValue() && Objects.equals(this.value, value);
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return Integer.hashCode(key) ^ Objects.hashCode(value);
		}
		
		@Override
		public String toString() {
			return Integer.toString(key) + "=" + Objects.toString(value);
		}
	}
}