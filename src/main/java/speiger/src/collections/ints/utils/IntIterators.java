package speiger.src.collections.ints.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.IntPredicate;

import speiger.src.collections.ints.collections.IntIterator;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.utils.ObjectIterators;
import speiger.src.collections.ints.functions.IntConsumer;
import speiger.src.collections.ints.functions.IntComparator;
import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.lists.IntListIterator;
import speiger.src.collections.ints.collections.IntBidirectionalIterator;
import speiger.src.collections.ints.collections.IntCollection;

/**
 * A Helper class for Iterators
 */
public class IntIterators
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
	public static IntBidirectionalIterator invert(IntBidirectionalIterator it) {
		return it instanceof ReverseBiIterator ? ((ReverseBiIterator)it).it : new ReverseBiIterator(it);
	}
	
	/**
	 * Inverter function for List Iterators
	 * @param it the iterator that should be inverted
	 * @return a Inverted List Iterator. If it was inverted then it just gives back the original reference
	 */
	public static IntListIterator invert(IntListIterator it) {
		return it instanceof ReverseListIterator ? ((ReverseListIterator)it).it : new ReverseListIterator(it);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static IntIterator unmodifiable(IntIterator iterator) {
		return iterator instanceof UnmodifiableIterator ? iterator : new UnmodifiableIterator(iterator);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static IntBidirectionalIterator unmodifiable(IntBidirectionalIterator iterator) {
		return iterator instanceof UnmodifiableBiIterator ? iterator : new UnmodifiableBiIterator(iterator);
	}
	
	/**
	 * Returns a Immutable ListIterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @return a unmodifiable listiterator wrapper. If the ListIterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static IntListIterator unmodifiable(IntListIterator iterator) {
		return iterator instanceof UnmodifiableListIterator ? iterator : new UnmodifiableListIterator(iterator);
	}
	
	/**
	 * A Helper function that maps a Java-Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <E> ObjectIterator<E> map(Iterator<? extends Integer> iterator, IntFunction<E> mapper) {
		return new MappedIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that maps a Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <E> ObjectIterator<E> map(IntIterator iterator, IntFunction<E> mapper) {
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
	public static <E, V extends Iterable<E>> ObjectIterator<E> flatMap(Iterator<? extends Integer> iterator, IntFunction<V> mapper) {
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
	public static <E, V extends Iterable<E>> ObjectIterator<E> flatMap(IntIterator iterator, IntFunction<V> mapper) {
		return new FlatMappedIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E> ObjectIterator<E> arrayFlatMap(Iterator<? extends Integer> iterator, IntFunction<E[]> mapper) {
		return new FlatMappedArrayIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <E> ObjectIterator<E> arrayFlatMap(IntIterator iterator, IntFunction<E[]> mapper) {
		return new FlatMappedArrayIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that filters out all desired elements from a Java-Iterator
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterator
	 */
	public static IntIterator filter(Iterator<? extends Integer> iterator, IntPredicate filter) {
		return new FilteredIterator(wrap(iterator), filter);
	}
	
	/**
	 * A Helper function that filters out all desired elements
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterator
	 */
	public static IntIterator filter(IntIterator iterator, IntPredicate filter) {
		return new FilteredIterator(iterator, filter);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements.
	 * @param iterator that should be distinct
	 * @return a distinct iterator
	 */
	public static IntIterator distinct(IntIterator iterator) {
		return new DistinctIterator(iterator);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements from a Java Iterator.
	 * @param iterator that should be distinct
	 * @return a distinct iterator
	 */
	public static IntIterator distinct(Iterator<? extends Integer> iterator) {
		return new DistinctIterator(wrap(iterator));
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @return a repeating iterator
	 */
	public static IntIterator repeat(IntIterator iterator, int repeats) {
		return new RepeatingIterator(iterator, repeats);
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times from a Java Iterator
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @return a repeating iterator
	 */
	public static IntIterator repeat(Iterator<? extends Integer> iterator, int repeats) {
		return new RepeatingIterator(wrap(iterator), repeats);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterator
	 */
	public static IntIterator limit(IntIterator iterator, long limit) {
		return new LimitedIterator(iterator, limit);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size from a Java Iterator
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterator
	 */
	public static IntIterator limit(Iterator<? extends Integer> iterator, long limit) {
		return new LimitedIterator(wrap(iterator), limit);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @return a new sorted iterator
	 */
	public static IntIterator sorted(IntIterator iterator, IntComparator sorter) {
		return new SortedIterator(iterator, sorter);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand from a Java Iterator.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @return a new sorted iterator
	 */
	public static IntIterator sorted(Iterator<? extends Integer> iterator, IntComparator sorter) {
		return new SortedIterator(wrap(iterator), sorter);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator.
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @return a peeked iterator
	 */
	public static IntIterator peek(IntIterator iterator, IntConsumer action) {
		return new PeekIterator(iterator, action);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator  from a Java Iterator
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @return a peeked iterator
	 */
	public static IntIterator peek(Iterator<? extends Integer> iterator, IntConsumer action) {
		return new PeekIterator(wrap(iterator), action);
	}
	
	/**
	 * Helper function to convert a Object Iterator into a Primitive Iterator
	 * @param iterator that should be converted to a unboxing iterator
	 * @return a primitive iterator
	 */
	public static IntIterator wrap(Iterator<? extends Integer> iterator) {
		return iterator instanceof IntIterator ? (IntIterator)iterator : new IteratorWrapper(iterator);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped
	 * @return a Iterator that is wrapping a array.
	 */
	public static ArrayIterator wrap(int... a) {
		return wrap(a, 0, a.length);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped.
	 * @param start the index to be started from.
	 * @param end the index that should be ended.
	 * @return a Iterator that is wrapping a array.
	 */
	public static ArrayIterator wrap(int[] a, int start, int end) {
		return new ArrayIterator(a, start, end);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(int[] a, Iterator<? extends Integer> i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(int[] a, Iterator<? extends Integer> i, int offset) {
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
	public static int unwrap(int[] a, Iterator<? extends Integer> i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.next().intValue();
		return index;
	}
	
	/**
	 * A Primitive iterator variant of the IntIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(int[] a, IntIterator i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * A Primitive iterator variant of the IntIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(int[] a, IntIterator i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * A Primitive iterator variant of the IntIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static int unwrap(int[] a, IntIterator i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.nextInt();
		return index;
	}
	
	/**
	 * A Function to convert a Primitive Iterator to a Object array.
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @return the amount of elements that were inserted into the array.
	 */
	public static int unwrap(Integer[] a, IntIterator i) {
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
	public static int unwrap(Integer[] a, IntIterator i, int offset) {
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
	public static int unwrap(Integer[] a, IntIterator i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = Integer.valueOf(i.nextInt());
		return index;
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @return the amount of elements that were added
	 */
	public static int pour(IntIterator iter, IntCollection c) {
		return pour(iter, c, Integer.MAX_VALUE);
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @param max the maximum amount of elements that should be collected
	 * @return the amount of elements that were added
	 */
	public static int pour(IntIterator iter, IntCollection c, int max) {
		if(max < 0) throw new IllegalStateException("Max is negative");
		int done = 0;
		for(;done<max && iter.hasNext();done++, c.add(iter.nextInt()));
		return done;
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @return iterator of the inputted iterators
	 */
	public static IntIterator concat(IntIterator... array) {
		return concat(array, 0, array.length);
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @param offset where to start within the array
	 * @param length the length of the array
	 * @return iterator of the inputted iterators
	 */
	public static IntIterator concat(IntIterator[] array, int offset, int length) {
		return new ConcatIterator(array, offset, length);
	}
	
	private static class IteratorWrapper implements IntIterator
	{
		Iterator<? extends Integer> iter;
		
		public IteratorWrapper(Iterator<? extends Integer> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public int nextInt() {
			return iter.next().intValue();
		}
		
		@Override
		@Deprecated
		public Integer next() {
			return iter.next();
		}
	}
	
	private static class ConcatIterator implements IntIterator
	{
		IntIterator[] iters;
		int offset;
		int lastOffset = -1;
		int length;
		
		public ConcatIterator(IntIterator[] iters, int offset, int length) {
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
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			int result = iters[lastOffset = offset].nextInt();
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
	
	private static class ReverseBiIterator implements IntBidirectionalIterator {
		IntBidirectionalIterator it;
		
		ReverseBiIterator(IntBidirectionalIterator it) {
			this.it = it;
		}
		
		@Override
		public int nextInt() { return it.previousInt(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public int previousInt() { return it.nextInt(); }
		@Override
		public void remove() { it.remove(); }
	}
	
	private static class ReverseListIterator implements IntListIterator {
		IntListIterator it;
		
		ReverseListIterator(IntListIterator it) {
			this.it = it;
		}
	
		@Override
		public int nextInt() { return it.previousInt(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public int previousInt() { return it.nextInt(); }
		@Override
		public void remove() { it.remove(); }
		@Override
		public int nextIndex() { return it.previousIndex(); }
		@Override
		public int previousIndex() { return it.nextIndex(); }
		@Override
		public void set(int e) { it.set(e); }
		@Override
		public void add(int e) { it.add(e); }
	}
	
	private static class UnmodifiableListIterator implements IntListIterator
	{
		IntListIterator iter;
	
		UnmodifiableListIterator(IntListIterator iter) {
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
		public int previousInt() {
			return iter.previousInt();
		}
		
		@Override
		public int nextInt() {
			return iter.nextInt();
		}

		@Override
		public void set(int e) { throw new UnsupportedOperationException(); }
		
		@Override
		public void add(int e) { throw new UnsupportedOperationException(); }
	}
	
	private static class UnmodifiableBiIterator implements IntBidirectionalIterator
	{
		IntBidirectionalIterator iter;
		
		UnmodifiableBiIterator(IntBidirectionalIterator iter) {
			this.iter = iter;
		}
		
		@Override
		public int nextInt() {
			return iter.nextInt();
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
		public int previousInt() {
			return iter.previousInt();
		}
	}
	
	private static class UnmodifiableIterator implements IntIterator
	{
		IntIterator iterator;
	
		UnmodifiableIterator(IntIterator iterator) {
			this.iterator = iterator;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public int nextInt() {
			return iterator.nextInt();
		}
	}
	
	private static class EmptyIterator implements IntListIterator
	{
		@Override
		public boolean hasNext() { return false; }
		@Override
		public int nextInt() { throw new NoSuchElementException(); }
		@Override
		public boolean hasPrevious() { return false; }
		@Override
		public int previousInt() { throw new NoSuchElementException(); }
		@Override
		public int nextIndex() { return 0; }
		@Override
		public int previousIndex() { return -1; }
		@Override
		public void remove() { throw new UnsupportedOperationException(); }
		@Override
		public void set(int e) { throw new UnsupportedOperationException(); }
		@Override
		public void add(int e) { throw new UnsupportedOperationException(); }
	}
	
	private static class ArrayIterator implements IntIterator
	{
		int[] a;
		int from;
		int to;
		
		ArrayIterator(int[] a, int from, int to) {
			this.a = a;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public boolean hasNext() {
			return from < to;
		}
		
		@Override
		public int nextInt() {
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
		IntIterator iterator;
		IntFunction<T> mapper;
		
		MappedIterator(IntIterator iterator, IntFunction<T> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public T next() {
			return mapper.apply(iterator.nextInt());
		}
		
		@Override
		public int skip(int amount) {
			return iterator.skip(amount);
		}
	}
	
	private static class FlatMappedIterator<T, V extends Iterable<T>> implements ObjectIterator<T>
	{
		IntIterator iterator;
		Iterator<T> last = null;
		IntFunction<V> mapper;
		boolean foundNext = false;
		
		FlatMappedIterator(IntIterator iterator, IntFunction<V> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = mapper.apply(iterator.nextInt()).iterator();
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
		IntIterator iterator;
		Iterator<T> last = null;
		IntFunction<T[]> mapper;
		boolean foundNext = false;
		
		FlatMappedArrayIterator(IntIterator iterator, IntFunction<T[]> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = ObjectIterators.wrap(mapper.apply(iterator.nextInt()));
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
	
	private static class RepeatingIterator implements IntIterator
	{
		final int repeats;
		int index = 0;
		IntIterator iter;
		IntCollection repeater = IntCollections.wrapper();
		
		public RepeatingIterator(IntIterator iter, int repeat) {
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
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			int value = iter.nextInt();
			if(index == 0) repeater.add(value);
			return value;
		}
	}
	
	private static class SortedIterator implements IntIterator
	{
		IntIterator iterator;
		IntComparator sorter;
		IntCollections.CollectionWrapper sortedElements = null;
		int index = 0;
		
		public SortedIterator(IntIterator iterator, IntComparator sorter) {
			this.iterator = iterator;
			this.sorter = sorter;
		}
		
		@Override
		public boolean hasNext() {
			if(sortedElements == null) {
				boolean hasNext = iterator.hasNext();
				if(hasNext) {
					sortedElements = IntCollections.wrapper();
					pour(iterator, sortedElements);
				}
				else sortedElements = IntCollections.wrapper();
				if(hasNext) sortedElements.unstableSort(sorter);
			}
			return index < sortedElements.size();
		}
		
		@Override
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			return sortedElements.getInt(index++);
		}
	}
	
	private static class DistinctIterator implements IntIterator
	{
		IntIterator iterator;
		IntCollection filtered = IntCollections.distinctWrapper();
		int lastFound;
		boolean foundNext = false;
		
		public DistinctIterator(IntIterator iterator) {
			this.iterator = iterator;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.nextInt();
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
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class FilteredIterator implements IntIterator
	{
		IntIterator iterator;
		IntPredicate filter;
		int lastFound;
		boolean foundNext = false;
		
		public FilteredIterator(IntIterator iterator, IntPredicate filter) {
			this.iterator = iterator;
			this.filter = filter;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.nextInt();
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
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class LimitedIterator implements IntIterator
	{
		IntIterator iterator;
		long limit;
		
		public LimitedIterator(IntIterator iterator, long limit) {
			this.iterator = iterator;
			this.limit = limit;
		}
		
		@Override
		public boolean hasNext() {
			return limit > 0 && iterator.hasNext();
		}
		
		@Override
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			limit--;
			return iterator.nextInt();
		}
	}
	
	private static class PeekIterator implements IntIterator
	{
		IntIterator iterator;
		IntConsumer action;
		
		public PeekIterator(IntIterator iterator, IntConsumer action) {
			this.iterator = iterator;
			this.action = action;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public int nextInt() {
			if(!hasNext()) throw new NoSuchElementException();
			int result = iterator.nextInt();
			action.accept(result);
			return result;
		}
	}
}