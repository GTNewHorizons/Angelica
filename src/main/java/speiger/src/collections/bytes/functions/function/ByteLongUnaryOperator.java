package speiger.src.collections.bytes.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface ByteLongUnaryOperator extends BiFunction<Byte, Long, Long>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public long applyAsLong(byte k, long v);
	
	@Override
	public default Long apply(Byte k, Long v) { return Long.valueOf(applyAsLong(k.byteValue(), v.longValue())); }
}