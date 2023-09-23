package speiger.src.collections.doubles.functions;

import java.util.Objects;
import java.util.function.Consumer;
/**
 * Type-Specific Consumer interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
@FunctionalInterface
public interface DoubleConsumer extends Consumer<Double>, java.util.function.DoubleConsumer
{
	/**
	 * Type-Specific function to reduce (un)boxing. 
	 * Performs this operation on the given argument.
	 *
	 * @param t the input argument
	 */
	void accept(double t);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced consumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default DoubleConsumer andThen(DoubleConsumer after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(T);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Double t) { accept(t.doubleValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default DoubleConsumer andThen(Consumer<? super Double> after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(Double.valueOf(T));};
	}
	
	/** {@inheritDoc}
	  * <p>This default implementation delegates to the corresponding type-specific function.
	  * @deprecated Please use the corresponding type-specific function instead. 
	  */
	@Override
	@Deprecated
	default DoubleConsumer andThen(java.util.function.DoubleConsumer after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(T);};
	}
}