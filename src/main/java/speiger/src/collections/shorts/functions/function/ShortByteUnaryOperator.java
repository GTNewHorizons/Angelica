package speiger.src.collections.shorts.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface ShortByteUnaryOperator extends BiFunction<Short, Byte, Byte>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public byte applyAsByte(short k, byte v);
	
	@Override
	public default Byte apply(Short k, Byte v) { return Byte.valueOf(applyAsByte(k.shortValue(), v.byteValue())); }
}