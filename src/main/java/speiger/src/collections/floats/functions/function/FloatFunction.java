package speiger.src.collections.floats.functions.function;


/**
 * A Type Specific Function interface that reduces boxing/unboxing and fills the gaps of interfaces that are missing.
 * @param <V> the keyType of elements maintained by this Collection
 */
@FunctionalInterface
public interface FloatFunction<V>
{
	/**
	 * Type Specific get function to reduce boxing/unboxing
	 * @param k the value that should be processed
	 * @return the result of the function
	 */
	public V apply(float k);
	
}