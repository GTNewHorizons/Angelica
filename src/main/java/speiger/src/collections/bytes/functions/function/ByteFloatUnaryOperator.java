package speiger.src.collections.bytes.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface ByteFloatUnaryOperator extends BiFunction<Byte, Float, Float>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public float applyAsFloat(byte k, float v);
	
	@Override
	public default Float apply(Byte k, Float v) { return Float.valueOf(applyAsFloat(k.byteValue(), v.floatValue())); }
}