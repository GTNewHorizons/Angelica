package speiger.src.collections.ints.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface IntDoubleUnaryOperator extends BiFunction<Integer, Double, Double>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public double applyAsDouble(int k, double v);
	
	@Override
	public default Double apply(Integer k, Double v) { return Double.valueOf(applyAsDouble(k.intValue(), v.doubleValue())); }
}