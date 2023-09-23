package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface DoubleCharUnaryOperator extends BiFunction<Double, Character, Character>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public char applyAsChar(double k, char v);
	
	@Override
	public default Character apply(Double k, Character v) { return Character.valueOf(applyAsChar(k.doubleValue(), v.charValue())); }
}