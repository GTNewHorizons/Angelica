package speiger.src.collections.doubles.functions.function;


/**
 * A Type Specific Function interface that reduces boxing/unboxing and fills the gaps of interfaces that are missing.
 */
@FunctionalInterface
public interface Double2FloatFunction
{
	/**
	 * Type Specific get function to reduce boxing/unboxing
	 * @param k the value that should be processed
	 * @return the result of the function
	 */
	public float applyAsFloat(double k);
	
}