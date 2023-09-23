package speiger.src.collections.objects.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectCharUnaryOperator<T> extends BiFunction<T, Character, Character>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public char applyAsChar(T k, char v);
	
	@Override
	public default Character apply(T k, Character v) { return Character.valueOf(applyAsChar(k, v.charValue())); }
}