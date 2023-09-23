package speiger.src.collections.objects.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectByteUnaryOperator<T> extends BiFunction<T, Byte, Byte>
{
	/**
	 * A Type Specifc apply method to reduce boxing/unboxing.
	 * Applies this function to the given arguments.
	 *
	 * @param k the first function argument
	 * @param v the second function argument
	 * @return the function result
	 */
	public byte applyAsByte(T k, byte v);
	
	@Override
	public default Byte apply(T k, Byte v) { return Byte.valueOf(applyAsByte(k, v.byteValue())); }
}