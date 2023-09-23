package speiger.src.collections.doubles.functions.consumer;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A Type Specific BiConsumer class to reduce boxing/unboxing and that fills the gaps that java has.
 */
public interface DoubleDoubleConsumer extends BiConsumer<Double, Double>
{
	/**
	 * A Type Specific operation method to reduce boxing/unboxing
	 * Performs this operation on the given arguments.
	 *
	 * @param k the first input argument
	 * @param v the second input argument
	 */
	void accept(double k, double v);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced biconsumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default DoubleDoubleConsumer andThen(DoubleDoubleConsumer after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(K, V);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Double k, Double v) { accept(k.doubleValue(), v.doubleValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default DoubleDoubleConsumer andThen(BiConsumer<? super Double, ? super Double> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(Double.valueOf(K), Double.valueOf(V));};
	}
}