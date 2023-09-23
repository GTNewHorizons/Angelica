package speiger.src.collections.longs.utils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import speiger.src.collections.longs.collections.LongIterable;
import speiger.src.collections.longs.collections.LongCollection;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.longs.functions.LongConsumer;
import speiger.src.collections.longs.functions.LongComparator;
import speiger.src.collections.longs.collections.LongIterator;
import speiger.src.collections.longs.functions.function.LongFunction;
import speiger.src.collections.utils.ISizeProvider;

/**
 * A Helper class for Iterables
 */
public class LongIterables
{
	/**
	 * A Helper function that maps a Java-Iterable into a new Type.
	 * @param iterable the iterable that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterable that is mapped to a new result
	 */
	public static <E> ObjectIterable<E> map(Iterable<? extends Long> iterable, LongFunction<E> mapper) {
		return new MappedIterable<>(wrap(iterable), mapper);
	}
	
	/**
	 * A Helper function that maps a Iterable into a new Type.
	 * @param iterable the iterable that should be mapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterable that is mapped to a new result
	 */
	public static <E> ObjectIterable<E> map(LongIterable iterable, LongFunction<E> mapper) {
		return new MappedIterable<>(iterable, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterable into a new Type.
	 * @param iterable the iterable that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterable that is flatMapped to a new result
	 */
	public static <E, V extends Iterable<E>> ObjectIterable<E> flatMap(Iterable<? extends Long> iterable, LongFunction<V> mapper) {
		return new FlatMappedIterable<>(wrap(iterable), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterable into a new Type.
	 * @param iterable the iterable that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a iterable that is flatMapped to a new result
	 */
	public static <E, V extends Iterable<E>> ObjectIterable<E> flatMap(LongIterable iterable, LongFunction<V> mapper) {
		return new FlatMappedIterable<>(iterable, mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Java-Iterable into a new Type.
	 * @param iterable the iterable that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterable that is flatMapped to a new result
	 */
	public static <E> ObjectIterable<E> arrayFlatMap(Iterable<? extends Long> iterable, LongFunction<E[]> mapper) {
		return new FlatMappedArrayIterable<>(wrap(iterable), mapper);
	}
	
	/**
	 * A Helper function that flatMaps a Iterable into a new Type.
	 * @param iterable the iterable that should be flatMapped
	 * @param mapper the function that decides what the result turns into.
	 * @param <E> The return type.
	 * @return a iterable that is flatMapped to a new result
	 */
	public static <E> ObjectIterable<E> arrayFlatMap(LongIterable iterable, LongFunction<E[]> mapper) {
		return new FlatMappedArrayIterable<>(iterable, mapper);
	}
	
	/**
	 * A Helper function that filters out all desired elements from a Java-Iterable
	 * @param iterable that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterable
	 */
	public static LongIterable filter(Iterable<? extends Long> iterable, LongPredicate filter) {
		return new FilteredIterable(wrap(iterable), filter);
	}
	
	/**
	 * A Helper function that filters out all desired elements
	 * @param iterable that should be filtered.
	 * @param filter the filter that decides that should be let through
	 * @return a filtered iterable
	 */
	public static LongIterable filter(LongIterable iterable, LongPredicate filter) {
		return new FilteredIterable(iterable, filter);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements.
	 * @param iterable that should be distinct
	 * @return a distinct iterable
	 */
	public static LongIterable distinct(LongIterable iterable) {
		return new DistinctIterable(iterable);
	}
	
	/**
	 * A Helper function that filters out all duplicated elements from a Java Iterable.
	 * @param iterable that should be distinct
	 * @return a distinct iterable
	 */
	public static LongIterable distinct(Iterable<? extends Long> iterable) {
		return new DistinctIterable(wrap(iterable));
	}
	
	/**
	 * A Helper function that repeats the Iterable a specific amount of times
	 * @param iterable that should be repeated
	 * @param repeats the amount of times the iterable should be repeated
	 * @return a repeating iterable
	 */
	public static LongIterable repeat(LongIterable iterable, int repeats) {
		return new RepeatingIterable(iterable, repeats);
	}
	
	/**
	 * A Helper function that repeats the Iterable a specific amount of times from a Java Iterable
	 * @param iterable that should be repeated
	 * @param repeats the amount of times the iterable should be repeated
	 * @return a repeating iterable
	 */
	public static LongIterable repeat(Iterable<? extends Long> iterable, int repeats) {
		return new RepeatingIterable(wrap(iterable), repeats);
	}
	
	/**
	 * A Helper function that hard limits the Iterable to a specific size
	 * @param iterable that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterable
	 */
	public static LongIterable limit(LongIterable iterable, long limit) {
		return new LimitedIterable(iterable, limit);
	}
	
	/**
	 * A Helper function that hard limits the Iterable to a specific size from a Java Iterable
	 * @param iterable that should be limited
	 * @param limit the amount of elements it should be limited to
	 * @return a limited iterable
	 */
	public static LongIterable limit(Iterable<? extends Long> iterable, long limit) {
		return new LimitedIterable(wrap(iterable), limit);
	}
	
	/**
	 * A Helper function that sorts the Iterable.
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterable that should be sorted
	 * @param sorter that sorts the iterable. Can be null.
	 * @return a sorted iterable.
	 */
	public static LongIterable sorted(LongIterable iterable, LongComparator sorter) {
		return new SortedIterable(iterable, sorter);
	}
	
	/**
	 * A Helper function that sorts the Iterable from a Java Iterable
	 * This operation is heavily hurting performance because it rebuilds the entire iterator and then sorts it.
	 * @param iterable that should be sorted
	 * @param sorter that sorts the iterable. Can be null.
	 * @return a sorted iterable.
	 */
	public static LongIterable sorted(Iterable<? extends Long> iterable, LongComparator sorter) {
		return new SortedIterable(wrap(iterable), sorter);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterable.
	 * @param iterable that should be peeked at
	 * @param action callback that receives the value before the iterable returns it
	 * @return a peeked iterable
	 */
	public static LongIterable peek(LongIterable iterable, LongConsumer action) {
		return new PeekIterable(iterable, action);
	}
	
	/**
	 * A Helper function that allows to preview the result of a Iterable from a Java Iterable
	 * @param iterable that should be peeked at
	 * @param action callback that receives the value before the iterable returns it
	 * @return a peeked iterable
	 */
	public static LongIterable peek(Iterable<? extends Long> iterable, LongConsumer action) {
		return new PeekIterable(wrap(iterable), action);
	}
	
	/**
	 * A Wrapper function that wraps a Java-Iterable into a Type Specific Iterable
	 * @param iterable that should be wrapped
	 * @return a type specific iterable
	 */
	public static LongIterable wrap(Iterable<? extends Long> iterable) {
		return new WrappedIterable(iterable);
	}
	
	private static class WrappedIterable implements LongIterable, ISizeProvider
	{
		Iterable<? extends Long> iterable;
		
		public WrappedIterable(Iterable<? extends Long> iterable) {
			this.iterable = iterable;
		}
		
		public LongIterator iterator() {
			return LongIterators.wrap(iterable.iterator());
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(iterable);
			return prov == null ? -1 : prov.size();
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			iterable.forEach(action);
		}
	}
	
	private static class MappedIterable<T> implements ObjectIterable<T>, ISizeProvider
	{
		LongIterable iterable;
		LongFunction<T> mapper;
		
		MappedIterable(LongIterable iterable, LongFunction<T> mapper) {
			this.iterable = iterable;
			this.mapper = mapper;
		}
		
		public ObjectIterator<T> iterator() {
			return LongIterators.map(iterable.iterator(), mapper);
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(this);
			return prov == null ? -1 : prov.size();
		}
		
		@Override
		public void forEach(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			iterable.forEach(E -> action.accept(mapper.apply(E)));
		}
	}
	
	private static class FlatMappedIterable<T, V extends Iterable<T>> implements ObjectIterable<T>
	{
		LongIterable iterable;
		LongFunction<V> mapper;
		
		FlatMappedIterable(LongIterable iterable, LongFunction<V> mapper) {
			this.iterable = iterable;
			this.mapper = mapper;
		}
		
		@Override
		public ObjectIterator<T> iterator() {
			return LongIterators.flatMap(iterable.iterator(), mapper);
		}
		
		@Override
		public void forEach(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			iterable.forEach(E -> mapper.apply(E).forEach(action));
		}
		
	}
	
	private static class FlatMappedArrayIterable<T> implements ObjectIterable<T>
	{
		LongIterable iterable;
		LongFunction<T[]> mapper;
		
		FlatMappedArrayIterable(LongIterable iterable, LongFunction<T[]> mapper) {
			this.iterable = iterable;
			this.mapper = mapper;
		}
		
		@Override
		public ObjectIterator<T> iterator() {
			return LongIterators.arrayFlatMap(iterable.iterator(), mapper);
		}
		
		@Override
		public void forEach(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			iterable.forEach(E -> {
				T[] array = mapper.apply(E);
				for(int i = 0,m=array.length;i<m;action.accept(array[i++]));
			});
		}
	}
	
	private static class RepeatingIterable implements LongIterable, ISizeProvider
	{
		LongIterable iterable;
		int repeats;
		
		public RepeatingIterable(LongIterable iterable, int repeats) {
			this.iterable = iterable;
			this.repeats = repeats;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.repeat(iterable.iterator(), repeats);
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(iterable);
			return prov == null ? -1 : prov.size() * (repeats+1);
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			LongCollection repeater = LongCollections.wrapper();
			iterable.forEach(action.andThen(repeater::add));
			for(int i = 0;i<repeats;i++)
				repeater.forEach(action);
		}
	}
	
	private static class FilteredIterable implements LongIterable
	{
		LongIterable iterable;
		LongPredicate filter;
		
		public FilteredIterable(LongIterable iterable, LongPredicate filter) {
			this.iterable = iterable;
			this.filter = filter;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.filter(iterable.iterator(), filter);
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			iterable.forEach(T -> { if(!filter.test(T)) action.accept(T); } );
		}
	}
	
	private static class LimitedIterable implements LongIterable, ISizeProvider
	{
		LongIterable iterable;
		long limit;
		
		public LimitedIterable(LongIterable iterable, long limit) {
			this.iterable = iterable;
			this.limit = limit;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.limit(iterable.iterator(), limit);
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(iterable);
			return prov == null ? -1 : (int)Math.min(prov.size(), limit);
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			AtomicLong counter = new AtomicLong();
			iterable.forEach(T -> {
				if(counter.get() >= limit) return;
				counter.incrementAndGet();
				action.accept(T);
			});
		}
	}
	
	private static class SortedIterable implements LongIterable, ISizeProvider
	{
		LongIterable iterable;
		LongComparator sorter;
		
		public SortedIterable(LongIterable iterable, LongComparator sorter) {
			this.iterable = iterable;
			this.sorter = sorter;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.sorted(iterable.iterator(), sorter);
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(iterable);
			return prov == null ? -1 : prov.size();
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			LongCollections.CollectionWrapper wrapper = LongCollections.wrapper();
			iterable.forEach(wrapper::add);
			wrapper.unstableSort(sorter);
			wrapper.forEach(action);
		}
	}
	
	private static class DistinctIterable implements LongIterable
	{
		LongIterable iterable;
		
		public DistinctIterable(LongIterable iterable) {
			this.iterable = iterable;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.distinct(iterable.iterator());
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			LongCollection filtered = LongCollections.distinctWrapper();
			iterable.forEach(T -> { if(filtered.add(T)) action.accept(T); });
		}
	}
	
	private static class PeekIterable implements LongIterable, ISizeProvider
	{
		LongIterable iterable;
		LongConsumer action;
		
		public PeekIterable(LongIterable iterable, LongConsumer action) {
			this.iterable = iterable;
			this.action = action;
		}
		
		@Override
		public LongIterator iterator() {
			return LongIterators.peek(iterable.iterator(), action);
		}
		
		@Override
		public int size() {
			ISizeProvider prov = ISizeProvider.of(iterable);
			return prov == null ? -1 : prov.size();
		}
		
		@Override
		public void forEach(LongConsumer action) {
			Objects.requireNonNull(action);
			iterable.forEach(this.action.andThen(action));
		}
	}
}