package speiger.src.collections.doubles.functions.consumer;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A Type Specific BiConsumer class to reduce boxing/unboxing and that fills the gaps that java has.
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface DoubleObjectConsumer<V> extends BiConsumer<Double, V>
{
	/**
	 * A Type Specific operation method to reduce boxing/unboxing
	 * Performs this operation on the given arguments.
	 *
	 * @param k the first input argument
	 * @param v the second input argument
	 */
	void accept(double k, V v);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced biconsumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default DoubleObjectConsumer<V> andThen(DoubleObjectConsumer<V> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(K, V);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Double k, V v) { accept(k.doubleValue(), v); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default DoubleObjectConsumer<V> andThen(BiConsumer<? super Double, ? super V> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(Double.valueOf(K), V);};
	}
}