package speiger.src.collections.ints.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.IntPredicate;
import java.util.function.Consumer;

import speiger.src.collections.ints.collections.AbstractIntCollection;
import speiger.src.collections.ints.collections.IntCollection;
import speiger.src.collections.ints.collections.IntIterator;
import speiger.src.collections.ints.functions.IntComparator;
import speiger.src.collections.objects.utils.ObjectArrays;
import speiger.src.collections.ints.functions.IntConsumer;
import speiger.src.collections.ints.utils.IntArrays;
import speiger.src.collections.ints.functions.function.IntIntUnaryOperator;
import speiger.src.collections.ints.functions.consumer.IntIntConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectIntConsumer;
import speiger.src.collections.utils.HashUtil;
import speiger.src.collections.utils.ITrimmable;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Helper class for Collections
 */
public class IntCollections
{
	/**
	 * Empty Collection Reference
	 */
	public static final IntCollection EMPTY = new EmptyCollection();
	
	/**
	 * Returns a Immutable EmptyCollection instance that is automatically casted.
	 * @return an empty collection
	 */
	public static IntCollection empty() {
		return EMPTY;
	}
	
	/**
	 * Returns a Immutable Collection instance based on the instance given.
	 * @param c that should be made immutable/unmodifiable
	 * @return a unmodifiable collection wrapper. If the Collection already a unmodifiable wrapper then it just returns itself.
	 */
	public static IntCollection unmodifiable(IntCollection c) {
		return c instanceof UnmodifiableCollection ? c : new UnmodifiableCollection(c);
	}
	
	/**
	 * Returns a synchronized Collection instance based on the instance given.
	 * @param c that should be synchronized
	 * @return a synchronized collection wrapper. If the Collection already a synchronized wrapper then it just returns itself.
	 */
	public static IntCollection synchronize(IntCollection c) {
		return c instanceof SynchronizedCollection ? c : new SynchronizedCollection(c);
	}
	
	/**
	 * Returns a synchronized Collection instance based on the instance given.
	 * @param c that should be synchronized
	 * @param mutex is the controller of the synchronization block.
	 * @return a synchronized collection wrapper. If the Collection already a synchronized wrapper then it just returns itself.
	 */
	public static IntCollection synchronize(IntCollection c, Object mutex) {
		return c instanceof SynchronizedCollection ? c : new SynchronizedCollection(c, mutex);
	}
	
	/**
	 * Creates a Singleton Collection of a given element
	 * @param element the element that should be converted into a singleton collection
	 * @return a singletoncollection of the given element
	 */
	public static IntCollection singleton(int element) {
		return new SingletonCollection(element);
	}
	
	protected static CollectionWrapper wrapper() {
		return new CollectionWrapper();
	}
	
	protected static CollectionWrapper wrapper(int size) {
		return new CollectionWrapper(size);
	}
	
	protected static DistinctCollectionWrapper distinctWrapper() {
		return new DistinctCollectionWrapper();
	}
	
	protected static DistinctCollectionWrapper distinctWrapper(int size) {
		return new DistinctCollectionWrapper(size);
	}
	
	protected static class CollectionWrapper extends AbstractIntCollection implements ITrimmable {
		int[] elements;
		int size = 0;
		
		public CollectionWrapper() {
			this(10);
		}
		
		public CollectionWrapper(int size) {
			if(size < 0) throw new IllegalStateException("Size has to be 0 or greater");
			elements = new int[size];
		}
		
		@Override
		public boolean add(int o) {
			if(size >= elements.length) elements = Arrays.copyOf(elements, (int)Math.min((long)elements.length + (elements.length >> 1), SanityChecks.MAX_ARRAY_SIZE));
			elements[size++] = o;
			return true;
		}
		
		public int getInt(int index) {
			if(index < 0 || index >= size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
			return elements[index];
		}
		
		@Override
		public boolean remInt(int e) {
			for(int i = 0;i<size;i++) {
				if(elements[i] == e) {
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
		public IntIterator iterator() {
			return new IntIterator() {
				int index = 0;
				int lastReturned = -1;
				
				@Override
				public boolean hasNext() {
					return index < size;
				}
				
				@Override
				public int nextInt() {
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
			size = 0;
		}
		
		public void sort(IntComparator c) {
			if(c != null) IntArrays.stableSort(elements, size, c);
			else IntArrays.stableSort(elements, size);
		}
		
		public void unstableSort(IntComparator c) {
			if(c != null) IntArrays.unstableSort(elements, size, c);
			else IntArrays.unstableSort(elements, size);		
		}
		
		@Override
		public void forEach(IntConsumer action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++)
				action.accept(elements[i]);
		}
		
		@Override
		public <E> void forEach(E input, ObjectIntConsumer<E> action) {
			Objects.requireNonNull(action);
			for(int i = 0;i<size;i++)
				action.accept(input, elements[i]);		
		}
		
		@Override
		public boolean trim(int size) {
			if(size > size() || size() == elements.length) return false;
			int value = Math.max(size, size());
			elements = value == 0 ? IntArrays.EMPTY_ARRAY : Arrays.copyOf(elements, value);
			return true;
		}
		
		@Override
		public void clearAndTrim(int size) {
			if(elements.length <= size) {
				clear();
				return;
			}
			elements = size == 0 ? IntArrays.EMPTY_ARRAY : new int[size];
			this.size = size;
		}
		
		@Override
		@Deprecated
		public Object[] toArray() {
			Object[] obj = new Object[size];
			for(int i = 0;i<size;i++)
				obj[i] = Integer.valueOf(elements[i]);
			return obj;
		}
		
		@Override
		@Deprecated
		public <E> E[] toArray(E[] a) {
			if(a == null) a = (E[])new Object[size];
			else if(a.length < size) a = (E[])ObjectArrays.newArray(a.getClass().getComponentType(), size);
			for(int i = 0;i<size;i++)
				a[i] = (E)Integer.valueOf(elements[i]);
			if (a.length > size) a[size] = null;
			return a;
		}
		
		@Override
		public int[] toIntArray(int[] a) {
			if(a.length < size) a = new int[size];
			System.arraycopy(elements, 0, a, 0, size);
			if (a.length > size) a[size] = 0;
			return a;
		}		
	}
	
	protected static class DistinctCollectionWrapper extends AbstractIntCollection {
		int[] keys;
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
			keys = new int[nullIndex + 1];
		}

		@Override
		public boolean add(int o) {
			if(o == 0) {
				if(containsNull) return false;
				containsNull = true;
			}
			else {
				int pos = HashUtil.mix(Integer.hashCode(o)) & mask;
				int current = keys[pos];
				if(current != 0) {
					if(current == o) return false;
					while((current = keys[pos = (++pos & mask)]) != 0)
						if(current == o) return false;
				}
				keys[pos] = o;
			}
			if(size++ >= maxFill) rehash(HashUtil.arraySize(size+1, HashUtil.DEFAULT_LOAD_FACTOR));
			return true;
		}
		
		@Override
		public boolean contains(Object o) {
			if(o == null) return false;
			if(o instanceof Integer && ((Integer)o).intValue() == 0) return containsNull;
			int pos = HashUtil.mix(o.hashCode()) & mask;
			int current = keys[pos];
			if(current == 0) return false;
			if(Objects.equals(o, Integer.valueOf(current))) return true;
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == 0) return false;
				else if(Objects.equals(o, Integer.valueOf(current))) return true;
			}
		}

		@Override
		public boolean remove(Object o) {
			if(o == null) return false;
			if(o instanceof Integer && ((Integer)o).intValue() == 0) return (containsNull ? removeNullIndex() : false);
			int pos = HashUtil.mix(o.hashCode()) & mask;
			int current = keys[pos];
			if(current == 0) return false;
			if(Objects.equals(o, Integer.valueOf(current))) return removeIndex(pos);
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == 0) return false;
				else if(Objects.equals(o, Integer.valueOf(current))) return removeIndex(pos);
			}
		}

		@Override
		public boolean contains(int o) {
			if(o == 0) return containsNull;
			int pos = HashUtil.mix(Integer.hashCode(o)) & mask;
			int current = keys[pos];
			if(current == 0) return false;
			if(current == o) return true;
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == 0) return false;
				else if(current == o) return true;
			}
		}
		
		@Override
		public boolean remInt(int o) {
			if(o == 0) return containsNull ? removeNullIndex() : false;
			int pos = HashUtil.mix(Integer.hashCode(o)) & mask;
			int current = keys[pos];
			if(current == 0) return false;
			if(current == o) return removeIndex(pos);
			while(true) {
				if((current = keys[pos = (++pos & mask)]) == 0) return false;
				else if(current == o) return removeIndex(pos);
			}
		}
		
		protected boolean removeIndex(int pos) {
			if(pos == nullIndex) return containsNull ? removeNullIndex() : false;
			keys[pos] = 0;
			size--;
			shiftKeys(pos);
			if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
			return true;
		}
		
		protected boolean removeNullIndex() {
			containsNull = false;
			keys[nullIndex] = 0;
			size--;
			if(nullIndex > minCapacity && size < maxFill / 4 && nullIndex > HashUtil.DEFAULT_MIN_CAPACITY) rehash(nullIndex / 2);
			return true;
		}
		
		@Override
		public IntIterator iterator() {
			return new SetIterator();
		}
		
		@Override
		public void forEach(IntConsumer action) {
			if(size() <= 0) return;
			if(containsNull) action.accept(keys[nullIndex]);
			for(int i = nullIndex-1;i>=0;i--) {
				if(keys[i] != 0) action.accept(keys[i]);
			}
		}
		
		@Override
		public DistinctCollectionWrapper copy() {
			DistinctCollectionWrapper set = new DistinctCollectionWrapper(0);
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
			int current;
			while(true) {
				startPos = ((last = startPos) + 1) & mask;
				while(true){
					if((current = keys[startPos]) == 0) {
						keys[last] = 0;
						return;
					}
					slot = HashUtil.mix(Integer.hashCode(current)) & mask;
					if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
					startPos = ++startPos & mask;
				}
				keys[last] = current;
			}
		}
		
		protected void rehash(int newSize) {
			int newMask = newSize - 1;
			int[] newKeys = new int[newSize + 1];
			for(int i = nullIndex, pos = 0, j = (size - (containsNull ? 1 : 0));j-- != 0;) {
				while(true) {
					if(--i < 0) throw new ConcurrentModificationException("Set was modified during rehash");
					if(keys[i] != 0) break;
				}
				if(newKeys[pos = HashUtil.mix(Integer.hashCode(keys[i])) & newMask] != 0)
					while(newKeys[pos = (++pos & newMask)] != 0);
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
			Arrays.fill(keys, 0);
		}
		
		@Override
		public int size() {
			return size;
		}
		
		private class SetIterator implements IntIterator {
			int pos = nullIndex;
			int returnedPos = -1;
			int lastReturned = -1;
			int nextIndex = Integer.MIN_VALUE;
			boolean returnNull = containsNull;
			int[] wrapped = null;
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
							if(keys[pos] != 0){
								nextIndex = pos;
								break;
							}
						}
					}
				}
				return nextIndex != Integer.MIN_VALUE;
			}
			
			@Override
			public int nextInt() {
				if(!hasNext()) throw new NoSuchElementException();
				returnedPos = pos;
				if(nextIndex < 0){
					lastReturned = Integer.MAX_VALUE;
					int value = wrapped[nextIndex];
					nextIndex = Integer.MIN_VALUE;
					return value;
				}
				int value = keys[(lastReturned = nextIndex)];
				nextIndex = Integer.MIN_VALUE;
				return value;
			}
			
			@Override
			public void remove() {
				if(lastReturned == -1) throw new IllegalStateException();
				if(lastReturned == nullIndex) {
					containsNull = false;
					keys[nullIndex] = 0;
				}
				else if(returnedPos >= 0) shiftKeys(returnedPos);
				else {
					DistinctCollectionWrapper.this.remInt(wrapped[-returnedPos - 1]);
					lastReturned = -1;
					return;
				}
				size--;
				lastReturned = -1;
			}
			
			private void shiftKeys(int startPos) {
				int slot, last;
				int current;
				while(true) {
					startPos = ((last = startPos) + 1) & mask;
					while(true){
						if((current = keys[startPos]) == 0) {
							keys[last] = 0;
							return;
						}
						slot = HashUtil.mix(Integer.hashCode(current)) & mask;
						if(last <= startPos ? (last >= slot || slot > startPos) : (last >= slot && slot > startPos)) break;
						startPos = ++startPos & mask;
					}
					if(startPos < last) addWrapper(keys[startPos]);
					keys[last] = current;
				}
			}
			
			private void addWrapper(int value) {
				if(wrapped == null) wrapped = new int[2];
				else if(wrappedIndex >= wrapped.length) {
					int[] newArray = new int[wrapped.length * 2];
					System.arraycopy(wrapped, 0, newArray, 0, wrapped.length);
					wrapped = newArray;
				}
				wrapped[wrappedIndex++] = value;
			}
		}
	}
	
	private static class SingletonCollection extends AbstractIntCollection
	{
		int element;
		
		SingletonCollection(int element) {
			this.element = element;
		}
		
		@Override
		public boolean remInt(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean add(int o) { throw new UnsupportedOperationException(); }
		@Override
		public IntIterator iterator()
		{
			return new IntIterator() {
				boolean next = true;
				@Override
				public boolean hasNext() { return next; }
				@Override
				public int nextInt() {
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
			return Integer.hashCode(element);
		}
		
		@Override
		public int size() { return 1; }
		
		@Override
		public SingletonCollection copy() { return new SingletonCollection(element); }
	}
	
	/**
	 * Synchronized Collection Wrapper for the synchronizedCollection function
	 */
	public static class SynchronizedCollection implements IntCollection {
		IntCollection c;
		protected Object mutex;
		
		SynchronizedCollection(IntCollection c) {
			this.c = c;
			mutex = this;
		}
		
		SynchronizedCollection(IntCollection c, Object mutex) {
			this.c = c;
			this.mutex = mutex;
		}
		
		@Override
		public boolean add(int o) { synchronized(mutex) { return c.add(o); } }
		@Override
		public boolean addAll(Collection<? extends Integer> c) { synchronized(mutex) { return this.c.addAll(c); } }
		@Override
		public boolean addAll(IntCollection c) { synchronized(mutex) { return this.c.addAll(c); } }
		@Override
		public boolean addAll(int[] e, int offset, int length) { synchronized(mutex) { return c.addAll(e, offset, length); } }
		@Override
		public boolean contains(int o) { synchronized(mutex) { return c.contains(o); } }
		@Override
		@Deprecated
		public boolean containsAll(Collection<?> c) { synchronized(mutex) { return this.c.containsAll(c); } }
		
		@Override
		@Deprecated
		public boolean containsAny(Collection<?> c) { synchronized(mutex) { return this.c.containsAny(c); } }
		
		@Override
		public boolean containsAll(IntCollection c) { synchronized(mutex) { return this.c.containsAll(c); } }
		
		@Override
		public boolean containsAny(IntCollection c) { synchronized(mutex) { return this.c.containsAny(c); } }
		
		@Override
		public int size() { synchronized(mutex) { return c.size(); } }
		
		@Override
		public boolean isEmpty() { synchronized(mutex) { return c.isEmpty(); } }
		
		@Override
		public IntIterator iterator() {
			return c.iterator();
		}
		
		@Override
		public IntCollection copy() { synchronized(mutex) { return c.copy(); } }
		
		@Override
		@Deprecated
		public boolean remove(Object o) { synchronized(mutex) { return c.remove(o); } }
		@Override
		@Deprecated
		public boolean removeAll(Collection<?> c) { synchronized(mutex) { return this.c.removeAll(c); } }
		@Override
		@Deprecated
		public boolean retainAll(Collection<?> c) { synchronized(mutex) { return this.c.retainAll(c); } }
		@Override
		public boolean remInt(int o) { synchronized(mutex) { return c.remInt(o); } }
		@Override
		public boolean removeAll(IntCollection c) { synchronized(mutex) { return this.c.removeAll(c); } }
		@Override
		public boolean removeAll(IntCollection c, IntConsumer r) { synchronized(mutex) { return this.c.removeAll(c, r); } }
		@Override
		public boolean retainAll(IntCollection c) { synchronized(mutex) { return this.c.retainAll(c); } }
		@Override
		public boolean retainAll(IntCollection c, IntConsumer r) { synchronized(mutex) { return this.c.retainAll(c, r); } }
		@Override
		public boolean remIf(IntPredicate filter){ synchronized(mutex) { return c.remIf(filter); } }
		@Override
		public void clear() { synchronized(mutex) { c.clear(); } }
		@Override
		public Object[] toArray() { synchronized(mutex) { return c.toArray(); } }
		@Override
		public <T> T[] toArray(T[] a) { synchronized(mutex) { return c.toArray(a); } }
		@Override
		public int[] toIntArray() { synchronized(mutex) { return c.toIntArray(); } }
		@Override
		public int[] toIntArray(int[] a) { synchronized(mutex) { return c.toIntArray(a); } }
		@Override
		public void forEach(IntConsumer action) { synchronized(mutex) { c.forEach(action); } }
		@Override
		@Deprecated
		public void forEach(Consumer<? super Integer> action) { synchronized(mutex) { c.forEach(action); } }
		@Override
		public void forEachIndexed(IntIntConsumer action) { synchronized(mutex) { c.forEachIndexed(action); } }
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
		public <E> void forEach(E input, ObjectIntConsumer<E> action) { synchronized(mutex) { c.forEach(input, action); } }
		@Override
		public boolean matchesAny(IntPredicate filter) { synchronized(mutex) { return c.matchesAny(filter); } }
		@Override
		public boolean matchesNone(IntPredicate filter) { synchronized(mutex) { return c.matchesNone(filter); } }
		@Override
		public boolean matchesAll(IntPredicate filter) { synchronized(mutex) { return c.matchesAll(filter); } }
		@Override
		public int reduce(int identity, IntIntUnaryOperator operator) { synchronized(mutex) { return c.reduce(identity, operator); } }
		@Override
		public int reduce(IntIntUnaryOperator operator) { synchronized(mutex) { return c.reduce(operator); } }
		@Override
		public int findFirst(IntPredicate filter) { synchronized(mutex) { return c.findFirst(filter); } }
		@Override
		public int count(IntPredicate filter) { synchronized(mutex) { return c.count(filter); } }
	}
	
	/**
	 * Unmodifyable Collection Wrapper for the unmodifyableCollection method
	 */
	public static class UnmodifiableCollection implements IntCollection {
		IntCollection c;
		
		UnmodifiableCollection(IntCollection c) {
			this.c = c;
		}
		
		@Override
		public boolean add(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(Collection<? extends Integer> c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(int[] e, int offset, int length) { throw new UnsupportedOperationException(); }
		@Override
		public boolean contains(int o) { return c.contains(o); }
		@Override
		public boolean containsAll(IntCollection c) { return this.c.containsAll(c); }
		@Override
		public boolean containsAny(IntCollection c) { return this.c.containsAny(c); }
		@Override
		@Deprecated
		public boolean containsAny(Collection<?> c) { return this.c.containsAny(c); }
		@Override
		@Deprecated
		public boolean containsAll(Collection<?> c) { return this.c.containsAll(c); }
		@Override
		public int size() { return c.size(); }
		@Override
		public boolean isEmpty() { return c.isEmpty(); }
		@Override
		public IntIterator iterator() { return IntIterators.unmodifiable(c.iterator()); }
		@Override
		public IntCollection copy() { return c.copy(); }
		@Override
		@Deprecated
		public boolean remove(Object o) { throw new UnsupportedOperationException(); }
		@Override
		@Deprecated
		public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		@Deprecated
		public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		@Deprecated
		public boolean removeIf(Predicate<? super Integer> filter) { throw new UnsupportedOperationException(); }
		@Override
		public boolean remInt(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(IntCollection c, IntConsumer r) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(IntCollection c, IntConsumer r) { throw new UnsupportedOperationException(); }
		@Override
		public boolean remIf(IntPredicate filter){ throw new UnsupportedOperationException(); }
		@Override
		public void clear() { throw new UnsupportedOperationException(); }
		@Override
		public Object[] toArray() { return c.toArray(); }
		@Override
		public <T> T[] toArray(T[] a) { return c.toArray(a); }
		@Override
		public int[] toIntArray() { return c.toIntArray(); }
		@Override
		public int[] toIntArray(int[] a) { return c.toIntArray(a); }
		@Override
		public void forEach(IntConsumer action) { c.forEach(action); }
		@Override
		@Deprecated
		public void forEach(Consumer<? super Integer> action) { c.forEach(action); }
		@Override
		public void forEachIndexed(IntIntConsumer action) { c.forEachIndexed(action); }
		@Override
		public int hashCode() { return c.hashCode(); }
		@Override
		public boolean equals(Object obj) { return obj == this || c.equals(obj); }
		@Override
		public String toString() { return c.toString(); }
		@Override
		public <E> void forEach(E input, ObjectIntConsumer<E> action) { c.forEach(input, action); }
		@Override
		public boolean matchesAny(IntPredicate filter) { return c.matchesAny(filter); }
		@Override
		public boolean matchesNone(IntPredicate filter) { return c.matchesNone(filter); }
		@Override
		public boolean matchesAll(IntPredicate filter) { return c.matchesAll(filter); }
		@Override
		public int reduce(int identity, IntIntUnaryOperator operator) { return c.reduce(identity, operator); }
		@Override
		public int reduce(IntIntUnaryOperator operator) { return c.reduce(operator); }
		@Override
		public int findFirst(IntPredicate filter) { return c.findFirst(filter); }
		@Override
		public int count(IntPredicate filter) { return c.count(filter); }
	}
	
	/**
	 * Empty Collection implementation for the empty collection function
	 */
	public static class EmptyCollection extends AbstractIntCollection {
		@Override
		public boolean add(int o) { throw new UnsupportedOperationException(); }
		
		@Override
		public boolean addAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean addAll(int[] e, int offset, int length) { throw new UnsupportedOperationException(); }
		
		@Override
		public boolean contains(int o) { return false; }
		@Override
		public boolean containsAll(IntCollection c) { return c.isEmpty(); }
		@Override
		public boolean containsAny(IntCollection c) { return false; }
		@Override
		@Deprecated
		public boolean containsAny(Collection<?> c) { return false; }
		@Override
		@Deprecated
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
		@Deprecated
		public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		@Deprecated
		public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
		@Override
		@Deprecated
		public boolean removeIf(Predicate<? super Integer> filter) { throw new UnsupportedOperationException(); }
		@Override
		public boolean remInt(int o) { throw new UnsupportedOperationException(); }
		@Override
		public boolean removeAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean retainAll(IntCollection c) { throw new UnsupportedOperationException(); }
		@Override
		public boolean remIf(IntPredicate filter){ throw new UnsupportedOperationException(); }
		@Override
		public Object[] toArray() { return ObjectArrays.EMPTY_ARRAY; }
		@Override
		public <T> T[] toArray(T[] a) {
			if(a != null && a.length > 0)
				a[0] = null;
			return a;
		}
		
		@Override
		public int[] toIntArray() { return IntArrays.EMPTY_ARRAY; }
		@Override
		public int[] toIntArray(int[] a) {
			if(a != null && a.length > 0)
				a[0] = 0;
			return a;
		}
		
		@Override
		public IntIterator iterator() { return IntIterators.empty(); }
		@Override
		public void clear() {}
		@Override
		public int size() { return 0; }
		@Override
		public EmptyCollection copy() { return this; }
	}
}