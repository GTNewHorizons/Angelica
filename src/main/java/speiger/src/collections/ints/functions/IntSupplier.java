package speiger.src.collections.ints.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface IntSupplier extends java.util.function.IntSupplier
{
	/**
	 * @return the supplied value
	 */
	public int getAsInt();
}