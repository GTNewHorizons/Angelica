package speiger.src.collections.objects.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Consumer;

import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.utils.ObjectArrays;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;
import speiger.src.collections.utils.HashUtil;
import speiger.src.collections.utils.ITrimmable;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Helper class for Collections
 */
public class ObjectCollections
{
	/**
	 * Empty Collection Reference
	 */
	public static final ObjectCollection<?> EMPTY = new EmptyCollection<>();
	
	/**
	 * Returns a Immutable EmptyCollection instance that is automatically casted.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return an empty collection
	 */
	public static <T> ObjectCollection<T> empty() {
		return (ObjectCollection<T>)EMPTY;
	}
	
	/**
	 * Returns a Immutable Collection instance based on the instance given.
	 * @param c that should be made immutable/unmodifiable
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a unmodifiable collection wrapper. If the Collection already a unmodifiable wrapper then it just returns itself.
	 */
	public static <T> ObjectCollection<T> unmodifiable(ObjectCollection<T> c) {
		return c instanceof UnmodifiableCollection ? c : new UnmodifiableCollection<>(c);
	}
	
	/**
	 * Returns a synchronized Collection instance based on the instance given.
	 * @param c that should be synchronized
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a synchronized collection wrapper. If the Collection already a synchronized wrapper then it just returns itself.
	 */
	public static <T> ObjectCollection<T> synchronize(ObjectCollection<T> c) {
		return c instanceof SynchronizedCollection ? c : new SynchronizedCollection<>(c);
	}
	
	/**
	 * Returns a synchronized Collection instance based on the instance given.
	 * @param c that should be synchronized
	 * @param mutex is the controller of the synchronization block.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a synchronized collection wrapper. If the Collection already a synchronized wrapper then it just returns itself.
	 */
	public static <T> ObjectCollection<T> synchronize(ObjectCollection<T> c, Object mutex) {
		return c instanceof SynchronizedCollection ? c : new SynchronizedCollection<>(c, mutex);
	}
	
	/**
	 * Creates a Singleton Collection of a given element
	 * @param element the element that should be converted into a singleton collection
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a singletoncollection of the given element
	 */
	public static <T> ObjectCollection<T> singleton(T element) {
		return new SingletonCollection<>(element);
	}
	
	protected static <T> CollectionWrapper<T> wrapper() {
		return new CollectionWrapper<>();
	}
	
	protected static <T> CollectionWrapper<T> wrapper(int size) {
		return new CollectionWrapper<>(size);
	}
	
	protected static <T> DistinctCollectionWrapper<T> distinctWrapper() {
		return new DistinctCollectionWrapper<>();
	}
	
	protected static <T> DistinctCollectionWrapper<T> distinctWrapper(int size) {
		return new DistinctCollectionWrapper<>(size);
	}
	
	protected static class CollectionWrapper<T> extends AbstractObjectCollection<T> implements ITrimmable {
		T[] elements;
		int size = 0;
		
		public CollectionWrapper() {
			this(10);
		}
		
		public CollectionWrapper(int size) {
			if(size < 0) throw new IllegalStateException("Size has to be 0 or greater");
			elements = (T[])new Object[size];
		}
		
		@Override
		public boolean add(T o) {
			if(size >= elements.length) elements = Arrays.copyOf(elements, (int)Math.min((long)elements.length + (elements.length >> 1), SanityChecks.MAX_ARRAY_SIZE));
			elements[size++] = o;
			return true;
		}
		
		public T get(int index) {
			if(index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
			return elements[index];
		}
		
		@Override
		public boolean remove(Object e) {
			for(int i = 0;i<size;i++) {
				if(Objects.equals(elements[i], e)) {
					removeIndex(i);
					return true;
				}
			}
			return false;
		}
		
		private void removeIndex(int index) {
			if(index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
			size--;
			if(index != size) System.arraycopy(elements, index+1, elements, index, size - index);
		}
		
		@Override
		public ObjectIterator<T> iterator() {
			return new ObjectIterator<T>() {
				int index = 0;
				int lastReturned = -1;
				
				@Override
				public boolean hasNext() {
					return index < size;
				}
				
				@Override
				public T next() {
					int i = index++;
					return elements[(lastReturned = i)];
				}
				
				@Override
				public void remove() {
					if(lastReturned == -1) throw new IllegalStateException();
					removeIndex(lastReturned);
					index = lastReturned;
					lastReturned = -1;
				}
			};
		}
		
		@Override
		public int size() {
			return size;
		}
		
		@Override
		public void clear() {
			for(int i = 0;i<size;elements[i] = null,i++);
			size = 0;
		}
		
		public void sort(Comparator<? super T> c) {
			if(c != null) ObjectArrays.stableSort(elements, size, c);
			else ObjectArrays.stableSort(elements, size);
		}

		public void unstableSort(Comparator<? super T> c) {
			if(c != null) ObjectArrays.unstableSort(elements, size, c);
			else ObjectArrays.unstableSort(elements, size);
		}
		
		@Override
		public void forEach(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++)
				action.accept(elements[i]);
		}
		
		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, T> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++)
				action.accept(input, elements[i]);		
		}
		
		@Override
		public boolean trim(int size) {
			if(size > size() || size() == elements.length) return false;
			int value = Math.max(size, size());
			elements = value == 0 ? (T[])ObjectArrays.EMPTY_ARRAY : Arrays.copyOf(elements, value);
			return true;
		}
		
		@Override
		public void clearAndTrim(int size) {
			if(elements.length <= size) {
				clear();
				return;
			}
			elements = size == 0 ? (T[])ObjectArrays.EMPTY_ARRAY : (T[])new Object[size];
			this.size = size;
		}
		
		@Override
		public Object[] toArray() {
			Object[] obj = new Object[size];
			for(int i = 0;i<size;i++)
				obj[i] = elements[i];
			return obj;
		}
		
		@Override
		public <E> E[] toArray(E[] a) {
			if(a == null) a = (E[])new Object[size];
			else if(a.length < size) a = (E[])ObjectArrays.newArray(a.getClass().getComponentType(), size);
	        System.arraycopy(elements, 0, a, 0, size);
			if (a.length > size) a[size] = null;
			return a;
		}
		
	}
	
	protected static class DistinctCollectionWrapper<T> extends AbstractObjectCollection<T> {
		T[] keys;
		boolean containsNull;
		int minCapacity;
		int nullIndex;
		int maxFill;
		int mask;
		int size;
		
		public DistinctCollectionWrapper() {
			this(HashUtil.DEFAULT_MIN_CAPACITY);
		}
		
		public DistinctCollectionWrapper(int size) {
			if(minCapacity < 0)	throw new IllegalStateException("Minimum Capacity is negative. This is not allowed");
			minCapacity = nullIndex = HashUtil.arraySize(minCapacity, HashUtil.DEFAULT_LOAD_FACTOR);
			mask = nullIndex - 1;
			maxFill = Math.min((int)Math.ceil(nullIndex * HashUtil.DEFAULT_LOAD_FACTOR), nullIndex - 1);
			keys = (T[])new Object[nullIndex + 1];
		}

		@Override
		public boolean add(T o) {
			if(o == null) {
				if(containsNull) return false;
				containsNull = true;
			}
			else {
				int pos = HashUtil.mix(Objects.hashCode(o)) & mask;
				T current = keys[pos];
				if(current != null) {
					if(Objects.equals(current, o)) return false;
					while((current = keys[pos = (++pos & mask)]) != null)
						if(Objects.equals(current, o)) return false;
				}
				keys[pos] = o;
			}
			if(size++ >= maxFill) rehash(HashUtil.arraySize(size+1, HashUtil.DEFAULT_LOAD_FACTOR));
			return true;
		}
		
		@Override
		public boolean contains(Object o) {
			if(o == null) return containsNull;
			int pos = HashUtil.mix(o.hashCode()) & mask;
			T current = keys[pos];
			if(current == null) return false;
			if(Objects.equals(o, current)) return true;
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == null) return false;
				else if(Objects.equals(o, current)) return true;
			}
		}

		@Override
		public boolean remove(Object o) {
			if(o == null) return (containsNull ? removeNullIndex() : false);
			int pos = HashUtil.mix(o.hashCode()) & mask;
			T current = keys[pos];
			if(current == null) return false;
			if(Objects.equals(o, current)) return removeIndex(pos);
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == null) return false;
				else if(Objects.equals(o, current)) return removeIndex(pos);
			}
		}

		protected boolean removeIndex(int pos) {
			if(pos == nullIndex) return containsNull ? removeNullIndex() : false;
			keys[pos] = null;
			size--;
			shiftKeys(pos);
			if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
			return true;
		}
		
		protected boolean removeNullIndex() {
			containsNull = false;
			keys[nullIndex] = null;
			size--;
			if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
			return true;
		}
		
		@Override
		public ObjectIterator<T> iterator() {
			return new SetIterator();
		}
		
		@Override
		public void forEach(Consumer<? super T> action) {
			if(size() <= 0) return;
			if(containsNull) action.accept(keys[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != null) action.accept(keys[i]);
			}
		}
		
		@Override
		public DistinctCollectionWrapper<T> copy() {
			DistinctCollectionWrapper<T> set = new DistinctCollectionWrapper<>(0);
			set.minCapacity = minCapacity;
			set.mask = mask;
			set.maxFill = maxFill;
			set.nullIndex = nullIndex;
			set.containsNull = containsNull;
			set.size = size;
			set.keys = Arrays.copyOf(keys, keys.length);
			return set;
		}
		
		protected void shiftKeys(int startPos) {
			int slot, last;
			T current;
			while(true) {
				startPos = ((last = startPos) + 1) & mask;
				while(true){
					if((current = keys[startPos]) == null) {
						keys[last] = null;
						return;
					}
					slot = HashUtil.mix(Objects.hashCode(current)) & mask;
					if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
					startPos = ++startPos & mask;
				}
				keys[last] = current;
			}
		}
		
		protected void rehash(int newSize) {
			int newMask = newSize - 1;
			T[] newKeys = (T[])new Object[newSize + 1];
			for(int i = nullIndex, pos = 0, j = (size - (containsNull ? 1 : 0));j-- != 0;) {
				while(true) {
					if(--i < 0) throw new ConcurrentModificationException("Set was modified during rehash");
					if(keys[i] != null) break;
				}
				if(newKeys[pos = HashUtil.mix(Objects.hashCode(keys[i])) & newMask] != null)
					while(newKeys[pos = (++pos & newMask)] != null);
				newKeys[pos] = keys[i];
			}
			nullIndex = newSize;
			mask = newMask;
			maxFill = Math.min((int)Math.ceil(nullIndex * HashUtil.DEFAULT_LOAD_FACTOR), nullIndex - 1);
			keys = newKeys;
		}
		
		@Override
		public void clear() {
			if(size == 0) return;
			size = 0;
			containsNull = false;
			Arrays.fill(keys, null);
		}
		
		@Override
		public int size() {
			return size;
		}
		
		private class SetIterator implements ObjectIterator<T> {
			int pos = nullIndex;
			int returnedPos = -1;
			int lastReturned = -1;
			int nextIndex = Integer.MIN_VALUE;
			boolean returnNull = containsNull;
			T[] wrapped = null;
			int wrappedIndex = 0;
			
			@Override
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
			
			@Override
			public T next() {
				if(!hasNext()) throw new NoSuchElementException();
				returnedPos = pos;
				if(nextIndex < 0){
					lastReturned = Integer.MAX_VALUE;
					T value = wrapped[nextIndex];
					nextIndex = Integer.MIN_VALUE;
					return value;
				}
				T value = keys[(lastReturned = nextIndex)];
				nextIndex = Integer.MIN_VALUE;
				return value;
			}
			
			@Override
			public void remove() {
				if(lastReturned == -1) throw new IllegalStateException();
				if(lastReturned == nullIndex) {
					containsNull = false;
					keys[nullIndex] = null;
				}
				else if(returnedPos >= 0) shiftKeys(returnedPos);
				else {
					DistinctCollectionWrapper.this.remove(wrapped[-returnedPos - 1]);
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
							return;
						}
						slot = HashUtil.mix(Objects.hashCode(current)) & mask;
						if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
						startPos = ++startPos & mask;
					}
					if(startPos < last) addWrapper(keys[startPos]);
					keys[last] = current;
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
	
	private static class SingletonCollection<T> extends AbstractObjectCollection<T>
	{
		T element;
		
		SingletonCollection(T element) {
			this.element = element;
		}
		
		@Override
		public boolean add(T o) { throw new UnsupportedOperationException(); }
		@Override
		public ObjectIterator<T> iterator()
		{
			return new ObjectIterator<T>() {
				boolean next = true;
				@Override
				public boolean hasNext() { return next; }
				@Override
				public T next() {
					if(!hasNext()) throw new NoSuchElementException();
					next = false;
					return element;
				}
			};
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;
			if (!(o instanceof Collection))
				return false;
			Collection<?> l = (Collection<?>)o;
			if(l.size() != size()) return false;
			Iterator<?> iter = l.iterator();
			if (iter.hasNext() && !Objects.equals(element, iter.next())) {
				return false;
			}
			return !iter.hasNext();
		}
		
		@Override
		public int hashCode() {
			return Objects.hashCode(element);
		}
		
		@Override
		public int size() { return 1; }
		
		@Override
		public SingletonCollection<T> copy() { return new SingletonCollection<>(element); }
	}
	
	/**
	 * Synchronized Collection Wrapper for the synchronizedCollection function
	 * @param <T> the keyType of elements maintained by this Collection
	 */
	public static class SynchronizedCollection<T> implements ObjectCollection<T> {
		ObjectCollection<T> c;
		protected Object mutex;
		
		SynchronizedCollection(ObjectCollection<T> c) {
			this.c = c;
			mutex = this;
		}
		
		SynchronizedCollection(ObjectCollection<T> c, Object mutex) {
			this.c = c;
			this.mutex = mutex;
		}
		
		@Override
		public boolean add(T o) { synchronized(mutex) { return c.add(o); } }
		@Override
		public boolean addAll(Collection<? extends T> c) { synchronized(mutex) { return this.c.addAll(c); } }
		@Override
		public boolean addAll(ObjectCollection<T> c) { synchronized(mutex) { return this.c.addAll(c); } }
		@Override
		public boolean addAll(T[] e, int offset, int length) { synchronized(mutex) { return c.addAll(e, offset, length); } }
		@Override
		public boolean contains(Object o) { synchronized(mutex) { return c.contains(o); } }
		@Override
		public boolean containsAll(Collection<?> c) { synchronized(mutex) { return this.c.containsAll(c); } }
		
		@Override
		public boolean containsAny(Collection<?> c) { synchronized(mutex) { return this.c.containsAny(c); } }
		
		@Override
		public boolean containsAll(ObjectCollection<T> c) { synchronized(mutex) { return this.c.containsAll(c); } }
		
		@Override
		public boolean containsAny(ObjectCollection<T> c) { synchronized(mutex) { return this.c.containsAny(c); } }
		
		@Override
		public int size() { synchronized(mutex) { return c.size(); } }
		
		@Override
		public boolean isEmpty() { synchronized(mutex) { return c.isEmpty(); } }
		
		@Override
		public ObjectIterator<T> iterator() {
			return c.iterator();
		}
		
		@Override
		public ObjectCollection<T> copy() { synchronized(mutex) { return c.copy(); } }
		
		@Override
		public boolean remove(Object o) { synchronized(mutex) { return c.remove(o); } }
		@Override
		public boolean removeAll(Collection<?> c) { synchronized(mutex) { return this.c.removeAll(c); } }
		@Override
		public boolean retainAll(Collection<?> c) { synchronized(mutex) { return this.c.retainAll(c); } }
		@Override
		public boolean removeAll(ObjectCollection<T> c) { synchronized(mutex) { return this.c.removeAll(c); } }
		@Override
		public boolean removeAll(ObjectCollection<T> c, Consumer<T> r) { synchronized(mutex) { return this.c.removeAll(c, r); } }
		@Override
		public boolean retainAll(ObjectCollection<T> c) { synchronized(mutex) { return this.c.retainAll(c); } }
		@Override
		public boolean retainAll(ObjectCollection<T> c, Consumer<T> r) { synchronized(mutex) { return this.c.retainAll(c, r); } }
		@Override
		public void clear() { synchronized(mutex) { c.clear(); } }
		@Override
		public Object[] toArray() { synchronized(mutex) { return c.toArray(); } }
		@Override
		public <E> E[] toArray(E[] a) { synchronized(mutex) { return c.toArray(a); } }
		@Override
		public void forEach(Consumer<? super T> action) { synchronized(mutex) { c.forEach(action); } }
		@Override
		public void forEachIndexed(IntObjectConsumer<T> action) { synchronized(mutex) { c.forEachIndexed(action); } }
		@Override
		public int hashCode() { synchronized(mutex) { return c.hashCode(); } }
		@Override
		public boolean equals(Object obj) {
			if(obj == this) return true;
			synchronized(mutex) { return c.equals(obj); } 
		}
		@Override
		public String toString() { synchronized(mutex) { return c.toString(); } }
		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, T> action) { synchronized(mutex) { c.forEach(input, action); } }
		@Override
		public boolean matchesAny(Predicate<T> filter) { synchronized(mutex) { return c.matchesAny(filter); } }
		@Override
		public boolean matchesNone(Predicate<T> filter) { synchronized(mutex) { return c.matchesNone(filter); } }
		@Override
		public boolean matchesAll(Predicate<T> filter) { synchronized(mutex) { return c.matchesAll(filter); } }
		public <E> E reduce(E identity, BiFunction<E, T, E> operator) { synchronized(mutex) { return c.reduce(identity, operator); } }
		@Override
		public T reduce(ObjectObjectUnaryOperator<T, T> operator) { synchronized(mutex) { return c.reduce(operator); } }
		@Override
		public T findFirst(Predicate<T> filter) { synchronized(mutex) { return c.findFirst(filter); } }
		@Override
		public int count(Predicate<T> filter) { synchronized(mutex) { return c.count(filter); } }
	}
	
	/**
	 * Unmodifyable Collection Wrapper for the unmodifyableCollection method
	 * @param <T> the keyType of elements maintained by this Collection
	 */
	public static class UnmodifiableCollection<T> implements ObjectCollection<T> {
		ObjectCollection<T> c;
		
		UnmodifiableCollection(ObjectCollection<T> c) {
			this.c = c;
		}
		
		@Override
		public boolean add(T o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(Collection<? extends T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(T[] e, int offset, int length) { throw new UnsupportedOperationException(); }
		@Override
		public boolean contains(Object o) { return c.contains(o); }
		@Override
		public boolean containsAll(ObjectCollection<T> c) { return this.c.containsAll(c); }
		@Override
		public boolean containsAny(ObjectCollection<T> c) { return this.c.containsAny(c); }
		@Override
		public boolean containsAny(Collection<?> c) { return this.c.containsAny(c); }
		@Override
		public boolean containsAll(Collection<?> c) { return this.c.containsAll(c); }
		@Override
		public int size() { return c.size(); }
		@Override
		public boolean isEmpty() { return c.isEmpty(); }
		@Override
		public ObjectIterator<T> iterator() { return ObjectIterators.unmodifiable(c.iterator()); }
		@Override
		public ObjectCollection<T> copy() { return c.copy(); }
		@Override
		@Deprecated
		public boolean remove(Object o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeIf(Predicate<? super T> filter) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(ObjectCollection<T> c, Consumer<T> r) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(ObjectCollection<T> c, Consumer<T> r) { throw new UnsupportedOperationException(); }
		@Override
		public void clear() { throw new UnsupportedOperationException(); }
		@Override
		public Object[] toArray() { return c.toArray(); }
		@Override
		public <E> E[] toArray(E[] a) { return c.toArray(a); }
		@Override
		public void forEach(Consumer<? super T> action) { c.forEach(action); }
		@Override
		public void forEachIndexed(IntObjectConsumer<T> action) { c.forEachIndexed(action); }
		@Override
		public int hashCode() { return c.hashCode(); }
		@Override
		public boolean equals(Object obj) { return obj == this || c.equals(obj); }
		@Override
		public String toString() { return c.toString(); }
		@Override
		public <E> void forEach(E input, ObjectObjectConsumer<E, T> action) { c.forEach(input, action); }
		@Override
		public boolean matchesAny(Predicate<T> filter) { return c.matchesAny(filter); }
		@Override
		public boolean matchesNone(Predicate<T> filter) { return c.matchesNone(filter); }
		@Override
		public boolean matchesAll(Predicate<T> filter) { return c.matchesAll(filter); }
		public <E> E reduce(E identity, BiFunction<E, T, E> operator) { return c.reduce(identity, operator); }
		@Override
		public T reduce(ObjectObjectUnaryOperator<T, T> operator) { return c.reduce(operator); }
		@Override
		public T findFirst(Predicate<T> filter) { return c.findFirst(filter); }
		@Override
		public int count(Predicate<T> filter) { return c.count(filter); }
	}
	
	/**
	 * Empty Collection implementation for the empty collection function
	 * @param <T> the keyType of elements maintained by this Collection
	 */
	public static class EmptyCollection<T> extends AbstractObjectCollection<T> {
		@Override
		public boolean add(T o) { throw new UnsupportedOperationException(); }
		
		@Override
		public boolean addAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(T[] e, int offset, int length) { throw new UnsupportedOperationException(); }
		
		@Override
		public boolean contains(Object o) { return false; }
		@Override
		public boolean containsAny(Collection<?> c) { return false; }
		@Override
		public boolean containsAll(Collection<?> c) { return c.isEmpty(); }
		@Override
		public int hashCode() { return 0; }
		
		@Override
		public boolean equals(Object o) {
			if(o == this) return true;
		  	if(!(o instanceof Collection)) return false;
		  	return ((Collection<?>)o).isEmpty();
		}
		
		@Override
		@Deprecated
		public boolean remove(Object o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeIf(Predicate<? super T> filter) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(ObjectCollection<T> c) { throw new UnsupportedOperationException(); }
		@Override
		public Object[] toArray() { return ObjectArrays.EMPTY_ARRAY; }
		@Override
		public <E> E[] toArray(E[] a) {
			if(a != null && a.length > 0)
				a[0] = null;
			return a;
		}
		@Override
		public ObjectIterator<T> iterator() { return ObjectIterators.empty(); }
		@Override
		public void clear() {}
		@Override
		public int size() { return 0; }
		@Override
		public EmptyCollection<T> copy() { return this; }
	}
}