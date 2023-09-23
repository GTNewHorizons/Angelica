package speiger.src.collections.objects.collections;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import speiger.src.collections.objects.utils.ObjectSplititerators;
import speiger.src.collections.objects.utils.ObjectCollections;
import speiger.src.collections.utils.ISizeProvider;
import speiger.src.collections.utils.SanityChecks;

/**
 * A Type-Specific {@link Collection} that reduces (un)boxing
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectCollection<T> extends Collection<T>, ObjectIterable<T>, ISizeProvider
{
	/**
	 * A Type-Specific addAll function to reduce (un)boxing
	 * @param c the collection of elements that should be added
	 * @return true if elements were added into the collection
	 */
	public boolean addAll(ObjectCollection<T> c);
	
	/**
	 * A Type-Specific Array based addAll method to reduce the amount of Wrapping
	 * @param e the elements that should be added
	 * @return if the collection was modified
	 */
	public default boolean addAll(T... e) { return addAll(e, 0, e.length); }
	
	/**
	 * A Type-Specific Array based addAll method to reduce the amount of Wrapping
	 * @param e the elements that should be added
	 * @param length how many elements of the array should be added
	 * @return if the collection was modified
	 */
	public default boolean addAll(T[] e, int length) { return addAll(e, 0, length); }
	
	/**
	 * A Type-Specific Array based addAll method to reduce the amount of Wrapping
	 * @param e the elements that should be added
	 * @param offset where to start within the array
	 * @param length how many elements of the array should be added
	 * @return if the collection was modified
	 */
	public default boolean addAll(T[] e, int offset, int length) {
		if(length <= 0) return false;
		SanityChecks.checkArrayCapacity(e.length, offset, length);
		boolean added = false;
		for(int i = 0;i<length;i++) {
			if(add(e[offset+i])) added = true;
		}
		return added;
	}
	
	/**
 	 * A Type-Specific containsAll function to reduce (un)boxing
 	 * @param c the collection of elements that should be tested for
 	 * @return true if all the element is found in the collection
 	 */
	public boolean containsAll(ObjectCollection<T> c);
	
	/**
	 * A Type-Specific containsAny function to reduce (un)boxing
 	 * @param c the collection of elements that should be tested for
	 * @return true if any element was found
	 */
	public boolean containsAny(ObjectCollection<T> c);
	
	/**
	 * Returns true if any element of the Collection is found in the provided collection.
	 * A Small Optimization function to find out of any element is present when comparing collections and not all of them.
 	 * @param c the collection of elements that should be tested for
	 * @return true if any element was found.
	 */
	public boolean containsAny(Collection<?> c);
	
	/**
	 * A Type-Specific removeAll function that reduces (un)boxing.
	 * @param c the collection of elements that should be removed
	 * @return true if any element was removed
	 * @see Collection#removeAll(Collection)
	 */
	public boolean removeAll(ObjectCollection<T> c);
	
	/**
	 * A Type-Specific removeAll function that reduces (un)boxing.
	 * It also notifies the remover of which exact element is going to be removed.
	 * @param c the collection of elements that should be removed
	 * @param r elements that got removed
	 * @return true if any element was removed
	 * @see Collection#removeAll(Collection)
	 */
	public boolean removeAll(ObjectCollection<T> c, Consumer<T> r);
	
	/**
 	 * A Type-Specific retainAll function that reduces (un)boxing.
	 * @param c the collection of elements that should be kept
 	 * @return true if any element was removed
 	 * @see Collection#retainAll(Collection)
 	 */
	public boolean retainAll(ObjectCollection<T> c);
	
	/**
 	 * A Type-Specific retainAll function that reduces (un)boxing.
	 * It also notifies the remover of which exact element is going to be removed.
	 * @param c the collection of elements that should be kept
	 * @param r elements that got removed
 	 * @return true if any element was removed
 	 * @see Collection#retainAll(Collection)
 	 */
	public boolean retainAll(ObjectCollection<T> c, Consumer<T> r);
	
	/**
	 * A Helper function to reduce the usage of Streams and allows to collect all elements
	 * @param collection that the elements should be inserted to
	 * @param <E> the collection type
	 * @return the input with the desired elements
	 */
	default <E extends ObjectCollection<T>> E pour(E collection) {
		collection.addAll(this);
		return collection;
	}
	
	/**
	 * A Function that does a shallow clone of the Collection itself.
	 * This function is more optimized then a copy constructor since the Collection does not have to be unsorted/resorted.
	 * It can be compared to Cloneable but with less exception risk
	 * @return a Shallow Copy of the collection
	 * @note Wrappers and view collections will not support this feature
	 */
	public ObjectCollection<T> copy();
	
	/**
	 * A Helper function that simplifies the process of creating a new Array.
	 * @param action the array creation function
	 * @return an array containing all of the elements in this collection
	 * @see Collection#toArray(Object[])
	 */
	default <E> E[] toArray(IntFunction<E[]> action) {
		return toArray(action.apply(size()));
	}
	
	/**
	 * Returns a Type-Specific Iterator to reduce (un)boxing
	 * @return a iterator of the collection
	 * @see Collection#iterator()
	 */
	@Override
	public ObjectIterator<T> iterator();
	
	/**
	 * Creates a Wrapped Collection that is Synchronized
	 * @return a new Collection that is synchronized
	 * @see ObjectCollections#synchronize
	 */
	public default ObjectCollection<T> synchronize() { return ObjectCollections.synchronize(this); }
	
	/**
	 * Creates a Wrapped Collection that is Synchronized
	 * @param mutex is the controller of the synchronization block
	 * @return a new Collection Wrapper that is synchronized
	 * @see ObjectCollections#synchronize
	 */
	public default ObjectCollection<T> synchronize(Object mutex) { return ObjectCollections.synchronize(this, mutex); }
	
	/**
	 * Creates a Wrapped Collection that is unmodifiable
	 * @return a new Collection Wrapper that is unmodifiable
	 * @see ObjectCollections#unmodifiable
	 */
	public default ObjectCollection<T> unmodifiable() { return ObjectCollections.unmodifiable(this); }
	

	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default ObjectSplititerator<T> spliterator() { return ObjectSplititerators.createSplititerator(this, 0); }
}