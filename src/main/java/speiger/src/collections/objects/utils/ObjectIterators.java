package speiger.src.collections.objects.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.functions.function.UnaryOperator;
import speiger.src.collections.objects.lists.ObjectList;
import speiger.src.collections.objects.lists.ObjectArrayList;

import speiger.src.collections.objects.lists.ObjectListIterator;
import speiger.src.collections.objects.collections.ObjectBidirectionalIterator;
import speiger.src.collections.objects.collections.ObjectCollection;

/**
 * A Helper class for Iterators
 */
public class ObjectIterators
{
	/**
	 * Empty Iterator Reference
	 */
	private static final EmptyIterator<?> EMPTY = new EmptyIterator<>();
	
	/**
	 * Returns a Immutable EmptyIterator instance that is automatically casted.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return an empty iterator
	 */
	public static <T> EmptyIterator<T> empty() {
		return (EmptyIterator<T>)EMPTY;
	}
	
	/**
	 * Inverter function for Bidirectional Iterators
	 * @param it the iterator that should be inverted
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a Inverted Bidirectional Iterator. If it was inverted then it just gives back the original reference
	 */
	public static <T> ObjectBidirectionalIterator<T> invert(ObjectBidirectionalIterator<T> it) {
		return it instanceof ReverseBiIterator ? ((ReverseBiIterator<T>)it).it : new ReverseBiIterator<>(it);
	}
	
	/**
	 * Inverter function for List Iterators
	 * @param it the iterator that should be inverted
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a Inverted List Iterator. If it was inverted then it just gives back the original reference
	 */
	public static <T> ObjectListIterator<T> invert(ObjectListIterator<T> it) {
		return it instanceof ReverseListIterator ? ((ReverseListIterator<T>)it).it : new ReverseListIterator<>(it);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static <T> ObjectIterator<T> unmodifiable(ObjectIterator<T> iterator) {
		return iterator instanceof UnmodifiableIterator ? iterator : new UnmodifiableIterator<>(iterator);
	}
	
	/**
	 * Returns a Immutable Iterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a unmodifiable iterator wrapper. If the Iterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static <T> ObjectBidirectionalIterator<T> unmodifiable(ObjectBidirectionalIterator<T> iterator) {
		return iterator instanceof UnmodifiableBiIterator ? iterator : new UnmodifiableBiIterator<>(iterator);
	}
	
	/**
	 * Returns a Immutable ListIterator instance based on the instance given.
	 * @param iterator that should be made immutable/unmodifiable
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a unmodifiable listiterator wrapper. If the ListIterator already a unmodifiable wrapper then it just returns itself.
	 */
	public static <T> ObjectListIterator<T> unmodifiable(ObjectListIterator<T> iterator) {
		return iterator instanceof UnmodifiableListIterator ? iterator : new UnmodifiableListIterator<>(iterator);
	}
	
	/**
	 * A Helper function that maps a Java-Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <T, E> ObjectIterator<E> map(Iterator<? extends T> iterator, UnaryOperator<T, E> mapper) {
		return new MappedIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that maps a Iterator into a new Type.
	 * @param iterator that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <E> The return type.
	 * @return a iterator that is mapped to a new result
	 */
	public static <T, E> ObjectIterator<E> map(ObjectIterator<T> iterator, UnaryOperator<T, E> mapper) {
		return new MappedIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <T, E, V extends Iterable<E>> ObjectIterator<E> flatMap(Iterator<? extends T> iterator, UnaryOperator<T, V> mapper) {
		return new FlatMappedIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <T, E, V extends Iterable<E>> ObjectIterator<E> flatMap(ObjectIterator<T> iterator, UnaryOperator<T, V> mapper) {
		return new FlatMappedIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <T, E> ObjectIterator<E> arrayFlatMap(Iterator<? extends T> iterator, UnaryOperator<T, E[]> mapper) {
		return new FlatMappedArrayIterator<>(wrap(iterator), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterator into a new Type.
	 * @param iterator that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @param <E> The return type.
	 * @return a iterator that is flatMapped to a new result
	 */
	public static <T, E> ObjectIterator<E> arrayFlatMap(ObjectIterator<T> iterator, UnaryOperator<T, E[]> mapper) {
		return new FlatMappedArrayIterator<>(iterator, mapper);
	}
	
	/**
	 * A Helper function that filters out all desired elements from a Java-Iterator
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a filtered iterator
	 */
	public static <T> ObjectIterator<T> filter(Iterator<? extends T> iterator, Predicate<T> filter) {
		return new FilteredIterator<>(wrap(iterator), filter);
	}
	
	/**
	 * A Helper function that filters out all desired elements
	 * @param iterator that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a filtered iterator
	 */
	public static <T> ObjectIterator<T> filter(ObjectIterator<T> iterator, Predicate<T> filter) {
		return new FilteredIterator<>(iterator, filter);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements.
	 * @param iterator that should be distinct
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a distinct iterator
	 */
	public static <T> ObjectIterator<T> distinct(ObjectIterator<T> iterator) {
		return new DistinctIterator<>(iterator);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements from a Java Iterator.
	 * @param iterator that should be distinct
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a distinct iterator
	 */
	public static <T> ObjectIterator<T> distinct(Iterator<? extends T> iterator) {
		return new DistinctIterator<>(wrap(iterator));
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a repeating iterator
	 */
	public static <T> ObjectIterator<T> repeat(ObjectIterator<T> iterator, int repeats) {
		return new RepeatingIterator<>(iterator, repeats);
	}
	
	/**
	 * A Helper function that repeats the Iterator a specific amount of times from a Java Iterator
	 * @param iterator that should be repeated
	 * @param repeats the amount of times the iterator should be repeated
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a repeating iterator
	 */
	public static <T> ObjectIterator<T> repeat(Iterator<? extends T> iterator, int repeats) {
		return new RepeatingIterator<>(wrap(iterator), repeats);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a limited iterator
	 */
	public static <T> ObjectIterator<T> limit(ObjectIterator<T> iterator, long limit) {
		return new LimitedIterator<>(iterator, limit);
	}
	
	/**
	 * A Helper function that hard limits the Iterator to a specific size from a Java Iterator
	 * @param iterator that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a limited iterator
	 */
	public static <T> ObjectIterator<T> limit(Iterator<? extends T> iterator, long limit) {
		return new LimitedIterator<>(wrap(iterator), limit);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a new sorted iterator
	 */
	public static <T> ObjectIterator<T> sorted(ObjectIterator<T> iterator, Comparator<T> sorter) {
		return new SortedIterator<>(iterator, sorter);
	}
	
	/**
	 * A Helper function that sorts the Iterator beforehand from a Java Iterator.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterator that should be sorted.
	 * @param sorter the sorter of the iterator. Can be null.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a new sorted iterator
	 */
	public static <T> ObjectIterator<T> sorted(Iterator<? extends T> iterator, Comparator<T> sorter) {
		return new SortedIterator<>(wrap(iterator), sorter);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator.
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a peeked iterator
	 */
	public static <T> ObjectIterator<T> peek(ObjectIterator<T> iterator, Consumer<T> action) {
		return new PeekIterator<>(iterator, action);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterator  from a Java Iterator
	 * @param iterator that should be peeked at
	 * @param action callback that receives the value before the iterator returns it
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a peeked iterator
	 */
	public static <T> ObjectIterator<T> peek(Iterator<? extends T> iterator, Consumer<T> action) {
		return new PeekIterator<>(wrap(iterator), action);
	}
	
	/**
	 * Helper function to convert a Object Iterator into a Primitive Iterator
	 * @param iterator that should be converted to a unboxing iterator
	 * @param <T> the keyType of array that the operation should be applied
	 * @return a primitive iterator
	 */
	public static <T> ObjectIterator<T> wrap(Iterator<? extends T> iterator) {
		return iterator instanceof ObjectIterator ? (ObjectIterator<T>)iterator : new IteratorWrapper<>(iterator);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped
	 * @param <T> the keyType of array that the operation should be applied
	 * @return a Iterator that is wrapping a array.
	 */
	public static <T> ArrayIterator<T> wrap(T... a) {
		return wrap(a, 0, a.length);
	}
	
	/**
	 * Returns a Array Wrapping iterator
	 * @param a the array that should be wrapped.
	 * @param start the index to be started from.
	 * @param end the index that should be ended.
	 * @param <T> the keyType of array that the operation should be applied
	 * @return a Iterator that is wrapping a array.
	 */
	public static <T> ArrayIterator<T> wrap(T[] a, int start, int end) {
		return new ArrayIterator<>(a, start, end);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 */
	public static <T> int unwrap(T[] a, Iterator<? extends T> i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 */
	public static <T> int unwrap(T[] a, Iterator<? extends T> i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static <T> int unwrap(T[] a, Iterator<? extends T> i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.next();
		return index;
	}
	
	/**
	 * A Primitive iterator variant of the ObjectIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 */
	public static <T> int unwrap(T[] a, ObjectIterator<T> i) {
		return unwrap(a, i, 0, a.length);
	}
	
	/**
	 * A Primitive iterator variant of the ObjectIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 */
	public static <T> int unwrap(T[] a, ObjectIterator<T> i, int offset) {
		return unwrap(a, i, offset, a.length - offset);
	}
	
	/**
	 * A Primitive iterator variant of the ObjectIterators unwrap function
	 * Iterates over a iterator and inserts the values into the array and returns the amount that was inserted
	 * @param a where the elements should be inserted
	 * @param i the source iterator
	 * @param offset the array offset where the start should be
	 * @param max the maximum values that should be extracted from the source
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were inserted into the array.
	 * @throws IllegalStateException if max is smaller the 0 or if the maximum index is larger then the array
	 */
	public static <T> int unwrap(T[] a, ObjectIterator<T> i, int offset, int max) {
		if(max < 0) throw new IllegalStateException("The max size is smaller then 0");
		if(offset + max > a.length) throw new IllegalStateException("largest array index exceeds array size");
		int index = 0;
		for(;index<max && i.hasNext();index++) a[index+offset] = i.next();
		return index;
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a List
	 * @param iter the elements that should be poured into list.
	 * @param <T> the keyType of array that the operation should be applied
	 * @return A list of all elements of the Iterator
	 */
	public static <T> ObjectList<T> pour(ObjectIterator<T> iter) {
		return pour(iter, Integer.MAX_VALUE);
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a List
	 * @param iter the elements that should be poured into list.
	 * @param max the maximum amount of elements that should be collected
	 * @param <T> the keyType of array that the operation should be applied
	 * @return A list of all requested elements of the Iterator
	 */
	public static <T> ObjectList<T> pour(ObjectIterator<T> iter, int max) {
		ObjectArrayList<T> list = new ObjectArrayList<>();
		pour(iter, list, max);
		list.trim();
		return list;
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were added
	 */
	public static <T> int pour(ObjectIterator<T> iter, ObjectCollection<T> c) {
		return pour(iter, c, Integer.MAX_VALUE);
	}
	
	/**
	 * A Helper function to pours all elements of a Iterator into a Collection
	 * @param iter the elements that should be poured into list.
	 * @param c the collection where the elements should be poured into
	 * @param max the maximum amount of elements that should be collected
	 * @param <T> the keyType of array that the operation should be applied
	 * @return the amount of elements that were added
	 */
	public static <T> int pour(ObjectIterator<T> iter, ObjectCollection<T> c, int max) {
		if(max < 0) throw new IllegalStateException("Max is negative");
		int done = 0;
		for(;done<max && iter.hasNext();done++, c.add(iter.next()));
		return done;
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @param <T> the keyType of array that the operation should be applied
	 * @return iterator of the inputted iterators
	 */
	public static <T> ObjectIterator<T> concat(ObjectIterator<T>... array) {
		return concat(array, 0, array.length);
	}
	
	/**
	 * Helper Iterator that concats other iterators together
	 * @param array the Iterators that should be concatenated
	 * @param offset where to start within the array
	 * @param length the length of the array
	 * @param <T> the keyType of array that the operation should be applied
	 * @return iterator of the inputted iterators
	 */
	public static <T> ObjectIterator<T> concat(ObjectIterator<T>[] array, int offset, int length) {
		return new ConcatIterator<>(array, offset, length);
	}
	
	private static class IteratorWrapper<T> implements ObjectIterator<T>
	{
		Iterator<? extends T> iter;
		
		public IteratorWrapper(Iterator<? extends T> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		@Override
		public T next() {
			return iter.next();
		}
		
	}
	
	private static class ConcatIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T>[] iters;
		int offset;
		int lastOffset = -1;
		int length;
		
		public ConcatIterator(ObjectIterator<T>[] iters, int offset, int length) {
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
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			T result = iters[lastOffset = offset].next();
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
	
	private static class ReverseBiIterator<T> implements ObjectBidirectionalIterator<T> {
		ObjectBidirectionalIterator<T> it;
		
		ReverseBiIterator(ObjectBidirectionalIterator<T> it) {
			this.it = it;
		}
		
		@Override
		public T next() { return it.previous(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public T previous() { return it.next(); }
		@Override
		public void remove() { it.remove(); }
	}
	
	private static class ReverseListIterator<T> implements ObjectListIterator<T> {
		ObjectListIterator<T> it;
		
		ReverseListIterator(ObjectListIterator<T> it) {
			this.it = it;
		}
	
		@Override
		public T next() { return it.previous(); }
		@Override
		public boolean hasNext() { return it.hasPrevious(); }
		@Override
		public boolean hasPrevious() { return it.hasNext(); }
		@Override
		public T previous() { return it.next(); }
		@Override
		public void remove() { it.remove(); }
		@Override
		public int nextIndex() { return it.previousIndex(); }
		@Override
		public int previousIndex() { return it.nextIndex(); }
		@Override
		public void set(T e) { it.set(e); }
		@Override
		public void add(T e) { it.add(e); }
	}
	
	private static class UnmodifiableListIterator<T> implements ObjectListIterator<T>
	{
		ObjectListIterator<T> iter;
	
		UnmodifiableListIterator(ObjectListIterator<T> iter) {
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
		public T previous() {
			return iter.previous();
		}
		
		@Override
		public T next() {
			return iter.next();
		}

		@Override
		public void set(T e) { throw new UnsupportedOperationException(); }
		
		@Override
		public void add(T e) { throw new UnsupportedOperationException(); }
	}
	
	private static class UnmodifiableBiIterator<T> implements ObjectBidirectionalIterator<T>
	{
		ObjectBidirectionalIterator<T> iter;
		
		UnmodifiableBiIterator(ObjectBidirectionalIterator<T> iter) {
			this.iter = iter;
		}
		
		@Override
		public T next() {
			return iter.next();
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
		public T previous() {
			return iter.previous();
		}
	}
	
	private static class UnmodifiableIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
	
		UnmodifiableIterator(ObjectIterator<T> iterator) {
			this.iterator = iterator;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public T next() {
			return iterator.next();
		}
	}
	
	private static class EmptyIterator<T> implements ObjectListIterator<T>
	{
		@Override
		public boolean hasNext() { return false; }
		@Override
		public T next() { throw new NoSuchElementException(); }
		@Override
		public boolean hasPrevious() { return false; }
		@Override
		public T previous() { throw new NoSuchElementException(); }
		@Override
		public int nextIndex() { return 0; }
		@Override
		public int previousIndex() { return -1; }
		@Override
		public void remove() { throw new UnsupportedOperationException(); }
		@Override
		public void set(T e) { throw new UnsupportedOperationException(); }
		@Override
		public void add(T e) { throw new UnsupportedOperationException(); }
	}
	
	private static class ArrayIterator<T> implements ObjectIterator<T>
	{
		T[] a;
		int from;
		int to;
		
		ArrayIterator(T[] a, int from, int to) {
			this.a = a;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public boolean hasNext() {
			return from < to;
		}
		
		@Override
		public T next() {
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
	
	private static class MappedIterator<E, T> implements ObjectIterator<T>
	{
		ObjectIterator<E> iterator;
		UnaryOperator<E, T> mapper;
		
		MappedIterator(ObjectIterator<E> iterator, UnaryOperator<E, T> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public T next() {
			return mapper.apply(iterator.next());
		}
		
		@Override
		public int skip(int amount) {
			return iterator.skip(amount);
		}
	}
	
	private static class FlatMappedIterator<E, T, V extends Iterable<T>> implements ObjectIterator<T>
	{
		ObjectIterator<E> iterator;
		Iterator<T> last = null;
		UnaryOperator<E, V> mapper;
		boolean foundNext = false;
		
		FlatMappedIterator(ObjectIterator<E> iterator, UnaryOperator<E, V> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = mapper.apply(iterator.next()).iterator();
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
	
	private static class FlatMappedArrayIterator<E, T> implements ObjectIterator<T>
	{
		ObjectIterator<E> iterator;
		Iterator<T> last = null;
		UnaryOperator<E, T[]> mapper;
		boolean foundNext = false;
		
		FlatMappedArrayIterator(ObjectIterator<E> iterator, UnaryOperator<E, T[]> mapper) {
			this.iterator = iterator;
			this.mapper = mapper;
		}
		
		void compute() {
			if(foundNext) return;
			foundNext = true;
			while(iterator.hasNext()) {
				if(last != null && last.hasNext()) return;
				last = ObjectIterators.wrap(mapper.apply(iterator.next()));
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
	
	private static class RepeatingIterator<T> implements ObjectIterator<T>
	{
		final int repeats;
		int index = 0;
		ObjectIterator<T> iter;
		ObjectCollection<T> repeater = ObjectCollections.wrapper();
		
		public RepeatingIterator(ObjectIterator<T> iter, int repeat) {
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
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			T value = iter.next();
			if(index == 0) repeater.add(value);
			return value;
		}
	}
	
	private static class SortedIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
		Comparator<T> sorter;
		ObjectCollections.CollectionWrapper<T> sortedElements = null;
		int index = 0;
		
		public SortedIterator(ObjectIterator<T> iterator, Comparator<T> sorter) {
			this.iterator = iterator;
			this.sorter = sorter;
		}
		
		@Override
		public boolean hasNext() {
			if(sortedElements == null) {
				boolean hasNext = iterator.hasNext();
				if(hasNext) {
					sortedElements = ObjectCollections.wrapper();
					pour(iterator, sortedElements);
				}
				else sortedElements = ObjectCollections.wrapper();
				if(hasNext) sortedElements.unstableSort(sorter);
			}
			return index < sortedElements.size();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			return sortedElements.get(index++);
		}
	}
	
	private static class DistinctIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
		ObjectCollection<T> filtered = ObjectCollections.distinctWrapper();
		T lastFound;
		boolean foundNext = false;
		
		public DistinctIterator(ObjectIterator<T> iterator) {
			this.iterator = iterator;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.next();
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
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class FilteredIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
		Predicate<T> filter;
		T lastFound;
		boolean foundNext = false;
		
		public FilteredIterator(ObjectIterator<T> iterator, Predicate<T> filter) {
			this.iterator = iterator;
			this.filter = filter;
		}
		
		void compute() {
			if(foundNext) return;
			while(iterator.hasNext()) {
				lastFound = iterator.next();
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
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			foundNext = false;
			return lastFound;
		}
	}
	
	private static class LimitedIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
		long limit;
		
		public LimitedIterator(ObjectIterator<T> iterator, long limit) {
			this.iterator = iterator;
			this.limit = limit;
		}
		
		@Override
		public boolean hasNext() {
			return limit > 0 && iterator.hasNext();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			limit--;
			return iterator.next();
		}
	}
	
	private static class PeekIterator<T> implements ObjectIterator<T>
	{
		ObjectIterator<T> iterator;
		Consumer<T> action;
		
		public PeekIterator(ObjectIterator<T> iterator, Consumer<T> action) {
			this.iterator = iterator;
			this.action = action;
		}
		
		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			T result = iterator.next();
			action.accept(result);
			return result;
		}
	}
}