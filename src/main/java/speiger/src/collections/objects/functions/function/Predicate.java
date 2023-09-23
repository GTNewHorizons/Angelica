package speiger.src.collections.objects.functions.function;

import java.util.Objects;

/**
 * A Type Specific Function interface that reduces boxing/unboxing and fills the gaps of interfaces that are missing.
 * @param <T> the keyType of elements maintained by this Collection
 */
@FunctionalInterface
public interface Predicate<T> extends java.util.function.Predicate<T>
{
	/**
	 * Type Specific get function to reduce boxing/unboxing
	 * @param k the value that should be processed
	 * @return the result of the function
	 */
	public boolean test(T k);
	
	/**
	 * Creates a Always true function that may be useful if you don't need to process information or just want a default.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a default returning function
	 */
	public static <T> Predicate<T> alwaysTrue() {
		return T -> true;
	}
	
	/**
	 * Creates a Always false function that may be useful if you don't need to process information or just want a default.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a default returning function
	 */
	public static <T> Predicate<T> alwaysFalse() {
		return T -> false;
	}
	
	/**
	 * A Type specific and-function helper function that reduces boxing/unboxing
	 * @param other the other function that should be merged with.
	 * @return a function that compares values in a and comparason
	 */
	public default Predicate<T> andType(Predicate<T> other) {
		Objects.requireNonNull(other);
		return T -> test(T) && other.test(T);
	}
	
	@Override
	@Deprecated
	public default Predicate<T> and(java.util.function.Predicate<? super T> other) {
		Objects.requireNonNull(other);
		return T -> test(T) && other.test(T);
	}
	
	@Override
	public default Predicate<T> negate() {
		return T -> !test(T);
	}
	
	/**
	 * A Type specific or-function helper function that reduces boxing/unboxing
	 * @param other the other function that should be merged with.
	 * @return a function that compares values in a or comparason
	 */
	public default Predicate<T> orType(Predicate<T> other) {
		Objects.requireNonNull(other);
		return T -> test(T) || other.test(T);
	}
	
	@Override
	@Deprecated
	public default Predicate<T> or(java.util.function.Predicate<? super T> other) {
		Objects.requireNonNull(other);
		return T -> test(T) || other.test(T);
	}
}