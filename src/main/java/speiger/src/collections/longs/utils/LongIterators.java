package speiger.src.collections.longs.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.LongPredicate;

import speiger.src.collections.longs.collections.LongIterator;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.utils.ObjectIterators;
import speiger.src.collections.longs.functions.LongConsumer;
import speiger.src.collections.longs.functions.LongComparator;
import speiger.src.collections.longs.functions.function.LongFunction;
import speiger.src.collections.longs.lists.LongListIterator;
import speiger.src.collections.longs.collections.LongBidirectionalIterator;
import speiger.src.collections.longs.collections.LongCollection;

/**
 * A Helper class for Iterators
 */
public class LongIterators
{
	/**
	 * Empty Iterator Reference
	 */
	private static final EmptyIterator EMPTY = new EmptyIterator();
	
	/**
	 * Returns a Immutable EmptyIterator instance that is automatically casted.
	 * @return an empty iterator
	 */
	public static EmptyIterator empty() {
		return EMPTY;
	}
	
	/**
	 * Inverter function for Bidirectional Iterators
	 * @param it the iterator that should be inverted
	 * @return a Inverted Bidirectional Iterator. If it was inverted then it just gives back the original reference
	 */
	public static LongBidirectionalIterator invert(LongBidirectionalIterator it) {
		return it instanceof ReverseBiIterator ? ((ReverseBiIterator)it).it : new ReverseBiIterator(it);
	}
	
	/**
	 * Inverter function for List Iterators
	 * @param it the iterator that should be inverted
	 * @return a Inverted List Iterator. If it was inverted then it just gives back the original reference
	 */
	public static LongListIterator invert(LongListIterator it) {
		return it instanceof ReverseListIterator ? ((ReverseListIterator)it).it : new ReverseListIterator(it);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static LongIterator unmodifiable(LongIterator iterator) {
		return iterator instanceof UnmodifiableIterator ? iterator : new UnmodifiableIterator(iterator);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static LongBidirectionalIterator unmodifiable(LongBidirectionalIterator iterator) {
		return iterator instanceof UnmodifiableBiIterator ? iterator : new UnmodifiableBiIterator(iterator);
	}
	
	/**
	 * Returns a Immutable ListIterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable listiterator wrapper. If the ListIterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static LongListIterator unmodifiable(LongListIterator iterator) {
		return iterator instanceof UnmodifiableListIterator ? iterator : new UnmodifiableListIterator(iterator);
	}
	
	/**
	 * A Helper function that maps a Java-Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <E> ObjectIterator<E> map(Iterator<? extends Long> iterator, LongFunction<E> mapper) {
		return new MappedIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that maps a Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <E> ObjectIterator<E> map(LongIterator iterator, LongFunction<E> mapper) {
		return new MappedIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E, V extends Iterable<E>> ObjectIterator<E> flatMap(Iterator<? extends Long> iterator, LongFunction<V> mapper) {
		return new FlatMappedIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E, V extends Iterable<E>> ObjectIterator<E> flatMap(LongIterator iterator, LongFunction<V> mapper) {
		return new FlatMappedIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E> ObjectIterator<E> arrayFlatMap(Iterator<? extends Long> iterator, LongFunction<E[]> mapper) {
		return new FlatMappedArrayIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E> ObjectIterator<E> arrayFlatMap(LongIterator iterator, LongFunction<E[]> mapper) {
		return new FlatMappedArrayIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that filters out all desired elements from a Java-Iterator
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterator
	 */
	public static LongIterator filter(Iterator<? extends Long> iterator, LongPredicate filter) {
		return new FilteredIterator(wrap(iterator), filter);
	}
	
	/**
	 * A Helper function that filters out all desired elements
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterator
	 */
	public static LongIterator filter(LongIterator iterator, LongPredicate filter) {
		return new FilteredIterator(iterator, filter);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements.
	 * @param iterator that should be distinct
	 * @return a distinct iterator
	 */
	public static LongIterator distinct(LongIterator iterator) {
		return new DistinctIterator(iterator);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements from a Java Iterator.
	 * @param iterator that should be distinct
	 * @return a distinct iterator
	 */
	public static LongIterator distinct(Iterator<? extends Long> iterator) {
		return new DistinctIterator(wrap(iterator));
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @return a repeating iterator
	 */
	public static LongIterator repeat(LongIterator iterator, int repeats) {
		return new RepeatingIterator(iterator, repeats);
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times from a Java Iterator
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @return a repeating iterator
	 */
	public static LongIterator repeat(Iterator<? extends Long> iterator, int repeats) {
		return new RepeatingIterator(wrap(iterator), repeats);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterator
	 */
	public static LongIterator limit(LongIterator iterator, long limit) {
		return new LimitedIterator(iterator, limit);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size from a Java Iterator
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterator
	 */
	public static LongIterator limit(Iterator<? extends Long> iterator, long limit) {
		return new LimitedIterator(wrap(iterator), limit);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @return a new sorted iterator
	 */
	public static LongIterator sorted(LongIterator iterator, LongComparator sorter) {
		return new SortedIterator(iterator, sorter);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand from a Java Iterator.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @return a new sorted iterator
	 */
	public static LongIterator sorted(Iterator<? extends Long> iterator, LongComparator sorter) {
		return new SortedIterator(wrap(iterator), sorter);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator.
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @return a peeked iterator
	 */
	public static LongIterator peek(LongIterator iterator, LongConsumer action) {
		return new PeekIterator(iterator, action);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator  from a Java Iterator
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @return a peeked iterator
	 */
	public static LongIterator peek(Iterator<? extends Long> iterator, LongConsumer action) {
		return new PeekIterator(wrap(iterator), action);
	}
	
	/**
	 * Helper function to convert a Object Iterator into a Primitive Iterator
	 * @param iterator that should be converted to a unboxing iterator
	 * @return a primitive iterator
	 */
	public static LongIterator wrap(Iterator<? extends Long> iterator) {
		return iterator instanceof LongIterator ? (LongIterator)iterator : new IteratorWrapper(iterator);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped
	 * @return a Iterator that is wrapping a array.
	 */
	public static ArrayIterator wrap(long... a) {
		return wrap(a, 0, a.length);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped.
	 * @param start the index to be started from.
	 * @param end the index that should be ended.
	 * @return a Iterator that is wrapping a array.
	 */
	public static ArrayIterator wrap(long[] a, int start, int end) {
		return new ArrayIterator(a, start, end);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(long[] a, Iterator<? extends Long> i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(long[] a, Iterator<? extends Long> i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static int unwrap(long[] a, Iterator<? extends Long> i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.next().longValue();
		return index;
	}
	
	/**
	 * A Primitive iterator variant of the LongIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(long[] a, LongIterator i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * A Primitive iterator variant of the LongIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(long[] a, LongIterator i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * A Primitive iterator variant of the LongIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static int unwrap(long[] a, LongIterator i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.nextLong();
		return index;
	}
	
	/**
	 * A Function to convert a Primitive Iterator to a Object array.
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(Long[] a, LongIterator i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * A Function to convert a Primitive Iterator to a Object array.
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(Long[] a, LongIterator i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * A Function to convert a Primitive Iterator to a Object array.
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static int unwrap(Long[] a, LongIterator i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = Long.valueOf(i.nextLong());
		return index;
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @return the amount of elements that were added
	 */
	public static int pour(LongIterator iter, LongCollection c) {
		return pour(iter, c, Integer.MAX_VALUE);
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @param max the maximum amount of elements that should be collected
	 * @return the amount of elements that were added
	 */
	public static int pour(LongIterator iter, LongCollection c, int max) {
		if(max < 0) throw new IllegalStateException("Max is negative");
		int done = 0;
		for(;done<max && iter.hasNext();done++, c.add(iter.nextLong()));
		return done;
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @return iterator of the inputted iterators
	 */
	public static LongIterator concat(LongIterator... array) {
		return concat(array, 0, array.length);
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @param offset where to start within the array
	 * @param length the length of the array
	 * @return iterator of the inputted iterators
	 */
	public static LongIterator concat(LongIterator[] array, int offset, int length) {
		return new ConcatIterator(array, offset, length);
	}
	
	private static class IteratorWrapper implements LongIterator
	{
		Iterator<? extends Long> iter;
		
		public IteratorWrapper(Iterator<? extends Long> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public long nextLong() {
			return iter.next().longValue();
		}
		
		@Override
		@Deprecated
		public Long next() {
			return iter.next();
		}
	}
	
	private static class ConcatIterator implements LongIterator
	{
		LongIterator[] iters;
		int offset;
		int lastOffset = -1;
		int length;
		
		public ConcatIterator(LongIterator[] iters, int offset, int length) {
			this.iters = iters;
			this.offset = offset;
			this.length = length;
			find();
		}
		
		private void find() {
			for(;length != 0 && !iters[offset].hasNext();length--, offset++);
		}
		
		@Override
		public boolean hasNext() {
			return length > 0;
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			long result = iters[lastOffset = offset].nextLong();
			find();
			return result;
		}
		
		@Override
		public void remove() {
			if(lastOffset == -1) throw new IllegalStateException();
			iters[lastOffset].remove();
			lastOffset = -1;
		}
	}
	
	private static class ReverseBiIterator implements LongBidirectionalIterator {
		LongBidirectionalIterator it;
		
		ReverseBiIterator(LongBidirectionalIterator it) {
			this.it = it;
		}
		
		@Override
		public long nextLong() { return it.previousLong(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public long previousLong() { return it.nextLong(); }
		@Override
		public void remove() { it.remove(); }
	}
	
	private static class ReverseListIterator implements LongListIterator {
		LongListIterator it;
		
		ReverseListIterator(LongListIterator it) {
			this.it = it;
		}
	
		@Override
		public long nextLong() { return it.previousLong(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public long previousLong() { return it.nextLong(); }
		@Override
		public void remove() { it.remove(); }
		@Override
		public int nextIndex() { return it.previousIndex(); }
		@Override
		public int previousIndex() { return it.nextIndex(); }
		@Override
		public void set(long e) { it.set(e); }
		@Override
		public void add(long e) { it.add(e); }
	}
	
	private static class UnmodifiableListIterator implements LongListIterator
	{
		LongListIterator iter;
	
		UnmodifiableListIterator(LongListIterator iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public boolean hasPrevious() {
			return iter.hasPrevious();
		}
		
		@Override
		public int nextIndex() {
			return iter.nextIndex();
		}
		
		@Override
		public int previousIndex() {
			return iter.previousIndex();
		}
		
		@Override
		public void remove() { throw new UnsupportedOperationException(); }
		
		@Override
		public long previousLong() {
			return iter.previousLong();
		}
		
		@Override
		public long nextLong() {
			return iter.nextLong();
		}

		@Override
		public void set(long e) { throw new UnsupportedOperationException(); }
		
		@Override
		public void add(long e) { throw new UnsupportedOperationException(); }
	}
	
	private static class UnmodifiableBiIterator implements LongBidirectionalIterator
	{
		LongBidirectionalIterator iter;
		
		UnmodifiableBiIterator(LongBidirectionalIterator iter) {
			this.iter = iter;
		}
		
		@Override
		public long nextLong() {
			return iter.nextLong();
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public boolean hasPrevious() {
			return iter.hasPrevious();
		}
		
		@Override
		public long previousLong() {
			return iter.previousLong();
		}
	}
	
	private static class UnmodifiableIterator implements LongIterator
	{
		LongIterator iterator;
	
		UnmodifiableIterator(LongIterator iterator) {
			this.iterator = iterator;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public long nextLong() {
			return iterator.nextLong();
		}
	}
	
	private static class EmptyIterator implements LongListIterator
	{
		@Override
		public boolean hasNext() { return false; }
		@Override
		public long nextLong() { throw new NoSuchElementException(); }
		@Override
		public boolean hasPrevious() { return false; }
		@Override
		public long previousLong() { throw new NoSuchElementException(); }
		@Override
		public int nextIndex() { return 0; }
		@Override
		public int previousIndex() { return -1; }
		@Override
		public void remove() { throw new UnsupportedOperationException(); }
		@Override
		public void set(long e) { throw new UnsupportedOperationException(); }
		@Override
		public void add(long e) { throw new UnsupportedOperationException(); }
	}
	
	private static class ArrayIterator implements LongIterator
	{
		long[] a;
		int from;
		int to;
		
		ArrayIterator(long[] a, int from, int to) {
			this.a = a;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public boolean hasNext() {
			return from < to;
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			return a[from++];
		}
		
		@Override
		public int skip(int amount) {
			if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
			int left = Math.min(amount, to - from);
			from += left;
			return amount - left;
		}
	}
	
	private static class MappedIterator<T> implements ObjectIterator<T>
	{
		LongIterator iterator;
		LongFunction<T> mapper;
		
		MappedIterator(LongIterator iterator, LongFunction<T> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public T next() {
			return mapper.apply(iterator.nextLong());
		}
		
		@Override
		public int skip(int amount) {
			return iterator.skip(amount);
		}
	}
	
	private static class FlatMappedIterator<T, V extends Iterable<T>> implements ObjectIterator<T>
	{
		LongIterator iterator;
		Iterator<T> last = null;
		LongFunction<V> mapper;
		boolean foundNext = false;
		
		FlatMappedIterator(LongIterator iterator, LongFunction<V> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = mapper.apply(iterator.nextLong()).iterator();
			}
		}
		
		@Override
		public boolean hasNext() {
			compute();
			return last != null && last.hasNext();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			T result = last.next();
			foundNext = false;
			return result;
		}
	}
	
	private static class FlatMappedArrayIterator<T> implements ObjectIterator<T>
	{
		LongIterator iterator;
		Iterator<T> last = null;
		LongFunction<T[]> mapper;
		boolean foundNext = false;
		
		FlatMappedArrayIterator(LongIterator iterator, LongFunction<T[]> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = ObjectIterators.wrap(mapper.apply(iterator.nextLong()));
			}
		}
		
		@Override
		public boolean hasNext() {
			compute();
			return last != null && last.hasNext();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			T result = last.next();
			foundNext = false;
			return result;
		}
	}
	
	private static class RepeatingIterator implements LongIterator
	{
		final int repeats;
		int index = 0;
		LongIterator iter;
		LongCollection repeater = LongCollections.wrapper();
		
		public RepeatingIterator(LongIterator iter, int repeat) {
			this.iter = iter;
			this.repeats = repeat;
		}
		
		@Override
		public boolean hasNext() {
			if(iter.hasNext()) return true;
			if(index < repeats) {
				index++;
				iter = repeater.iterator();
				return iter.hasNext();
			}
			return false;
		}

		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			long value = iter.nextLong();
			if(index == 0) repeater.add(value);
			return value;
		}
	}
	
	private static class SortedIterator implements LongIterator
	{
		LongIterator iterator;
		LongComparator sorter;
		LongCollections.CollectionWrapper sortedElements = null;
		int index = 0;
		
		public SortedIterator(LongIterator iterator, LongComparator sorter) {
			this.iterator = iterator;
			this.sorter = sorter;
		}
		
		@Override
		public boolean hasNext() {
			if(sortedElements == null) {
				boolean hasNext = iterator.hasNext();
				if(hasNext) {
					sortedElements = LongCollections.wrapper();
					pour(iterator, sortedElements);
				}
				else sortedElements = LongCollections.wrapper();
				if(hasNext) sortedElements.unstableSort(sorter);
			}
			return index < sortedElements.size();
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			return sortedElements.getLong(index++);
		}
	}
	
	private static class DistinctIterator implements LongIterator
	{
		LongIterator iterator;
		LongCollection filtered = LongCollections.distinctWrapper();
		long lastFound;
		boolean foundNext = false;
		
		public DistinctIterator(LongIterator iterator) {
			this.iterator = iterator;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.nextLong();
				if(filtered.add(lastFound)) {
					foundNext = true;
					break;
				}
			}
		}
		
		@Override
		public boolean hasNext() {
			compute();
			return foundNext;
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class FilteredIterator implements LongIterator
	{
		LongIterator iterator;
		LongPredicate filter;
		long lastFound;
		boolean foundNext = false;
		
		public FilteredIterator(LongIterator iterator, LongPredicate filter) {
			this.iterator = iterator;
			this.filter = filter;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.nextLong();
				if(filter.test(lastFound)) {
					foundNext = true;
					break;
				}
			}
		}
		
		@Override
		public boolean hasNext() {
			compute();
			return foundNext;
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class LimitedIterator implements LongIterator
	{
		LongIterator iterator;
		long limit;
		
		public LimitedIterator(LongIterator iterator, long limit) {
			this.iterator = iterator;
			this.limit = limit;
		}
		
		@Override
		public boolean hasNext() {
			return limit > 0 && iterator.hasNext();
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			limit--;
			return iterator.nextLong();
		}
	}
	
	private static class PeekIterator implements LongIterator
	{
		LongIterator iterator;
		LongConsumer action;
		
		public PeekIterator(LongIterator iterator, LongConsumer action) {
			this.iterator = iterator;
			this.action = action;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public long nextLong() {
			if(!hasNext()) throw new NoSuchElementException();
			long result = iterator.nextLong();
			action.accept(result);
			return result;
		}
	}
}