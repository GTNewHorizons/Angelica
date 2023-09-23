package speiger.src.collections.chars.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface CharSupplier
{
	/**
	 * @return the supplied value
	 */
	public char getAsInt();
}