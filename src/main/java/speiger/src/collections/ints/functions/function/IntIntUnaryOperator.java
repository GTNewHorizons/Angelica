package speiger.src.collections.ints.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */

public interface IntIntUnaryOperator extends BiFunction<Integer, Integer, Integer>, java.util.function.IntBinaryOperator
{
	@Override
	public default Integer apply(Integer k, Integer v) { return Integer.valueOf(applyAsInt(k.intValue(), v.intValue())); }
}