package speiger.src.collections.longs.lists;

import java.util.ListIterator;

import speiger.src.collections.longs.collections.LongBidirectionalIterator;

/**
 * A Type Specific ListIterator that reduces boxing/unboxing
 */
public interface LongListIterator extends ListIterator<Long>, LongBidirectionalIterator
{
	/**
	 * A Primitive set function to reduce (un)boxing
	 * @param e the element that should replace the last returned value
	 * @see ListIterator#set(Object)
	 */
	public void set(long e);
	
	/**
	 * A Primitive set function to reduce (un)boxing
	 * @param e the element that should be inserted before the last returned value
	 * @see ListIterator#set(Object)
	 */
	public void add(long e);
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default Long previous() {
		return LongBidirectionalIterator.super.previous();
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default Long next() {
		return LongBidirectionalIterator.super.next();
	}	
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default void set(Long e) {
		set(e.longValue());
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default void add(Long e) {
		add(e.longValue());
	}
}