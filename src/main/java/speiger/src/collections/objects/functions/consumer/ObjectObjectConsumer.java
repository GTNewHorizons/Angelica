package speiger.src.collections.objects.functions.consumer;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A Type Specific BiConsumer class to reduce boxing/unboxing and that fills the gaps that java has.
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface ObjectObjectConsumer<T, V> extends BiConsumer<T, V>
{
	/**
	 * A Type Specific operation method to reduce boxing/unboxing
	 * Performs this operation on the given arguments.
	 *
	 * @param k the first input argument
	 * @param v the second input argument
	 */
	void accept(T k, V v);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced biconsumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default ObjectObjectConsumer<T, V> andThen(ObjectObjectConsumer<T, V> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(K, V);};
	}
	
}