package speiger.src.collections.longs.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */

public interface LongLongUnaryOperator extends BiFunction<Long, Long, Long>, java.util.function.LongBinaryOperator
{
	@Override
	public default Long apply(Long k, Long v) { return Long.valueOf(applyAsLong(k.longValue(), v.longValue())); }
}