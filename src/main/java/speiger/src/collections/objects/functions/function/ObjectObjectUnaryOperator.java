package speiger.src.collections.objects.functions.function;

import java.util.function.BiFunction;

/**
 * A Type Specific Unary Operator to support Compute/Merge functions with type specific methods to reduce boxing/unboxing
 * @param <T> the keyType of elements maintained by this Collection
 * @param <V> the keyType of elements maintained by this Collection
 */
public interface ObjectObjectUnaryOperator<T, V> extends BiFunction<T, V, V>
{

}