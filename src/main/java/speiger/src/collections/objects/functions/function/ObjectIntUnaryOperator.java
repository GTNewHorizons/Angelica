package speiger.src.collections.objects.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectIntUnaryOperator<T> extends BiFunction<T, Integer, Integer>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public int applyAsInt(T k, int v);
	
	@Override
	public default Integer apply(T k, Integer v) { return Integer.valueOf(applyAsInt(k, v.intValue())); }
}