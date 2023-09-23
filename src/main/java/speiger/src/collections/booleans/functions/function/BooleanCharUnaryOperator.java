package speiger.src.collections.booleans.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface BooleanCharUnaryOperator extends BiFunction<Boolean, Character, Character>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public char applyAsChar(boolean k, char v);
	
	@Override
	public default Character apply(Boolean k, Character v) { return Character.valueOf(applyAsChar(k.booleanValue(), v.charValue())); }
}