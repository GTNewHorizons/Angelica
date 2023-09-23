package speiger.src.collections.booleans.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface BooleanDoubleUnaryOperator extends BiFunction<Boolean, Double, Double>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public double applyAsDouble(boolean k, double v);
	
	@Override
	public default Double apply(Boolean k, Double v) { return Double.valueOf(applyAsDouble(k.booleanValue(), v.doubleValue())); }
}