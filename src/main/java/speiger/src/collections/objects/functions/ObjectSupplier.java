package speiger.src.collections.objects.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectSupplier<T> extends java.util.function.Supplier<T>
{
	/**
	 * @return the supplied value
	 */
	public T get();
}