package speiger.src.collections.chars.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface CharBooleanUnaryOperator extends BiFunction<Character, Boolean, Boolean>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public boolean applyAsBoolean(char k, boolean v);
	
	@Override
	public default Boolean apply(Character k, Boolean v) { return Boolean.valueOf(applyAsBoolean(k.charValue(), v.booleanValue())); }
}