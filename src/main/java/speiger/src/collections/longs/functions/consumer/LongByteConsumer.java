package speiger.src.collections.longs.functions.consumer;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A Type Specific BiConsumer class to reduce boxing/unboxing and that fills the gaps that java has.
 */
public interface LongByteConsumer extends BiConsumer<Long, Byte>
{
	/**
	 * A Type Specific operation method to reduce boxing/unboxing
	 * Performs this operation on the given arguments.
	 *
	 * @param k the first input argument
	 * @param v the second input argument
	 */
	void accept(long k, byte v);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced biconsumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default LongByteConsumer andThen(LongByteConsumer after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(K, V);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Long k, Byte v) { accept(k.longValue(), v.byteValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default LongByteConsumer andThen(BiConsumer<? super Long, ? super Byte> after) {
		Objects.requireNonNull(after);
		return (K, V) -> {accept(K, V); after.accept(Long.valueOf(K), Byte.valueOf(V));};
	}
}