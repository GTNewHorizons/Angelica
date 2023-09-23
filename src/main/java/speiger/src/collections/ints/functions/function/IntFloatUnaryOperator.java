package speiger.src.collections.ints.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface IntFloatUnaryOperator extends BiFunction<Integer, Float, Float>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public float applyAsFloat(int k, float v);
	
	@Override
	public default Float apply(Integer k, Float v) { return Float.valueOf(applyAsFloat(k.intValue(), v.floatValue())); }
}