package speiger.src.collections.longs.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface LongObjectUnaryOperator<V> extends BiFunction<Long, V, V>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public V apply(long k, V v);
	
	@Override
	public default V apply(Long k, V v) { return apply(k.longValue(), v); }
}