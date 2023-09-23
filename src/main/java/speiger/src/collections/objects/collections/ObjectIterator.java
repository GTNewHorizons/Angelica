package speiger.src.collections.objects.collections;

import java.util.Iterator;
import java.util.Objects;
import speiger.src.collections.objects.functions.consumer.ObjectObjectConsumer;

/**
 * A Type-Specific {@link Iterator} that reduces (un)boxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectIterator<T> extends Iterator<T>
{
	/**
	 * Helper function to reduce Lambda usage and allow for more method references, since these are faster/cleaner.
	 * @param input the object that should be included
	 * @param action The action to be performed for each element
	 * @param <E> the generic type of the Object
	 * @throws NullPointerException if the specified action is null
	 */
	default <E> void forEachRemaining(E input, ObjectObjectConsumer<E, T> action) {
		Objects.requireNonNull(action);
		while(hasNext()) { action.accept(input, next()); }
	}

	/**
	 * Skips the Given amount of elements if possible. A Optimization function to skip elements faster if the implementation allows it.
	 * @param amount the amount of elements that should be skipped
	 * @return the amount of elements that were skipped
	 */
	default int skip(int amount) {
		if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
		int i = 0;
		for(;i<amount && hasNext();next(), i++);
		return i;
	}
}
