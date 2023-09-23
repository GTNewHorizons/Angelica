package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface DoubleIntUnaryOperator extends BiFunction<Double, Integer, Integer>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public int applyAsInt(double k, int v);
	
	@Override
	public default Integer apply(Double k, Integer v) { return Integer.valueOf(applyAsInt(k.doubleValue(), v.intValue())); }
}