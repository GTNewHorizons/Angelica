package speiger.src.collections.objects.maps.abstracts;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;
import speiger.src.collections.objects.functions.function.UnaryOperator;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.maps.interfaces.Object2ObjectMap;
import speiger.src.collections.objects.sets.AbstractObjectSet;
import speiger.src.collections.objects.sets.ObjectSet;
import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.functions.ObjectSupplier;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Base Implementation of a Type Specific Map to reduce boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
public abstract class AbstractObject2ObjectMap<T, V> extends AbstractMap<T, V> implements Object2ObjectMap<T, V>
{
	protected V defaultReturnValue = null;
	
	@Override
	public V getDefaultReturnValue() {
		return defaultReturnValue;
	}
	
	@Override
	public AbstractObject2ObjectMap<T, V> setDefaultReturnValue(V v) {
		defaultReturnValue = v;
		return this;
	}
	
	protected ObjectIterable<Object2ObjectMap.Entry<T, V>> getFastIterable(Object2ObjectMap<T, V> map) {
		return map.object2ObjectEntrySet();
	}
	
	protected ObjectIterator<Object2ObjectMap.Entry<T, V>> getFastIterator(Object2ObjectMap<T, V> map) {
		return map.object2ObjectEntrySet().iterator();
	}
	
	@Override
	public Object2ObjectMap<T, V> copy() { 
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void putAll(Object2ObjectMap<T, V> m) {
		for(ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(m);iter.hasNext();) {
			Object2ObjectMap.Entry<T, V> entry = iter.next();
			put(entry.getKey(), entry.getValue());
		}
	}
	
	@Override
	public void putAll(Map<? extends T, ? extends V> m)
	{
		if(m instanceof Object2ObjectMap) putAll((Object2ObjectMap<T, V>)m);
		else super.putAll(m);
	}
	
	@Override
	public void putAll(T[] keys, V[] values, int offset, int size) {
		SanityChecks.checkArrayCapacity(keys.length, offset, size);
		SanityChecks.checkArrayCapacity(values.length, offset, size);
		for(int i = 0;i<size;i++) put(keys[i], values[i]);
	}
	
	@Override
	public void putAllIfAbsent(Object2ObjectMap<T, V> m) {
		for(Object2ObjectMap.Entry<T, V> entry : getFastIterable(m))
			putIfAbsent(entry.getKey(), entry.getValue());
	}
	
	
	@Override
	public boolean containsKey(Object key) {
		for(ObjectIterator<T> iter = keySet().iterator();iter.hasNext();)
			if(Objects.equals(key, iter.next())) return true;
		return false;
	}
	
	@Override
	public boolean containsValue(Object value) {
		for(ObjectIterator<V> iter = values().iterator();iter.hasNext();)
			if(Objects.equals(value, iter.next())) return true;
		return false;
	}
	
	@Override
	public boolean replace(T key, V oldValue, V newValue) {
		V curValue = getObject(key);
		if (!Objects.equals(curValue, oldValue) || (Objects.equals(curValue, getDefaultReturnValue()) && !containsKey(key))) {
			return false;
		}
		put(key, newValue);
		return true;
	}

	@Override
	public V replace(T key, V value) {
		V curValue;
		if (!Objects.equals((curValue = getObject(key)), getDefaultReturnValue()) || containsKey(key)) {
			curValue = put(key, value);
		}
		return curValue;
	}
	
	@Override
	public void replaceObjects(Object2ObjectMap<T, V> m) {
		for(Object2ObjectMap.Entry<T, V> entry : getFastIterable(m))
			replace(entry.getKey(), entry.getValue());
	}
	
	@Override
	public void replaceObjects(ObjectObjectUnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(this);iter.hasNext();) {
			Object2ObjectMap.Entry<T, V> entry = iter.next();
			entry.setValue(mappingFunction.apply(entry.getKey(), entry.getValue()));
		}
	}

	@Override
	public V compute(T key, ObjectObjectUnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value = getObject(key);
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
	public V computeIfAbsent(T key, UnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value;
		if((value = getObject(key)) == getDefaultReturnValue() || !containsKey(key)) {
			V newValue = mappingFunction.apply(key);
			if(!Objects.equals(newValue, getDefaultReturnValue())) {
				put(key, newValue);
				return newValue;
			}
		}
		return value;
	}
	
	@Override
	public V supplyIfAbsent(T key, ObjectSupplier<V> valueProvider) {
		Objects.requireNonNull(valueProvider);
		V value;
		if((value = getObject(key)) == getDefaultReturnValue() || !containsKey(key)) {
			V newValue = valueProvider.get();
			if(!Objects.equals(newValue, getDefaultReturnValue())) {
				put(key, newValue);
				return newValue;
			}
		}
		return value;
	}
	
	@Override
	public V computeIfPresent(T key, ObjectObjectUnaryOperator<T, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V value;
		if(!Objects.equals((value = getObject(key)), getDefaultReturnValue()) || containsKey(key)) {
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
	public V merge(T key, V value, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		V oldValue = getObject(key);
		V newValue = Objects.equals(oldValue, getDefaultReturnValue()) ? value : mappingFunction.apply(oldValue, value);
		if(Objects.equals(newValue, getDefaultReturnValue())) remove(key);
		else put(key, newValue);
		return newValue;
	}
	
	@Override
	public void mergeAll(Object2ObjectMap<T, V> m, ObjectObjectUnaryOperator<V, V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		for(Object2ObjectMap.Entry<T, V> entry : getFastIterable(m)) {
			T key = entry.getKey();
			V oldValue = getObject(key);
			V newValue = Objects.equals(oldValue, getDefaultReturnValue()) ? entry.getValue() : mappingFunction.apply(oldValue, entry.getValue());
			if(Objects.equals(newValue, getDefaultReturnValue())) remove(key);
			else put(key, newValue);
		}
	}
	
	@Override
	public V get(Object key) {
		return getObject((T)key);
	}
	
	@Override
	public V getOrDefault(Object key, V defaultValue) {
		V value = get(key);
		return !Objects.equals(value, getDefaultReturnValue()) || containsKey(key) ? value : defaultValue;
	}
	
	
	@Override
	public V remove(Object key) {
		return rem((T)key);
	}
	
	@Override
	public void forEach(ObjectObjectConsumer<T, V> action) {
		Objects.requireNonNull(action);
		for(ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(this);iter.hasNext();) {
			Object2ObjectMap.Entry<T, V> entry = iter.next();
			action.accept(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public ObjectSet<T> keySet() {
		return new AbstractObjectSet<T>() {
			@Override
			public boolean remove(Object o) {
				if(AbstractObject2ObjectMap.this.containsKey(o)) {
					AbstractObject2ObjectMap.this.remove(o);
					return true;
				}
				return false;
			}
			
			@Override
			public boolean add(T o) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			public ObjectIterator<T> iterator() {
				return new ObjectIterator<T>() {
					ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(AbstractObject2ObjectMap.this);
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public T next() {
						return iter.next().getKey();
					}
					
					@Override
					public void remove() {
						iter.remove();
					}
				};
			}
			
			@Override
			public int size() {
				return AbstractObject2ObjectMap.this.size();
			}
			
			@Override
			public void clear() {
				AbstractObject2ObjectMap.this.clear();
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
				return AbstractObject2ObjectMap.this.size();
			}
			
			@Override
			public void clear() {
				AbstractObject2ObjectMap.this.clear();
			}
			
			@Override
			public ObjectIterator<V> iterator() {
				return new ObjectIterator<V>() {
					ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(AbstractObject2ObjectMap.this);
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
	public ObjectSet<Map.Entry<T, V>> entrySet() {
		return (ObjectSet)object2ObjectEntrySet();
	}

	@Override
	public boolean equals(Object o) {
		if(o == this) return true;
		if(o instanceof Map) {
			if(size() != ((Map<?, ?>)o).size()) return false;
			if(o instanceof Object2ObjectMap) return object2ObjectEntrySet().containsAll(((Object2ObjectMap<T, V>)o).object2ObjectEntrySet());
			return object2ObjectEntrySet().containsAll(((Map<?, ?>)o).entrySet());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		int hash = 0;
		ObjectIterator<Object2ObjectMap.Entry<T, V>> iter = getFastIterator(this);
		while(iter.hasNext()) hash += iter.next().hashCode();
		return hash;
	}
	
	/**
	 * A Simple Type Specific Entry class to reduce boxing/unboxing
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> the keyType of elements maintained by this Collection
	 */
	public static class BasicEntry<T, V> implements Object2ObjectMap.Entry<T, V> {
		protected T key;
		protected V value;
		
		/**
		 * A basic Empty constructor
		 */
		public BasicEntry() {}
		/**
		 * A Type Specific Constructor
		 * @param key the key of a entry
		 * @param value the value of a entry 
		 */
		public BasicEntry(T key, V value) {
			this.key = key;
			this.value = value;
		}
		
		/**
		 * A Helper method for fast replacing values
		 * @param key the key that should be replaced
		 * @param value the value that should be replaced
		 */
		public void set(T key, V value) {
			this.key = key;
			this.value = value;
		}
		
		@Override
		public T getKey() {
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
				if(obj instanceof Object2ObjectMap.Entry) {
					Object2ObjectMap.Entry<T, V> entry = (Object2ObjectMap.Entry<T, V>)obj;
					return Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>)obj;
				Object key = entry.getKey();
				Object value = entry.getValue();
				return Objects.equals(this.key, key) && Objects.equals(this.value, value);
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return Objects.hashCode(key) ^ Objects.hashCode(value);
		}
		
		@Override
		public String toString() {
			return Objects.toString(key) + "=" + Objects.toString(value);
		}
	}
}