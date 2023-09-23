package speiger.src.collections.longs.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface LongFloatUnaryOperator extends BiFunction<Long, Float, Float>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public float applyAsFloat(long k, float v);
	
	@Override
	public default Float apply(Long k, Float v) { return Float.valueOf(applyAsFloat(k.longValue(), v.floatValue())); }
}