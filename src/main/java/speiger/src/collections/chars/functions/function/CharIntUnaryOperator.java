package speiger.src.collections.chars.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface CharIntUnaryOperator extends BiFunction<Character, Integer, Integer>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public int applyAsInt(char k, int v);
	
	@Override
	public default Integer apply(Character k, Integer v) { return Integer.valueOf(applyAsInt(k.charValue(), v.intValue())); }
}