package speiger.src.collections.objects.collections;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.Comparator;

import speiger.src.collections.objects.functions.function.UnaryOperator;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;
import speiger.src.collections.objects.functions.function.ObjectObjectUnaryOperator;
import speiger.src.collections.objects.lists.ObjectList;
import speiger.src.collections.objects.lists.ObjectArrayList;

import speiger.src.collections.objects.utils.ObjectArrays;
import speiger.src.collections.objects.utils.ObjectSplititerators;
import speiger.src.collections.objects.utils.ObjectIterables;
import speiger.src.collections.objects.utils.ObjectIterators;
import speiger.src.collections.utils.ISizeProvider;

/**
 * A Type-Specific {@link Iterable} that reduces (un)boxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectIterable<T> extends Iterable<T>
{
	/**
	 * Returns an iterator over elements of type {@code T}.
	 *
	 * @return an Iterator.
	 */
	@Override
	ObjectIterator<T> iterator();

	/**
	 * A Indexed forEach implementation that allows you to keep track of how many elements were already iterated over.
	 * @param action The action to be performed for each element
	 * @throws NullPointerException if the specified action is null
	 */
	public default void forEachIndexed(IntObjectConsumer<T> action) {
		Objects.requireNonNull(action);
		int index = 0;
		for(ObjectIterator<T> iter = iterator();iter.hasNext();action.accept(index++, iter.next()));
	}

	/**
	 * Helper function to reduce Lambda usage and allow for more method references, since these are faster/cleaner.
	 * @param input the object that should be included
	 * @param action The action to be performed for each element
	 * @param <E> the generic type of the Object
	 * @throws NullPointerException if the specified action is null
	 */
	default <E> void forEach(E input, ObjectObjectConsumer<E, T> action) {
		Objects.requireNonNull(action);
		iterator().forEachRemaining(input, action);
	}

	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default ObjectSplititerator<T> spliterator() { return ObjectSplititerators.createUnknownSplititerator(iterator(), 0); }

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the mapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 */
	default <E> ObjectIterable<E> map(UnaryOperator<T, E> mapper) {
		return ObjectIterables.map(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <V> The return type supplier.
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toArray optimizations.
	 */
	default <E, V extends Iterable<E>> ObjectIterable<E> flatMap(UnaryOperator<T, V> mapper) {
		return ObjectIterables.flatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to convert a Iterable to something else.
	 * @param mapper the flatMapping function
	 * @param <E> The return type.
	 * @return a new Iterable that returns the desired result
	 * @note does not support toArray optimizations.
	 */
	default <E> ObjectIterable<E> arrayflatMap(UnaryOperator<T, E[]> mapper) {
		return ObjectIterables.arrayFlatMap(this, mapper);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out unwanted elements
	 * @param filter the elements that should be kept.
	 * @return a Iterable that filtered out all unwanted elements
	 * @note does not support toArray optimizations.
	 */
	default ObjectIterable<T> filter(Predicate<T> filter) {
		return ObjectIterables.filter(this, filter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to filter out duplicated elements
	 * @return a Iterable that filtered out all duplicated elements
	 * @note does not support toArray optimizations.
	 */
	default ObjectIterable<T> distinct() {
		return ObjectIterables.distinct(this);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to repeat elements a desired amount of times
	 * @param repeats how many times the elements should be repeated
	 * @return a Iterable that is repeating multiple times
	 */
	default ObjectIterable<T> repeat(int repeats) {
		return ObjectIterables.repeat(this, repeats);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to limit the amount of elements
	 * @param limit the amount of elements it should be limited to
	 * @return a Iterable that is limited in length
	 */
	default ObjectIterable<T> limit(long limit) {
		return ObjectIterables.limit(this, limit);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to sort the elements
	 * @param sorter that sorts the elements.
	 * @return a Iterable that is sorted
	 */
	default ObjectIterable<T> sorted(Comparator<T> sorter) {
		return ObjectIterables.sorted(this, sorter);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to preview elements before they are iterated through
	 * @param action the action that should be applied
	 * @return a Peeked Iterable
	 */
	default ObjectIterable<T> peek(Consumer<T> action) {
		return ObjectIterables.peek(this, action);
	}

	/**
	 * A Helper function to reduce the usage of Streams and allows to collect all elements
	 * @param collection that the elements should be inserted to
	 * @param <E> the collection type
	 * @return the input with the desired elements
	 */
	default <E extends ObjectCollection<T>> E pour(E collection) {
		ObjectIterators.pour(iterator(), collection);
		return collection;
	}

	/**
	 * A Helper function that reduces the usage of streams and allows to collect all elements as a ArrayList
	 * @return a new ArrayList of all elements
	 */
	default ObjectList<T> pourAsList() {
		return pour(new ObjectArrayList<>());
	}


	/**
	 * A Helper function that reduces the usage of streams and allows to collect all elements as a Array
	 * @param action is the creator function of said Array to ensure type is kept.
	 * @return a new Array of all elements
	 */
	default <E> E[] toArray(IntFunction<E[]> action) {
		ISizeProvider prov = ISizeProvider.of(this);
		if(prov != null) {
			int size = prov.size();
			if(size >= 0) {
				E[] array = action.apply(size);
				ObjectIterators.unwrap(array, iterator());
				return array;
			}
		}
		return ObjectArrays.pour(iterator(), action);
	}
	/**
	 * Helper function to reduce stream usage that allows to filter for any matches.
	 * @param filter that should be applied
	 * @return true if any matches were found
	 */
	default boolean matchesAny(Predicate<T> filter) {
		Objects.requireNonNull(filter);
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			if(filter.test(iter.next())) return true;
		}
		return false;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for no matches.
	 * @param filter that should be applied
	 * @return true if no matches were found
	 */
	default boolean matchesNone(Predicate<T> filter) {
		Objects.requireNonNull(filter);
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			if(filter.test(iter.next())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for all matches.
	 * @param filter that should be applied
	 * @return true if all matches.
	 */
	default boolean matchesAll(Predicate<T> filter) {
		Objects.requireNonNull(filter);
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			if(!filter.test(iter.next())) return false;
		}
		return true;
	}

	/**
	 * Helper function to reduce stream usage that allows to filter for the first match.
	 * @param filter that should be applied
	 * @return the found value or the null equivalent variant.
	 */
	default T findFirst(Predicate<T> filter) {
		Objects.requireNonNull(filter);
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			T entry = iter.next();
			if(filter.test(entry)) return entry;
		}
		return null;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @param identity the start value
	 * @param <E> the keyType of elements maintained by this Collection
	 * @return the reduction result, returns identity if nothing was found
	 */
	default <E> E reduce(E identity, BiFunction<E, T, E> operator) {
		Objects.requireNonNull(operator);
		E state = identity;
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			state = operator.apply(state, iter.next());
		}
		return state;
	}

	/**
	 * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
	 * elements of this Iterable
	 * @param operator the operation that should be applied
	 * @return the reduction result, returns null value if nothing was found
	 */
	default T reduce(ObjectObjectUnaryOperator<T, T> operator) {
		Objects.requireNonNull(operator);
		T state = null;
		boolean empty = true;
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			if(empty) {
				empty = false;
				state = iter.next();
				continue;
			}
			state = operator.apply(state, iter.next());
		}
		return state;
	}

	/**
	 * Helper function to reduce stream usage that allows to count the valid elements.
	 * @param filter that should be applied
	 * @return the amount of Valid Elements
	 */
	default int count(Predicate<T> filter) {
		Objects.requireNonNull(filter);
		int result = 0;
		for(ObjectIterator<T> iter = iterator();iter.hasNext();) {
			if(filter.test(iter.next())) result++;
		}
		return result;
	}
}
