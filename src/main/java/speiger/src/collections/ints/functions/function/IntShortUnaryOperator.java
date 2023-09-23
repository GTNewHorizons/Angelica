package speiger.src.collections.ints.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface IntShortUnaryOperator extends BiFunction<Integer, Short, Short>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public short applyAsShort(int k, short v);
	
	@Override
	public default Short apply(Integer k, Short v) { return Short.valueOf(applyAsShort(k.intValue(), v.shortValue())); }
}