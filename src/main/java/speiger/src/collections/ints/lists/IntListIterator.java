package speiger.src.collections.ints.lists;

import java.util.ListIterator;

import speiger.src.collections.ints.collections.IntBidirectionalIterator;

/**
 * A Type Specific ListIterator that reduces boxing/unboxing
 */
public interface IntListIterator extends ListIterator<Integer>, IntBidirectionalIterator
{
	/**
	 * A Primitive set function to reduce (un)boxing
	 * @param e the element that should replace the last returned value
	 * @see ListIterator#set(Object)
	 */
	public void set(int e);
	
	/**
	 * A Primitive set function to reduce (un)boxing
	 * @param e the element that should be inserted before the last returned value
	 * @see ListIterator#set(Object)
	 */
	public void add(int e);
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default Integer previous() {
		return IntBidirectionalIterator.super.previous();
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default Integer next() {
		return IntBidirectionalIterator.super.next();
	}	
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default void set(Integer e) {
		set(e.intValue());
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public default void add(Integer e) {
		add(e.intValue());
	}
}