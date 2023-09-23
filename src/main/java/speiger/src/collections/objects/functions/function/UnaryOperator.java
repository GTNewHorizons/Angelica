package speiger.src.collections.objects.functions.function;

import java.util.Objects;

/**
 * A Type Specific Function interface that reduces boxing/unboxing and fills the gaps of interfaces that are missing.
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
@FunctionalInterface
public interface UnaryOperator<T, V> extends java.util.function.Function<T, V>
{
	/**
	 * Type Specific get function to reduce boxing/unboxing
	 * @param k the value that should be processed
	 * @return the result of the function
	 */
	public V apply(T k);
	
	/**
	 * Creates a Default function that returns the input provided.
	 * @param <T> the keyType of elements maintained by this Collection
	 * @return a input returning function
	 */
	public static <T> UnaryOperator<T, T> identity() {
		return T -> T;
	}
	
	/**
	 * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     * 
     * @param <I> the keyType of elements maintained by this Collection
     * @param before the function that should be used first
     * @return a composed function with a different starting function.
	 */
	public default <I> UnaryOperator<I, V> compose(UnaryOperator<I, T> before) {
        Objects.requireNonNull(before);
		return T -> apply(before.apply(T));
	}
	
	/**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     * 
     * @param <I> the keyType of elements maintained by this Collection
     * @param after the function that should be used last
     * @return a composed function with a different starting function.
	 */
	public default <I> UnaryOperator<T, I> andThen(UnaryOperator<V, I> after) {
        Objects.requireNonNull(after);
		return T -> after.apply(apply(T));
	}
	
}