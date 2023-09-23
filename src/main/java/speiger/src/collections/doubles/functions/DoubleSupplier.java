package speiger.src.collections.doubles.functions;

/**
 * Type-Specific Supplier interface that reduces (un)boxing and allows to merge other consumer types into this interface
 */
public interface DoubleSupplier extends java.util.function.DoubleSupplier
{
	/**
	 * @return the supplied value
	 */
	public double getAsDouble();
}