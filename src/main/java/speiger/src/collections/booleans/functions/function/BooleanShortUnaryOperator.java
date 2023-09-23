package speiger.src.collections.booleans.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface BooleanShortUnaryOperator extends BiFunction<Boolean, Short, Short>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public short applyAsShort(boolean k, short v);
	
	@Override
	public default Short apply(Boolean k, Short v) { return Short.valueOf(applyAsShort(k.booleanValue(), v.shortValue())); }
}