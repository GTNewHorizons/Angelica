package speiger.src.collections.ints.collections;

import java.util.Collection;
import java.util.Objects;
import java.util.AbstractCollection;

import speiger.src.collections.ints.functions.IntConsumer;
import speiger.src.collections.ints.utils.IntIterators;
import speiger.src.collections.ints.utils.IntArrays;

/**
 * Abstract Type Specific Collection that reduces boxing/unboxing
 */
public abstract class AbstractIntCollection extends AbstractCollection<Integer> implements IntCollection
{
	@Override
	public abstract IntIterator iterator();

	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead.
	 */
	@Override
	@Deprecated
	public boolean add(Integer e) { return IntCollection.super.add(e); }

	@Override
	public boolean addAll(IntCollection c) {
		boolean modified = false;
		for(IntIterator iter = c.iterator();iter.hasNext();modified |= add(iter.nextInt()));
		return modified;
	}

	@Override
	public IntCollection copy() { throw new UnsupportedOperationException(); }

	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead.
	 */
	@Override
	@Deprecated
	public boolean contains(Object e) { return IntCollection.super.contains(e); }

	/**
	 * A Type-Specific implementation of contains. This implementation iterates over the elements and returns true if the value match.
	 * @param e the element that should be searched for.
	 * @return true if the value was found.
	 */
	@Override
	public boolean contains(int e) {
		for(IntIterator iter = iterator();iter.hasNext();) { if(iter.nextInt() == e) return true; }
		return false;
	}

	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead.
	 */
	@Override
	@Deprecated
	public boolean addAll(Collection<? extends Integer> c)
	{
		return c instanceof IntCollection ? addAll((IntCollection)c) : super.addAll(c);
	}

	/**
	 * A Type-Specific implementation of containsAll. This implementation iterates over all elements and checks all elements are present in the other collection.
	 * @param c the collection that should be checked if it contains all elements.
	 * @return true if all elements were found in the collection
	 * @throws NullPointerException if the collection is null
	 */
	@Override
	public boolean containsAll(IntCollection c) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) return true;
		for(IntIterator iter = c.iterator();iter.hasNext();)
			if(!contains(iter.nextInt()))
				return false;
		return true;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		Objects.requireNonNull(c);
		return c instanceof IntCollection ? containsAll((IntCollection)c) : super.containsAll(c);
	}

	/**
	 * This implementation iterates over the elements of the collection and checks if they are stored in this collection
	 * @param c the elements that should be checked for
	 * @return true if any element is in this collection
	 * @throws NullPointerException if the collection is null
	 */
	@Override
	@Deprecated
	public boolean containsAny(Collection<?> c) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) return false;
		for(Object e : c)
			if(contains(e))
				return true;
		return false;
	}

	/**
	 * This implementation iterates over the elements of the collection and checks if they are stored in this collection.
	 * @param c the elements that should be checked for
	 * @return true if any element is in this collection
 	 * @throws NullPointerException if the collection is null
	 */
	@Override
	public boolean containsAny(IntCollection c) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) return false;
		for(IntIterator iter = c.iterator();iter.hasNext();)
			if(contains(iter.nextInt()))
				return true;
		return false;
	}

	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead.
	 */
	@Override
	@Deprecated
	public boolean remove(Object e) { return IntCollection.super.remove(e); }

	/**
	 * A Type-Specific implementation of remove. This implementation iterates over the elements until it finds the element that is searched for or it runs out of elements.
	 * It stops after finding the first element
	 * @param e the element that is searched for
	 * @return true if the element was found and removed.
	 */
	@Override
	public boolean remInt(int e) {
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(iter.nextInt() == e) {
				iter.remove();
				return true;
			}
		}
		return false;
	}

	/**
	 * A Type-Specific implementation of removeAll. This Implementation iterates over all elements and removes them as they were found in the other collection.
	 * @param c the elements that should be deleted
	 * @return true if the collection was modified.
	 * @throws NullPointerException if the collection is null
	 */
	@Override
	public boolean removeAll(IntCollection c) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) return false;
		boolean modified = false;
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(c.contains(iter.nextInt())) {
				iter.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	public boolean removeAll(IntCollection c, IntConsumer r) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) return false;
		Objects.requireNonNull(r);
		boolean modified = false;
		for(IntIterator iter = iterator();iter.hasNext();) {
			int e = iter.nextInt();
			if(c.contains(e)) {
				r.accept(e);
				iter.remove();
				modified = true;
			}
		}
		return modified;
	}

	/**
	 * A Type-Specific implementation of retainAll. This Implementation iterates over all elements and removes them as they were not found in the other collection.
	 * @param c the elements that should be kept
	 * @return true if the collection was modified.
	 * @throws NullPointerException if the collection is null
	 */
	@Override
	public boolean retainAll(IntCollection c) {
		Objects.requireNonNull(c);
		if(c.isEmpty()) {
			boolean modified = !isEmpty();
			clear();
			return modified;
		}
		boolean modified = false;
		for(IntIterator iter = iterator();iter.hasNext();) {
			if(!c.contains(iter.nextInt())) {
				iter.remove();
				modified = true;
			}
		}
		return modified;
	}

	@Override
	public boolean retainAll(IntCollection c, IntConsumer r) {
		Objects.requireNonNull(c);
		Objects.requireNonNull(r);
		if(c.isEmpty()) {
			boolean modified = !isEmpty();
			forEach(r);
			clear();
			return modified;
		}
		boolean modified = false;
		for(IntIterator iter = iterator();iter.hasNext();) {
			int e = iter.nextInt();
			if(!c.contains(e)) {
				r.accept(e);
				iter.remove();
				modified = true;
			}
		}
		return modified;
	}

	/**
	 * A Type-Specific implementation of toArray that links to {@link #toIntArray(int[])} with a newly created array.
	 * @return an array containing all of the elements in this collection
	 */
	@Override
	public int[] toIntArray() {
		if(isEmpty()) return IntArrays.EMPTY_ARRAY;
		return toIntArray(new int[size()]);
	}

	/**
	 * A Type-Specific implementation of toArray. This implementation iterates over all elements and unwraps them into primitive type.
	 * @param a array that the elements should be injected to. If null or to small a new array with the right size is created
	 * @return an array containing all of the elements in this collection
	 */
	@Override
	public int[] toIntArray(int[] a) {
		if(a == null || a.length < size()) a = new int[size()];
		IntIterators.unwrap(a, iterator());
		if (a.length > size()) a[size()] = 0;
		return a;
	}
}
