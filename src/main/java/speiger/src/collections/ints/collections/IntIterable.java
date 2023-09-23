package speiger.src.collections.ints.collections;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntPredicate;


import speiger.src.collections.ints.functions.IntConsumer;
import speiger.src.collections.ints.functions.IntComparator;
import speiger.src.collections.objects.collections.ObjectIterable;
import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.functions.consumer.IntIntConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectIntConsumer;
import speiger.src.collections.ints.functions.function.IntIntUnaryOperator;

import speiger.src.collections.ints.utils.IntArrays;
import speiger.src.collections.ints.utils.IntSplititerators;
import speiger.src.collections.ints.utils.IntIterables;
import speiger.src.collections.ints.utils.IntIterators;
import speiger.src.collections.utils.ISizeProvider;

/**
 * A Type-Specific {@link Iterable} that reduces (un)boxing
 */
public interface IntIterable extends Iterable<Integer>
{
	/**
	 * Returns an iterator over elements of type {@code T}.
	 *
	 * @return an Iterator.
	 */
	@Override
	IntIterator iterator();

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
	default void forEach(IntConsumer action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(action);
	}

	/** {@inheritDoc}
	* <p>This default implementation delegates to the corresponding type-specific function.
	* @deprecated Please use the corresponding type-specific function instead.
	*/
	@Deprecated
	@Override
	default void forEach(Consumer<? super Integer> action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(action);
	}

	/**
	 * A Indexed forEach implementation that allows you to keep track of how many elements were already iterated over.
	 * @param action The action to be performed for each element
	 * @throws NullPointerException if the specified action is null
	 */
	public default void forEachIndexed(IntIntConsumer action) {
		Objects.requireNonNull(action);
		int index = 0;
		for(IntIterator iter = iterator();iter.hasNext();action.accept(index++, iter.nextInt()));
	}

	/**
	 * Helper function to reduce Lambda usage and allow for more method references, since these are faster/cleaner.
	 * @param input the object that should be included
	 * @param action The action to be performed for each element
	 * @param <E> the generic type of the Object
	 * @throws NullPointerException if the specified action is null
	 */
	default <E> void forEach(E input, ObjectIntConsumer<E> action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(input, action);
	}

	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default IntSplititerator spliterator() { return IntSplititerators.createUnknownSplititerator(iterator(), 0); }

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the mapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 */
	default <E> ObjectIterable<E> map(IntFunction<E> mapper) {
		return IntIterables.map(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toIntArray optimizations.
	 */
	default <E, V extends Iterable<E>> ObjectIterable<E> flatMap(IntFunction<V> mapper) {
		return IntIterables.flatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toIntArray optimizations.
	 */
	default <E> ObjectIterable<E> arrayflatMap(IntFunction<E[]> mapper) {
		return IntIterables.arrayFlatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out unwanted elements
	 * @param filter the elements that should be kept.
	 * @return a Iterable that filtered out all unwanted elements
	 * @note does not support toIntArray optimizations.
	 */
	default IntIterable filter(IntPredicate filter) {
		return IntIterables.filter(this, filter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out duplicated elements
	 * @return a Iterable that filtered out all duplicated elements
	 * @note does not support toIntArray optimizations.
	 */
	default IntIterable distinct() {
		return IntIterables.distinct(this);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to repeat elements a desired amount of times
	 * @param repeats how many times the elements should be repeated
	 * @return a Iterable that is repeating multiple times
	 */
	default IntIterable repeat(int repeats) {
		return IntIterables.repeat(this, repeats);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to limit the amount of elements
	 * @param limit the amount of elements it should be limited to
	 * @return a Iterable that is limited in length
	 */
	default IntIterable limit(long limit) {
		return IntIterables.limit(this, limit);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to sort the elements
	 * @param sorter that sorts the elements.
	 * @return a Iterable that is sorted
	 */
	default IntIterable sorted(IntComparator sorter) {
		return IntIterables.sorted(this, sorter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to preview elements before they are iterated through
	 * @param action the action that should be applied
	 * @return a Peeked Iterable
	 */
	default IntIterable peek(IntConsumer action) {
		return IntIterables.peek(this, action);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to collect all elements
	 * @param collection that the elements should be inserted to
	 * @param <E> the collection type
	 * @return the input with the desired elements
	 */
	default <E extends IntCollection> E pour(E collection) {
		IntIterators.pour(iterator(), collection);
		return collection;
	}


	/**
	 * A Helper function that reduces the usage of streams and allows to collect all elements as a Array
	 * @return a new Array of all elements
	 */
	default int[] toIntArray() {
		ISizeProvider prov = ISizeProvider.of(this);
		if(prov != null) {
			int size = prov.size();
			if(size >= 0) {
				int[] array = new int[size];
				IntIterators.unwrap(array, iterator());
				return array;
			}
		}
		return IntArrays.pour(iterator());
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for any matches.
	 * @param filter that should be applied
	 * @return true if any matches were found
	 */
	default boolean matchesAny(IntPredicate filter) {
		Objects.requireNonNull(filter);
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextInt())) return true;
		}
		return false;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for no matches.
	 * @param filter that should be applied
	 * @return true if no matches were found
	 */
	default boolean matchesNone(IntPredicate filter) {
		Objects.requireNonNull(filter);
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextInt())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for all matches.
	 * @param filter that should be applied
	 * @return true if all matches.
	 */
	default boolean matchesAll(IntPredicate filter) {
		Objects.requireNonNull(filter);
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(!filter.test(iter.nextInt())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for the first match.
	 * @param filter that should be applied
	 * @return the found value or the null equivalent variant.
	 */
	default int findFirst(IntPredicate filter) {
		Objects.requireNonNull(filter);
		for(IntIterator iter = iterator();iter.hasNext();) {
			int entry = iter.nextInt();
			if(filter.test(entry)) return entry;
		}
		return 0;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @param identity the start value
	 * @return the reduction result, returns identity if nothing was found
	 */
	default int reduce(int identity, IntIntUnaryOperator operator) {
		Objects.requireNonNull(operator);
		int state = identity;
		for(IntIterator iter = iterator();iter.hasNext();) {
			state = operator.applyAsInt(state, iter.nextInt());
		}
		return state;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
	 * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @return the reduction result, returns null value if nothing was found
	 */
	default int reduce(IntIntUnaryOperator operator) {
		Objects.requireNonNull(operator);
		int state = 0;
		boolean empty = true;
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(empty) {
				empty = false;
				state = iter.nextInt();
				continue;
			}
			state = operator.applyAsInt(state, iter.nextInt());
		}
		return state;
	}

	/**
	 * Helper function to reduce stream usage that allows to count the valid elements.
	 * @param filter that should be applied
	 * @return the amount of Valid Elements
	 */
	default int count(IntPredicate filter) {
		Objects.requireNonNull(filter);
		int result = 0;
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(filter.test(iter.nextInt())) result++;
		}
		return result;
	}
}
