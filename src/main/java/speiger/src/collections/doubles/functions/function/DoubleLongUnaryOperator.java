package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface DoubleLongUnaryOperator extends BiFunction<Double, Long, Long>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public long applyAsLong(double k, long v);
	
	@Override
	public default Long apply(Double k, Long v) { return Long.valueOf(applyAsLong(k.doubleValue(), v.longValue())); }
}