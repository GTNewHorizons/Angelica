package speiger.src.collections.bytes.functions;

import java.util.Objects;
import java.util.function.Consumer;
/**
 * Type-Specific Consumer interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
@FunctionalInterface
public interface ByteConsumer extends Consumer<Byte>
{
	/**
	 * Type-Specific function to reduce (un)boxing. 
	 * Performs this operation on the given argument.
	 *
	 * @param t the input argument
	 */
	void accept(byte t);
	
	/**
	 * Type Specific sequencing method to reduce boxing/unboxing.
	 * @param after a operation that should be performed afterwards
	 * @return a sequenced consumer that does 2 operations
	 * @throws NullPointerException if after is null
	 */
	public default ByteConsumer andThen(ByteConsumer after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(T);};
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	default void accept(Byte t) { accept(t.byteValue()); }
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
 	 * @deprecated Please use the corresponding type-specific function instead. 
 	 */
	@Override
	@Deprecated
	default ByteConsumer andThen(Consumer<? super Byte> after) {
		Objects.requireNonNull(after);
		return T -> {accept(T); after.accept(Byte.valueOf(T));};
	}
}