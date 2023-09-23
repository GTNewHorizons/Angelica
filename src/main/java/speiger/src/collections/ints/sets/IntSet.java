package speiger.src.collections.ints.sets;

import java.util.Set;

import speiger.src.collections.ints.collections.IntCollection;
import speiger.src.collections.ints.collections.IntIterator;
import speiger.src.collections.ints.collections.IntSplititerator;
import speiger.src.collections.ints.utils.IntSplititerators;


/**
 * A Type Specific Set class to reduce boxing/unboxing
 */
public interface IntSet extends Set<Integer>, IntCollection
{	
	@Override
	public IntIterator iterator();
	
	@Override
	public IntSet copy();
	
	/**
	 * A Type Specific remove function to reduce boxing/unboxing
	 * @param o the element that should be removed
	 * @return true if the element was removed
	 */
	public boolean remove(int o);
	
	@Override
	public default boolean remInt(int o) {
		return remove(o);
	}
	
	@Override
	@Deprecated
	public default boolean add(Integer e) {
		return IntCollection.super.add(e);
	}
	
	@Override
	@Deprecated
	public default boolean contains(Object o) {
		return IntCollection.super.contains(o);
	}
	
	@Override
	@Deprecated
	public default boolean remove(Object o) {
		return IntCollection.super.remove(o);
	}
	
	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default IntSplititerator spliterator() { return IntSplititerators.createSplititerator(this, 0); }
}