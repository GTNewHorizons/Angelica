package speiger.src.collections.longs.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface LongIntUnaryOperator extends BiFunction<Long, Integer, Integer>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public int applyAsInt(long k, int v);
	
	@Override
	public default Integer apply(Long k, Integer v) { return Integer.valueOf(applyAsInt(k.longValue(), v.intValue())); }
}