package speiger.src.collections.doubles.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 */
public interface DoubleByteUnaryOperator extends BiFunction<Double, Byte, Byte>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public byte applyAsByte(double k, byte v);
	
	@Override
	public default Byte apply(Double k, Byte v) { return Byte.valueOf(applyAsByte(k.doubleValue(), v.byteValue())); }
}