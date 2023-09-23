package speiger.src.collections.booleans.functions.function;

import java.util.Objects;

/**
 * A Type Specific Function interface that reduces boxing/unboxing and fills the gaps of interfaces that are missing.
 */
@FunctionalInterface
public interface BooleanPredicate
{
	/**
	 * Type Specific get function to reduce boxing/unboxing
	 * @param k the value that should be processed
	 * @return the result of the function
	 */
	public boolean test(boolean k);
	
	/**
	 * Creates a Default function that returns the input provided.
	 * @return a input returning function
	 */
	public static BooleanPredicate identity() {
		return T -> T;
	}
	
	/**
	 * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     * 
     * @param before the function that should be used first
     * @return a composed function with a different starting function.
	 */
	public default BooleanPredicate compose(BooleanPredicate before) {
        Objects.requireNonNull(before);
		return T -> test(before.test(T));
	}
	
	/**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     * 
     * @param after the function that should be used last
     * @return a composed function with a different starting function.
	 */
	public default BooleanPredicate andThen(BooleanPredicate after) {
        Objects.requireNonNull(after);
		return T -> after.test(test(T));
	}
	
	/**
	 * Creates a Always true function that may be useful if you don't need to process information or just want a default.
	 * @return a default returning function
	 */
	public static BooleanPredicate alwaysTrue() {
		return T -> true;
	}
	
	/**
	 * Creates a Always false function that may be useful if you don't need to process information or just want a default.
	 * @return a default returning function
	 */
	public static BooleanPredicate alwaysFalse() {
		return T -> false;
	}
	
	/**
	 * A Type specific and-function helper function that reduces boxing/unboxing
	 * @param other the other function that should be merged with.
	 * @return a function that compares values in a and comparason
	 */
	public default BooleanPredicate andType(BooleanPredicate other) {
		Objects.requireNonNull(other);
		return T -> test(T) && other.test(T);
	}
	
	/**
	 * A type specific inverter function
	 * @return the same function but inverts the result
	 */
	public default BooleanPredicate negate() {
		return T -> !test(T);
	}
	
	/**
	 * A Type specific or-function helper function that reduces boxing/unboxing
	 * @param other the other function that should be merged with.
	 * @return a function that compares values in a or comparason
	 */
	public default BooleanPredicate orType(BooleanPredicate other) {
		Objects.requireNonNull(other);
		return T -> test(T) || other.test(T);
	}
	
}