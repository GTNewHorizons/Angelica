package speiger.src.collections.floats.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface FloatBooleanUnaryOperator extends BiFunction<Float, Boolean, Boolean>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public boolean applyAsBoolean(float k, boolean v);
	
	@Override
	public default Boolean apply(Float k, Boolean v) { return Boolean.valueOf(applyAsBoolean(k.floatValue(), v.booleanValue())); }
}