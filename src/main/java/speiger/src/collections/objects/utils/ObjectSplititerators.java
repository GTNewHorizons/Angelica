package speiger.src.collections.objects.utils;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;

import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.collections.ObjectSplititerator;
import speiger.src.collections.utils.SanityChecks;

/**
 * Helper class that provides SplitIterators for normal and stream usage
 */
public class ObjectSplititerators
{
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	*/
	public static <T> ObjectSplititerator<T> createArraySplititerator(T[] array, int characteristics) { return createArraySplititerator(array, 0, array.length, characteristics);}
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param size the maximum index within the array
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	* @throws IllegalStateException if the size is outside of the array size
	*/
	public static <T> ObjectSplititerator<T> createArraySplititerator(T[] array, int size, int characteristics) { return createArraySplititerator(array, 0, size, characteristics);}
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param offset the starting index of the array
	* @param size the maximum index within the array
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	* @throws IllegalStateException the offset and size are outside of the arrays range
	*/
	public static <T> ObjectSplititerator<T> createArraySplititerator(T[] array, int offset, int size, int characteristics) {
		SanityChecks.checkArrayCapacity(array.length, offset, size);
		return new TypeArraySplitIterator<>(array, offset, size, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param collection the collection that should be wrapped in a split iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	*/
	public static <T> ObjectSplititerator<T> createSplititerator(ObjectCollection<T> collection, int characteristics) {
		return new TypeIteratorSpliterator<>(collection, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param iterator the Iterator that should be wrapped in a split iterator
	* @param characteristics characteristics properties of this spliterator's source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	*/
	public static <T> ObjectSplititerator<T> createUnknownSplititerator(ObjectIterator<T> iterator, int characteristics) {
		return new TypeIteratorSpliterator<>(iterator, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param iterator the collection that should be wrapped in a split iterator
	* @param size the amount of elements in the iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @param <T> the keyType of elements maintained by this Collection
	* @return a Type Specific SplitIterator
	*/
	public static <T> ObjectSplititerator<T> createSizedSplititerator(ObjectIterator<T> iterator, long size, int characteristics) {
		return new TypeIteratorSpliterator<>(iterator, size, characteristics);
	}
	
	static class TypeIteratorSpliterator<T> implements ObjectSplititerator<T> {
		static final int BATCH_UNIT = 1 << 10;
		static final int MAX_BATCH = 1 << 25;
		private final ObjectCollection<T> collection;
		private ObjectIterator<T> it;
		private final int characteristics;
		private long est;
		private int batch;
	
		TypeIteratorSpliterator(ObjectCollection<T> collection, int characteristics) {
			this.collection = collection;
			it = null;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								: characteristics;
		}
	
		TypeIteratorSpliterator(ObjectIterator<T> iterator, long size, int characteristics) {
			collection = null;
			it = iterator;
			est = size;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								: characteristics;
		}
		
		TypeIteratorSpliterator(ObjectIterator<T> iterator, int characteristics) {
			collection = null;
			it = iterator;
			est = Long.MAX_VALUE;
			this.characteristics = characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
		}
		
		private ObjectIterator<T> iterator()
		{
			if (it == null) {
				it = collection.iterator();
				est = collection.size();
			}
			return it;
		}
		
		@Override
		public ObjectSplititerator<T> trySplit() {
			ObjectIterator<T> i = iterator();
			if (est > 1 && i.hasNext()) {
				int n = Math.min(batch + BATCH_UNIT, Math.min((int)est, MAX_BATCH));
				T[] a = (T[])new Object[n];
				int j = 0;
				do { a[j] = i.next(); } while (++j < n && i.hasNext());
				batch = j;
				if (est != Long.MAX_VALUE)
					est -= j;
				return new TypeArraySplitIterator<>(a, 0, j, characteristics);
			}
			return null;
		}
		
		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (action == null) throw new NullPointerException();
			iterator().forEachRemaining(action);
		}
		
		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (action == null) throw new NullPointerException();
			ObjectIterator<T> iter = iterator();
			if (iter.hasNext()) {
				action.accept(iter.next());
				return true;
			}
			return false;
		}
		
		@Override
		public long estimateSize() {
			iterator();
			return est;
		}
	
		@Override
		public int characteristics() { return characteristics; }
		
		@Override
		public Comparator<? super T> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
	
		@Override
		public T next() { return iterator().next(); }
	
		@Override
		public boolean hasNext() { return iterator().hasNext(); }
	}
	
	static final class TypeArraySplitIterator<T> implements ObjectSplititerator<T> {
		private final T[] array;
		private int index;
		private final int fence; 
		private final int characteristics;
		
		public TypeArraySplitIterator(T[] array, int origin, int fence, int additionalCharacteristics) {
			this.array = array;
			index = origin;
			this.fence = fence;
			characteristics = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
		}
		
		@Override
		public ObjectSplititerator<T> trySplit() {
			int lo = index, mid = (lo + fence) >>> 1;
			return (lo >= mid) ? null : new TypeArraySplitIterator<>(array, lo, index = mid, characteristics);
		}
		
		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (action == null) throw new NullPointerException();
			T[] a; int i, hi;
			if ((a = array).length >= (hi = fence) && (i = index) >= 0 && i < (index = hi)) {
				do { action.accept(a[i]); } while (++i < hi);
			}
		}
		
		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (action == null) throw new NullPointerException();
			if (index >= 0 && index < fence) {
				action.accept(array[index++]);
				return true;
			}
			return false;
		}
		
		@Override
		public long estimateSize() { return fence - index; }
		@Override
		public int characteristics() { return characteristics; }
		@Override
		public Comparator<? super T> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			return array[index++];
		}
		
		@Override
		public boolean hasNext() { return index < fence; }
	}
	
}