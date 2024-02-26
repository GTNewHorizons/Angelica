package com.gtnewhorizons.angelica.models.fapi;

/**
 * Represents a function that accepts a boolean-valued argument and produces a result.
 *
 * <p>This is the {@code boolean}-consuming primitive specialization for {@link java.util.function.Function}.
 */
@FunctionalInterface
public interface BooleanFunction<R> {
	/**
	 * Applies this function to the given argument.
	 *
	 * @param value the function argument
	 * @return the function result
	 */
	R apply(boolean value);
}
