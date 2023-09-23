package speiger.src.collections.ints.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface IntLongUnaryOperator extends BiFunction<Integer, Long, Long>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public long applyAsLong(int k, long v);
	
	@Override
	public default Long apply(Integer k, Long v) { return Long.valueOf(applyAsLong(k.intValue(), v.longValue())); }
}