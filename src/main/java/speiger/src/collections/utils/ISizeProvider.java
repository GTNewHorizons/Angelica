package speiger.src.collections.utils;

import java.util.Collection;

/**
 * @author Speiger
 * 
 * This Interface is a Helper class to allow transfer the information through Iterators, without forcing a Implementation of a size method through it.
 * This is mainly used to optimize the toArray function.
 *
 */
public interface ISizeProvider
{
	/** @return the size of the implementing Collection */
	public int size();
	
	/**
	 * Gets a SizeProvider given the Iterable. May return null if it isn't a Collection or a SizeProvider.
	 * @param iter the Iterable that you want the size of.
	 * @return a SizeProvider if it is one or if it is a JavaCollection
	 */
	public static ISizeProvider of(Iterable<?> iter) {
		if(iter instanceof ISizeProvider) return (ISizeProvider)iter;
		if(iter instanceof Collection) return new CollectionSize((Collection<?>)iter);
		return null;
	}
	
	/**
	 * Collection implementation of the SizeProvider
	 */
	static class CollectionSize implements ISizeProvider
	{
		Collection<?> collection;
		
		public CollectionSize(Collection<?> collection)
		{
			this.collection = collection;
		}
		
		@Override
		public int size()
		{
			return collection.size();
		}
	}
}
