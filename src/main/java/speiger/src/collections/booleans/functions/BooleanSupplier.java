package speiger.src.collections.booleans.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface BooleanSupplier
{
	/**
	 * @return the supplied value
	 */
	public boolean getAsBoolean();
}