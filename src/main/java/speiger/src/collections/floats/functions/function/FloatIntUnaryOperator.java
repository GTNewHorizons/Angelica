package speiger.src.collections.floats.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface FloatIntUnaryOperator extends BiFunction<Float, Integer, Integer>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public int applyAsInt(float k, int v);
	
	@Override
	public default Integer apply(Float k, Integer v) { return Integer.valueOf(applyAsInt(k.floatValue(), v.intValue())); }
}