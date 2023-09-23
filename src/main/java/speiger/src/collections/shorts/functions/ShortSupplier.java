package speiger.src.collections.shorts.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface ShortSupplier
{
	/**
	 * @return the supplied value
	 */
	public short getAsInt();
}