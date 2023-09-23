package speiger.src.collections.ints.collections;

import speiger.src.collections.objects.collections.ObjectBidirectionalIterator;
/**
 * A Type-Specific {@link ObjectBidirectionalIterator} to reduce (un)boxing
 */
public interface IntBidirectionalIterator extends IntIterator, ObjectBidirectionalIterator<Integer>
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
	public int previousInt();
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default Integer previous() {
		return Integer.valueOf(previousInt());
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	default int skip(int amount)
	{
		return IntIterator.super.skip(amount);
	}
	
	/**
	 * Reverses the Given amount of elements if possible. A Optimization function to reverse elements faster if the implementation allows it.
	 * @param amount the amount of elements that should be reversed
	 * @return the amount of elements that were reversed
	 */
	public default int back(int amount) {
		if(amount < 0) throw new IllegalStateException("Can't go forward");
		int i = 0;
		for(;i<amount && hasPrevious();previousInt(),i++);
		return i;
	}
}