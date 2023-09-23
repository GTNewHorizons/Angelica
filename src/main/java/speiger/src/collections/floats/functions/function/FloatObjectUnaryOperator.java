package speiger.src.collections.floats.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface FloatObjectUnaryOperator<V> extends BiFunction<Float, V, V>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public V apply(float k, V v);
	
	@Override
	public default V apply(Float k, V v) { return apply(k.floatValue(), v); }
}