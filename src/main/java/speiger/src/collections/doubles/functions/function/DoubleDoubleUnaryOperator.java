package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */

public interface DoubleDoubleUnaryOperator extends BiFunction<Double, Double, Double>, java.util.function.DoubleBinaryOperator
{
	@Override
	public default Double apply(Double k, Double v) { return Double.valueOf(applyAsDouble(k.doubleValue(), v.doubleValue())); }
}