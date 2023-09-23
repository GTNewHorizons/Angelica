package speiger.src.collections.shorts.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface ShortLongUnaryOperator extends BiFunction<Short, Long, Long>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public long applyAsLong(short k, long v);
	
	@Override
	public default Long apply(Short k, Long v) { return Long.valueOf(applyAsLong(k.shortValue(), v.longValue())); }
}