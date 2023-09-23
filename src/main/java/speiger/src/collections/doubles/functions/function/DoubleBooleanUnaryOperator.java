package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface DoubleBooleanUnaryOperator extends BiFunction<Double, Boolean, Boolean>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public boolean applyAsBoolean(double k, boolean v);
	
	@Override
	public default Boolean apply(Double k, Boolean v) { return Boolean.valueOf(applyAsBoolean(k.doubleValue(), v.booleanValue())); }
}