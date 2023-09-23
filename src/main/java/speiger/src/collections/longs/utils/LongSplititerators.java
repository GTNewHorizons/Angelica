package speiger.src.collections.longs.utils;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterator.OfLong;
import java.util.function.Consumer;

import speiger.src.collections.longs.collections.LongCollection;
import speiger.src.collections.longs.collections.LongIterator;
import speiger.src.collections.longs.collections.LongSplititerator;
import speiger.src.collections.longs.functions.LongConsumer;
import speiger.src.collections.utils.SanityChecks;

/**
 * Helper class that provides SplitIterators for normal and stream usage
 */
public class LongSplititerators
{
	/**
	 * Creates A stream compatible split iterator without copying the original array or boxing
	 * @param array that should be wrapped into a split iterator
	 * @param characteristics characteristics properties of this spliterator's  source or elements.
	 * @return a split iterator of a Stream compatible type
	 */
	public static OfLong createArrayJavaSplititerator(long[] array, int characteristics) { return createArrayJavaSplititerator(array, 0, array.length, characteristics);}
	/**
	 * Creates A stream compatible split iterator without copying the original array or boxing
	 * @param array that should be wrapped into a split iterator
	 * @param size the maximum index within the array
	 * @param characteristics characteristics properties of this spliterator's  source or elements.
	 * @return a split iterator of a Stream compatible type
	 * @throws IllegalStateException if the size is outside of the array size
	 */
	public static OfLong createArrayJavaSplititerator(long[] array, int size, int characteristics) { return createArrayJavaSplititerator(array, 0, size, characteristics);}
	/**
	 * Creates A stream compatible split iterator without copying the original array or boxing
	 * @param array that should be wrapped into a split iterator
	 * @param offset the starting index of the array
	 * @param size the maximum index within the array
	 * @param characteristics characteristics properties of this spliterator's  source or elements.
	 * @return a split iterator of a Stream compatible type
	 * @throws IllegalStateException the offset and size are outside of the arrays range
	 */
	public static OfLong createArrayJavaSplititerator(long[] array, int offset, int size, int characteristics) {
		SanityChecks.checkArrayCapacity(array.length, offset, size);
		return new ArraySplitIterator(array, offset, size, characteristics);
	}
	
	/**
	 * Creates a stream compatible split iterator without copying it or boxing it
	 * @param collection the collection that should be wrapped in a split iterator
	 * @param characteristics characteristics properties of this spliterator's  source or elements.
	 * @return a split iterator of a Stream compatible type
	 */
	public static OfLong createJavaSplititerator(LongCollection collection, int characteristics) {
		return new IteratorSpliterator(collection, characteristics);
	}
	
	/**
	 * Creates a stream compatible split iterator without copying it or boxing it
	 * @param iterator the Iterator that should be wrapped in a split iterator
	 * @param characteristics characteristics properties of this spliterator's source or elements.
	 * @return a split iterator of a Stream compatible type
	 */
	public static OfLong createUnknownJavaSplititerator(LongIterator iterator, int characteristics) {
		return new IteratorSpliterator(iterator, characteristics);
	}
	
	/**
	 * Creates a stream compatible split iterator without copying it or boxing it
	 * @param iterator the collection that should be wrapped in a split iterator
	 * @param size the amount of elements in the iterator
	 * @param characteristics characteristics properties of this spliterator's  source or elements.
	 * @return a split iterator of a Stream compatible type
	 */
	public static OfLong createSizedJavaSplititerator(LongIterator iterator, long size, int characteristics) {
		return new IteratorSpliterator(iterator, size, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @return a Type Specific SplitIterator
	*/
	public static LongSplititerator createArraySplititerator(long[] array, int characteristics) { return createArraySplititerator(array, 0, array.length, characteristics);}
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param size the maximum index within the array
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @return a Type Specific SplitIterator
	* @throws IllegalStateException if the size is outside of the array size
	*/
	public static LongSplititerator createArraySplititerator(long[] array, int size, int characteristics) { return createArraySplititerator(array, 0, size, characteristics);}
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param array that should be wrapped into a split iterator
	* @param offset the starting index of the array
	* @param size the maximum index within the array
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @return a Type Specific SplitIterator
	* @throws IllegalStateException the offset and size are outside of the arrays range
	*/
	public static LongSplititerator createArraySplititerator(long[] array, int offset, int size, int characteristics) {
		SanityChecks.checkArrayCapacity(array.length, offset, size);
		return new TypeArraySplitIterator(array, offset, size, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param collection the collection that should be wrapped in a split iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @return a Type Specific SplitIterator
	*/
	public static LongSplititerator createSplititerator(LongCollection collection, int characteristics) {
		return new TypeIteratorSpliterator(collection, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param iterator the Iterator that should be wrapped in a split iterator
	* @param characteristics characteristics properties of this spliterator's source or elements.
	* @return a Type Specific SplitIterator
	*/
	public static LongSplititerator createUnknownSplititerator(LongIterator iterator, int characteristics) {
		return new TypeIteratorSpliterator(iterator, characteristics);
	}
	
	/**
	* Creates a Type Specific SplitIterator to reduce boxing/unboxing
	* @param iterator the collection that should be wrapped in a split iterator
	* @param size the amount of elements in the iterator
	* @param characteristics characteristics properties of this spliterator's  source or elements.
	* @return a Type Specific SplitIterator
	*/
	public static LongSplititerator createSizedSplititerator(LongIterator iterator, long size, int characteristics) {
		return new TypeIteratorSpliterator(iterator, size, characteristics);
	}
	
	static class TypeIteratorSpliterator implements LongSplititerator {
		static final int BATCH_UNIT = 1 << 10;
		static final int MAX_BATCH = 1 << 25;
		private final LongCollection collection;
		private LongIterator it;
		private final int characteristics;
		private long est;
		private int batch;
	
		TypeIteratorSpliterator(LongCollection collection, int characteristics) {
			this.collection = collection;
			it = null;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								: characteristics;
		}
	
		TypeIteratorSpliterator(LongIterator iterator, long size, int characteristics) {
			collection = null;
			it = iterator;
			est = size;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								: characteristics;
		}
		
		TypeIteratorSpliterator(LongIterator iterator, int characteristics) {
			collection = null;
			it = iterator;
			est = Long.MAX_VALUE;
			this.characteristics = characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
		}
		
		private LongIterator iterator()
		{
			if (it == null) {
				it = collection.iterator();
				est = collection.size();
			}
			return it;
		}
		
		@Override
		public LongSplititerator trySplit() {
			LongIterator i = iterator();
			if (est > 1 && i.hasNext()) {
				int n = Math.min(batch + BATCH_UNIT, Math.min((int)est, MAX_BATCH));
				long[] a = new long[n];
				int j = 0;
				do { a[j] = i.nextLong(); } while (++j < n && i.hasNext());
				batch = j;
				if (est != Long.MAX_VALUE)
					est -= j;
				return new TypeArraySplitIterator(a, 0, j, characteristics);
			}
			return null;
		}
		
		@Override
		public void forEachRemaining(LongConsumer action) {
			if (action == null) throw new NullPointerException();
			iterator().forEachRemaining(action);
		}
		
		@Override
		public boolean tryAdvance(LongConsumer action) {
			if (action == null) throw new NullPointerException();
			LongIterator iter = iterator();
			if (iter.hasNext()) {
				action.accept(iter.nextLong());
				return true;
			}
			return false;
		}
		
		@Override
		public boolean tryAdvance(Consumer<? super Long> action) {
			if (action == null) throw new NullPointerException();
			LongIterator iter = iterator();
			if (iter.hasNext()) {
				action.accept(Long.valueOf(iter.nextLong()));
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
		public Comparator<? super Long> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
	
		@Override
		public long nextLong() { return iterator().nextLong(); }
	
		@Override
		public boolean hasNext() { return iterator().hasNext(); }
	}
	
	static final class TypeArraySplitIterator implements LongSplititerator {
		private final long[] array;
		private int index;
		private final int fence; 
		private final int characteristics;
		
		public TypeArraySplitIterator(long[] array, int origin, int fence, int additionalCharacteristics) {
			this.array = array;
			index = origin;
			this.fence = fence;
			characteristics = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
		}
		
		@Override
		public LongSplititerator trySplit() {
			int lo = index, mid = (lo + fence) >>> 1;
			return (lo >= mid) ? null : new TypeArraySplitIterator(array, lo, index = mid, characteristics);
		}
		
		@Override
		public void forEachRemaining(LongConsumer action) {
			if (action == null) throw new NullPointerException();
			long[] a; int i, hi;
			if ((a = array).length >= (hi = fence) && (i = index) >= 0 && i < (index = hi)) {
				do { action.accept(a[i]); } while (++i < hi);
			}
		}
		
		@Override
		public boolean tryAdvance(LongConsumer action) {
			if (action == null) throw new NullPointerException();
			if (index >= 0 && index < fence) {
				action.accept(array[index++]);
				return true;
			}
			return false;
		}
		
		@Override
		public boolean tryAdvance(Consumer<? super Long> action) {
			if (action == null) throw new NullPointerException();
			if (index >= 0 && index < fence) {
				action.accept(Long.valueOf(array[index++]));
				return true;
			}
			return false;
		}
		
		@Override
		public long estimateSize() { return fence - index; }
		@Override
		public int characteristics() { return characteristics; }
		@Override
		public Comparator<? super Long> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			return array[index++];
		}
		
		@Override
		public boolean hasNext() { return index < fence; }
	}
	
	static class IteratorSpliterator implements OfLong {
		static final int BATCH_UNIT = 1 << 10;
		static final int MAX_BATCH = 1 << 25;
		private final LongCollection collection;
		private LongIterator it;
		private final int characteristics;
		private long est;
		private int batch;

		IteratorSpliterator(LongCollection collection, int characteristics) {
			this.collection = collection;
			it = null;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								   ? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								   : characteristics;
		}

		IteratorSpliterator(LongIterator iterator, long size, int characteristics) {
			collection = null;
			it = iterator;
			est = size;
			this.characteristics = (characteristics & Spliterator.CONCURRENT) == 0
								   ? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
								   : characteristics;
		}
		
		IteratorSpliterator(LongIterator iterator, int characteristics) {
			collection = null;
			it = iterator;
			est = Long.MAX_VALUE;
			this.characteristics = characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED);
		}
		
		private LongIterator iterator()
		{
			if (it == null) {
				it = collection.iterator();
				est = collection.size();
			}
			return it;
		}
		
		@Override
		public OfLong trySplit() {
			LongIterator i = iterator();
			if (est > 1 && i.hasNext()) {
				int n = Math.min(batch + BATCH_UNIT, Math.min((int)est, MAX_BATCH));
				long[] a = new long[n];
				int j = 0;
				do { a[j] = i.nextLong(); } while (++j < n && i.hasNext());
				batch = j;
				if (est != Long.MAX_VALUE)
					est -= j;
				return new ArraySplitIterator(a, 0, j, characteristics);
			}
			return null;
		}
		
		@Override
		public void forEachRemaining(java.util.function.LongConsumer action) {
			if (action == null) throw new NullPointerException();
			iterator().forEachRemaining(T -> action.accept(T));
		}

		@Override
		public boolean tryAdvance(java.util.function.LongConsumer action) {
			if (action == null) throw new NullPointerException();
			LongIterator iter = iterator();
			if (iter.hasNext()) {
				action.accept(iter.nextLong());
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
		public Comparator<? super Long> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
	}
	
	static final class ArraySplitIterator implements OfLong {
		private final long[] array;
		private int index;
		private final int fence; 
		private final int characteristics;
		
		public ArraySplitIterator(long[] array, int origin, int fence, int additionalCharacteristics) {
			this.array = array;
			index = origin;
			this.fence = fence;
			characteristics = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
		}
		
		@Override
		public OfLong trySplit() {
			int lo = index, mid = (lo + fence) >>> 1;
			return (lo >= mid) ? null : new ArraySplitIterator(array, lo, index = mid, characteristics);
		}
		
		@Override
		public void forEachRemaining(java.util.function.LongConsumer action) {
			if (action == null) throw new NullPointerException();
			long[] a; int i, hi;
			if ((a = array).length >= (hi = fence) && (i = index) >= 0 && i < (index = hi)) {
				do { action.accept(a[i]); } while (++i < hi);
			}
		}

		@Override
		public boolean tryAdvance(java.util.function.LongConsumer action) {
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
		public Comparator<? super Long> getComparator() {
			if (hasCharacteristics(4)) //Sorted
				return null;
			throw new IllegalStateException();
		}
	}
}