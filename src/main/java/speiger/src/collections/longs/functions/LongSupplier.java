package speiger.src.collections.longs.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface LongSupplier extends java.util.function.LongSupplier
{
	/**
	 * @return the supplied value
	 */
	public long getAsLong();
}