package speiger.src.collections.utils;

/**
 * Interface that allows to test for if a collection is trimmable.
 * This also allows that synchronization-wrappers are trimmable without extracting the original collection.
 */
public interface ITrimmable
{
	/**
	 * Trims the original collection down to the size of the current elements
	 * @return if the internal array has been trimmed.
	 */
	public default boolean trim() {
		return trim(0);
	}
	
	/**
	 * Trims the original collection down to the size of the current elements or the requested size depending which is bigger
	 * @param size the requested trim size.
	 * @return if the internal array has been trimmed.
	 */
	public boolean trim(int size);
	
	/**
	 * Trims the collection down to the original size and clears all present elements with it.
	 */
	public default void clearAndTrim() {
		clearAndTrim(0);
	}
	
	/**
	 * Trims the collection down to the requested size and clears all elements while doing so
	 * @param size the amount of elements that should be allowed
	 * @note this will enforce minimum size of the collection itself
	 */
	public void clearAndTrim(int size);
}
