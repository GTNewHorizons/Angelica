package speiger.src.collections.longs.functions;

import java.util.Objects;
import java.util.function.Consumer;
/**
 * Type-Specific Consumer interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
@FunctionalInterface
public interface LongConsumer extends Consumer<Long>, java.util.function.LongConsumer
{
	/**
	 * Type-Specific function to reduce (un)boxing. 
	 * Performs this operation on the given argument.
	 *
	 * @param t the input argument
	 */
	void accept(long t);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced consumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default LongConsumer andThen(LongConsumer after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(T);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Long t) { accept(t.longValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default LongConsumer andThen(Consumer<? super Long> after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(Long.valueOf(T));};
	}
	
	/** {@inheritDoc}
	  * <p>This default implementation delegates to the corresponding type-specific function.
	  * @deprecated Please use the corresponding type-specific function instead. 
	  */
	@Override
	@Deprecated
	default LongConsumer andThen(java.util.function.LongConsumer after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(T);};
	}
}