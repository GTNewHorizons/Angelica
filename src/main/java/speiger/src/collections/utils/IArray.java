package speiger.src.collections.utils;

import java.util.RandomAccess;

/**
 * A Helper interface that allows to detect if the Underlying implementation is
 * using a Array. This allows to read methods through synchronization layers.
 * Also it implements {@link RandomAccess} and {@link ITrimmable}
 */
public interface IArray extends RandomAccess, ITrimmable
{
	/**
	 * Increases the capacity of this implementation instance, if necessary,
	 * to ensure that it can hold at least the number of elements specified by
	 * the minimum capacity argument.
	 *
	 * @param size the desired minimum capacity
	 */
	public void ensureCapacity(int size);
}
