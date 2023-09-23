package speiger.src.collections.longs.collections;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongPredicate;


import speiger.src.collections.longs.functions.LongConsumer;
import speiger.src.collections.longs.functions.LongComparator;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.longs.functions.function.LongFunction;
import speiger.src.collections.ints.functions.consumer.IntLongConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectLongConsumer;
import speiger.src.collections.longs.functions.function.LongLongUnaryOperator;

import speiger.src.collections.longs.utils.LongArrays;
import speiger.src.collections.longs.utils.LongSplititerators;
import speiger.src.collections.longs.utils.LongIterables;
import speiger.src.collections.longs.utils.LongIterators;
import speiger.src.collections.utils.ISizeProvider;

/**
 * A Type-Specific {@link Iterable} that reduces (un)boxing
 */
public interface LongIterable extends Iterable<Long>
{
	/**
	 * Returns an iterator over elements of type {@code T}.
	 *
	 * @return an Iterator.
	 */
	@Override
	LongIterator iterator();

	/**
	 * A Type Specific foreach function that reduces (un)boxing
	 *
	 * @implSpec
	 * <p>The default implementation behaves as if:
	 * <pre>{@code
	 *	iterator().forEachRemaining(action);
	 * }</pre>
	 *
	 * @param action The action to be performed for each element
	 * @throws NullPointerException if the specified action is null
	 * @see Iterable#forEach(Consumer)
	 */
	default void forEach(LongConsumer action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(action);
	}

	/** {@inheritDoc}
	* <p>This default implementation delegates to the corresponding type-specific function.
	* @deprecated Please use the corresponding type-specific function instead.
	*/
	@Deprecated
	@Override
	default void forEach(Consumer<? super Long> action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(action);
	}

	/**
	 * A Indexed forEach implementation that allows you to keep track of how many elements were already iterated over.
	 * @param action The action to be performed for each element
	 * @throws NullPointerException if the specified action is null
	 */
	public default void forEachIndexed(IntLongConsumer action) {
		Objects.requireNonNull(action);
		int index = 0;
		for(LongIterator iter = iterator();iter.hasNext();action.accept(index++, iter.nextLong()));
	}

	/**
	 * Helper function to reduce Lambda usage and allow for more method references, since these are faster/cleaner.
	 * @param input the object that should be included
	 * @param action The action to be performed for each element
	 * @param <E> the generic type of the Object
	 * @throws NullPointerException if the specified action is null
	 */
	default <E> void forEach(E input, ObjectLongConsumer<E> action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(input, action);
	}

	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default LongSplititerator spliterator() { return LongSplititerators.createUnknownSplititerator(iterator(), 0); }

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the mapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 */
	default <E> ObjectIterable<E> map(LongFunction<E> mapper) {
		return LongIterables.map(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toLongArray optimizations.
	 */
	default <E, V extends Iterable<E>> ObjectIterable<E> flatMap(LongFunction<V> mapper) {
		return LongIterables.flatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toLongArray optimizations.
	 */
	default <E> ObjectIterable<E> arrayflatMap(LongFunction<E[]> mapper) {
		return LongIterables.arrayFlatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out unwanted elements
	 * @param filter the elements that should be kept.
	 * @return a Iterable that filtered out all unwanted elements
	 * @note does not support toLongArray optimizations.
	 */
	default LongIterable filter(LongPredicate filter) {
		return LongIterables.filter(this, filter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out duplicated elements
	 * @return a Iterable that filtered out all duplicated elements
	 * @note does not support toLongArray optimizations.
	 */
	default LongIterable distinct() {
		return LongIterables.distinct(this);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to repeat elements a desired amount of times
	 * @param repeats how many times the elements should be repeated
	 * @return a Iterable that is repeating multiple times
	 */
	default LongIterable repeat(int repeats) {
		return LongIterables.repeat(this, repeats);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to limit the amount of elements
	 * @param limit the amount of elements it should be limited to
	 * @return a Iterable that is limited in length
	 */
	default LongIterable limit(long limit) {
		return LongIterables.limit(this, limit);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to sort the elements
	 * @param sorter that sorts the elements.
	 * @return a Iterable that is sorted
	 */
	default LongIterable sorted(LongComparator sorter) {
		return LongIterables.sorted(this, sorter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to preview elements before they are iterated through
	 * @param action the action that should be applied
	 * @return a Peeked Iterable
	 */
	default LongIterable peek(LongConsumer action) {
		return LongIterables.peek(this, action);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to collect all elements
	 * @param collection that the elements should be inserted to
	 * @param <E> the collection type
	 * @return the input with the desired elements
	 */
	default <E extends LongCollection> E pour(E collection) {
		LongIterators.pour(iterator(), collection);
		return collection;
	}


	/**
	 * A Helper function that reduces the usage of streams and allows to collect all elements as a Array
	 * @return a new Array of all elements
	 */
	default long[] toLongArray() {
		ISizeProvider prov = ISizeProvider.of(this);
		if(prov != null) {
			int size = prov.size();
			if(size >= 0) {
				long[] array = new long[size];
				LongIterators.unwrap(array, iterator());
				return array;
			}
		}
		return LongArrays.pour(iterator());
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for any matches.
	 * @param filter that should be applied
	 * @return true if any matches were found
	 */
	default boolean matchesAny(LongPredicate filter) {
		Objects.requireNonNull(filter);
		for(LongIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextLong())) return true;
		}
		return false;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for no matches.
	 * @param filter that should be applied
	 * @return true if no matches were found
	 */
	default boolean matchesNone(LongPredicate filter) {
		Objects.requireNonNull(filter);
		for(LongIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextLong())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for all matches.
	 * @param filter that should be applied
	 * @return true if all matches.
	 */
	default boolean matchesAll(LongPredicate filter) {
		Objects.requireNonNull(filter);
		for(LongIterator iter = iterator();iter.hasNext();) {
			if(!filter.test(iter.nextLong())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for the first match.
	 * @param filter that should be applied
	 * @return the found value or the null equivalent variant.
	 */
	default long findFirst(LongPredicate filter) {
		Objects.requireNonNull(filter);
		for(LongIterator iter = iterator();iter.hasNext();) {
			long entry = iter.nextLong();
			if(filter.test(entry)) return entry;
		}
		return 0L;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @param identity the start value
	 * @return the reduction result, returns identity if nothing was found
	 */
	default long reduce(long identity, LongLongUnaryOperator operator) {
		Objects.requireNonNull(operator);
		long state = identity;
		for(LongIterator iter = iterator();iter.hasNext();) {
			state = operator.applyAsLong(state, iter.nextLong());
		}
		return state;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
	 * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @return the reduction result, returns null value if nothing was found
	 */
	default long reduce(LongLongUnaryOperator operator) {
		Objects.requireNonNull(operator);
		long state = 0L;
		boolean empty = true;
		for(LongIterator iter = iterator();iter.hasNext();) {
			if(empty) {
				empty = false;
				state = iter.nextLong();
				continue;
			}
			state = operator.applyAsLong(state, iter.nextLong());
		}
		return state;
	}

	/**
	 * Helper function to reduce stream usage that allows to count the valid elements.
	 * @param filter that should be applied
	 * @return the amount of Valid Elements
	 */
	default int count(LongPredicate filter) {
		Objects.requireNonNull(filter);
		int result = 0;
		for(LongIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextLong())) result++;
		}
		return result;
	}
}
