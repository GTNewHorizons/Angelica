package speiger.src.collections.longs.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface LongByteUnaryOperator extends BiFunction<Long, Byte, Byte>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public byte applyAsByte(long k, byte v);
	
	@Override
	public default Byte apply(Long k, Byte v) { return Byte.valueOf(applyAsByte(k.longValue(), v.byteValue())); }
}