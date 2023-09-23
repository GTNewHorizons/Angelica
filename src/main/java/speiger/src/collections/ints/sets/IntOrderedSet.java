package speiger.src.collections.ints.sets;

import speiger.src.collections.ints.collections.IntBidirectionalIterator;
import speiger.src.collections.ints.collections.IntSplititerator;
import speiger.src.collections.ints.utils.IntSplititerators;

/**
 * A Special Set Interface giving Access to some really usefull functions
 * The Idea behind this interface is to allow access to functions that give control to the Order of elements.
 * Since Linked implementations as examples can be reordered outside of the Insertion Order.
 * This interface provides basic access to such functions while also providing some Sorted/NaivgableSet implementations that still fit into here.
 * 
 */
public interface IntOrderedSet extends IntSet
{
	/**
	 * A customized add method that allows you to insert into the first index.
	 * @param o the element that should be inserted
	 * @return true if it was added
	 * @see java.util.Set#add(Object)
	 */
	public boolean addAndMoveToFirst(int o);
	/**
	 * A customized add method that allows you to insert into the last index.
	 * @param o the element that should be inserted
	 * @return true if it was added
	 * @see java.util.Set#add(Object)
	 */
	public boolean addAndMoveToLast(int o);
	
	/**
	 * A specific move method to move a given key to the first index.
	 * @param o that should be moved to the first index
	 * @return true if the value was moved.
	 * @note returns false if the value was not present in the first place
	 */
	public boolean moveToFirst(int o);
	/**
	 * A specific move method to move a given key to the last index.
	 * @param o that should be moved to the first last
	 * @return true if the value was moved.
	 * @note returns false if the value was not present in the first place
	 */
	public boolean moveToLast(int o);
	
	@Override
	public IntOrderedSet copy();
	
	@Override
	public IntBidirectionalIterator iterator();
	
	/**
	 * A type Specific Iterator starting from a given key
	 * @param fromElement the element the iterator should start from
	 * @return a iterator starting from the given element
	 * @throws java.util.NoSuchElementException if fromElement isn't found
	 */
	public IntBidirectionalIterator iterator(int fromElement);
	
	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default IntSplititerator spliterator() { return IntSplititerators.createSplititerator(this, 0); }
	
	/**
	 * A method to get the first element in the set
	 * @return first element in the set
	 */
	public int firstInt();
	/**
	 * A method to get and remove the first element in the set
	 * @return first element in the set
	 */
	public int pollFirstInt();
	/**
	 * A method to get the last element in the set
	 * @return last element in the set
	 */
	public int lastInt();
	/**
	 * A method to get and remove the last element in the set
	 * @return last element in the set
	 */
	public int pollLastInt();
	
}