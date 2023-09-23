package speiger.src.collections.floats.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface FloatByteUnaryOperator extends BiFunction<Float, Byte, Byte>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public byte applyAsByte(float k, byte v);
	
	@Override
	public default Byte apply(Float k, Byte v) { return Byte.valueOf(applyAsByte(k.floatValue(), v.byteValue())); }
}