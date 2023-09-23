package speiger.src.collections.objects.collections;

/**
 * This is a basically a {@link java.util.ListIterator} without the index functions.
 * Allowing to have a simple Bidirectional Iterator without having to keep track of the Iteration index.
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectBidirectionalIterator<T> extends ObjectIterator<T>
{
	/**
	 * Returns true if the Iterator has a Previous element
	 * @return true if the Iterator has a Previous element
	 */
	public boolean hasPrevious();
	
	/**
	 * Returns the Previous element of the iterator.
	 * @return the Previous element of the iterator.
 	 * @throws java.util.NoSuchElementException if the iteration has no more elements
	 */
	public T previous();
	
	/**
	 * Reverses the Given amount of elements if possible. A Optimization function to reverse elements faster if the implementation allows it.
	 * @param amount the amount of elements that should be reversed
	 * @return the amount of elements that were reversed
	 */
	public default int back(int amount) {
		if(amount < 0) throw new IllegalStateException("Can't go forward");
		int i = 0;
		for(;i<amount && hasPrevious();previous(),i++);
		return i;
	}
}