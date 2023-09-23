package speiger.src.collections.bytes.functions.consumer;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A Type Specific BiConsumer class to reduce boxing/unboxing and that fills the gaps that java has.
 */
public interface ByteDoubleConsumer extends BiConsumer<Byte, Double>
{
	/**
	 * A Type Specific operation method to reduce boxing/unboxing
	 * Performs this operation on the given arguments.
	 *
	 * @param k the first input argument
	 * @param v the second input argument
	 */
	void accept(byte k, double v);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced biconsumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default ByteDoubleConsumer andThen(ByteDoubleConsumer after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(K, V);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Byte k, Double v) { accept(k.byteValue(), v.doubleValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default ByteDoubleConsumer andThen(BiConsumer<? super Byte, ? super Double> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(Byte.valueOf(K), Double.valueOf(V));};
	}
}