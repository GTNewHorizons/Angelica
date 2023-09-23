package speiger.src.collections.objects.lists;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import speiger.src.collections.objects.collections.AbstractObjectCollection;
import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectIterator;
import speiger.src.collections.objects.collections.ObjectSplititerator;
import speiger.src.collections.objects.utils.ObjectSplititerators;
import speiger.src.collections.utils.SanityChecks;

/**
 * Abstract implementation of the {@link ObjectList} interface.
 * @param <T> the keyType of elements maintained by this Collection
 */
public abstract class AbstractObjectList<T> extends AbstractObjectCollection<T> implements ObjectList<T> 
{
	/**
	 * A Type-Specific implementation of add function that delegates to {@link List#add(int, Object)}
	 */
	@Override
	public boolean add(T e) {
		add(size(), e);
		return true;
	}
	
	/**
	 * A Type-Specific implementation that iterates over the elements and adds them.
	 * @param c the elements that wants to be added
	 * @return true if the list was modified
	 */
	@Override
	public boolean addAll(ObjectCollection<T> c) {
		return addAll(size(), c);
	}
	
	/**
	 * A Type-Specific implementation that iterates over the elements and adds them.
	 * @param c the elements that wants to be added
	 * @return true if the list was modified
	 */
	@Override
	public boolean addAll(ObjectList<T> c) {
		return addAll(size(), c);
	}
	
	/** {@inheritDoc}
	 * <p>This default implementation delegates to the corresponding type-specific function.
	 * @deprecated Please use the corresponding type-specific function instead. 
	 */
	@Override
	@Deprecated
	public boolean addAll(Collection<? extends T> c)
	{
		return c instanceof ObjectCollection ? addAll((ObjectCollection<T>)c) : addAll(size(), c);
	}
	
	/**
	 * The IndexOf implementation iterates over all elements and compares them to the search value.
	 * @param o the value that the index is searched for.
	 * @return index of the value that was searched for. -1 if not found
	 * @note it is highly suggested not to use this with Primitives because of boxing. But it is still supported because of ObjectComparason that are custom objects and allow to find the contents.
	 */
	@Override
	public int indexOf(Object o) {
		ObjectListIterator<T> iter = listIterator();
		if(o == null) {
			while(iter.hasNext()) {
				if(iter.next() == null)
					return iter.previousIndex();
			}
			return -1;
		}
		while(iter.hasNext()) {
			if(Objects.equals(o, iter.next()))
				return iter.previousIndex();
		}
		return -1;
	}
	
	/**
	 * The lastIndexOf implementation iterates over all elements and compares them to the search value.
	 * @param o the value that the index is searched for.
	 * @return the last index of the value that was searched for. -1 if not found
	 * @note it is highly suggested not to use this with Primitives because of boxing. But it is still supported because of ObjectComparason that are custom objects and allow to find the contents.
	 */
	@Override
	public int lastIndexOf(Object o) {
		ObjectListIterator<T> iter = listIterator(size());
		if(o == null) {
			while(iter.hasPrevious()) {
				if(iter.previous() == null)
					return iter.nextIndex();
			}
			return -1;
		}
		while(iter.hasPrevious()) {
			if(Objects.equals(o, iter.previous()))
				return iter.nextIndex();
		}
		return -1;
	}
	
	@Override
	public boolean swapRemove(T e) {
		int index = indexOf(e);
		if(index == -1) return false;
		swapRemove(index);
		return true;
	}
	
	/**
	 * Compares if the list are the same.
	 */
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof List))
			return false;
		List<?> l = (List<?>)o;
		if(l.size() != size()) return false;
		ListIterator<T> e1 = listIterator();
		ListIterator<?> e2 = l.listIterator();
		while (e1.hasNext() && e2.hasNext()) {
			if(!Objects.equals(e1.next(), e2.next()))
				return false;
		}
		return !(e1.hasNext() || e2.hasNext());
	}
	
	/**
	 * Generates the hashcode based on the values stored in the list.
	 */
	@Override
	public int hashCode() {
		int hashCode = 1;
		ObjectListIterator<T> i = listIterator();
		while(i.hasNext())
			hashCode = 31 * hashCode + i.next().hashCode();
		return hashCode;
	}
	
	@Override
	public ObjectList<T> subList(int fromIndex, int toIndex) {
		SanityChecks.checkArrayCapacity(size(), fromIndex, toIndex-fromIndex);
		return new SubList(this, 0, fromIndex, toIndex);
	}
	
	@Override
	public ObjectIterator<T> iterator() {
		return listIterator(0);
	}
	
	@Override
	public ObjectListIterator<T> listIterator() {
		return listIterator(0);
	}

	@Override
	public ObjectListIterator<T> listIterator(int index) {
		if(index < 0 || index > size()) throw new IndexOutOfBoundsException();
		return new ObjectListIter(index);
	}
	
	@Override
	public void size(int size) {
		while(size > size()) add(null);
		while(size < size()) remove(size() - 1);
	}
	
	public AbstractObjectList<T> copy() { throw new UnsupportedOperationException(); }
	
	private class SubList extends AbstractObjectList<T>
	{
		final AbstractObjectList<T> list;
		final int parentOffset;
		final int offset;
		int size;
		
		public SubList(AbstractObjectList<T> list, int offset, int from, int to) {
			this.list = list;
			this.parentOffset = from;
			this.offset = offset + from;
			this.size = to - from;
		}
		
		@Override
		public void add(int index, T element) {
			checkAddSubRange(index);
			list.add(parentOffset+index, element);
			size++;
		}
		
		@Override
		public boolean addAll(int index, Collection<? extends T> c) {
			checkAddSubRange(index);
			int add = c.size();
			if(add <= 0) return false;
			list.addAll(parentOffset+index, c);
			this.size += add;
			return true;
		}
		
		@Override
		public boolean addAll(int index, ObjectCollection<T> c) {
			checkAddSubRange(index);
			int add = c.size();
			if(add <= 0) return false;
			list.addAll(parentOffset+index, c);
			this.size += add;
			return true;
		}

		@Override
		public boolean addAll(int index, ObjectList<T> c) {
			checkAddSubRange(index);
			int add = c.size();
			if(add <= 0) return false;
			list.addAll(parentOffset+index, c);
			this.size += add;
			return true;
		}
		
		@Override
		public void addElements(int from, T[] a, int offset, int length) {
			checkAddSubRange(from);
			if(length <= 0) return;
			list.addElements(parentOffset+from, a, offset, length);
			this.size += length;
		}
		
		@Override
		public T[] getElements(int from, T[] a, int offset, int length) {
			SanityChecks.checkArrayCapacity(size, from, length);
			SanityChecks.checkArrayCapacity(a.length, offset, length);
			return list.getElements(from+parentOffset, a, offset, length);
		}
		
		@Override
		public void removeElements(int from, int to) {
			if(to-from <= 0) return;
			checkSubRange(from);
			checkAddSubRange(to);
			list.removeElements(from+parentOffset, to+parentOffset);
			size -= to - from;
		}
		
		@Override
		public <K> K[] extractElements(int from, int to, Class<K> type) {
			checkSubRange(from);
			checkAddSubRange(to);
			K[] result = list.extractElements(from+parentOffset, to+parentOffset, type);
			size -= result.length;
			return result;
		}

		@Override
		public T get(int index) {
			checkSubRange(index);
			return list.get(parentOffset+index);
		}

		@Override
		public T set(int index, T element) {
			checkSubRange(index);
			return list.set(parentOffset+index, element);
		}
		
		@Override
		public T swapRemove(int index) {
			checkSubRange(index);
			if(index == size-1) {
				T result = list.remove(parentOffset+size-1);
				size--;
				return result;
			}
			T result = list.set(index+parentOffset, list.get(parentOffset+size-1));
			list.remove(parentOffset+size-1);
			size--;
			return result;
		}
		
		@Override
		public T remove(int index) {
			checkSubRange(index);
			T result = list.remove(index+parentOffset);
			size--;
			return result;
		}
		
		@Override
		public int size() {
			return size;
		}
		
		@Override
		public ObjectSplititerator<T> spliterator() { return ObjectSplititerators.createSplititerator(this, 16464); }
		
		@Override
		public ObjectListIterator<T> listIterator(int index) {
			if(index < 0 || index > size()) throw new IndexOutOfBoundsException();
			return new SubListIterator(this, index);
		}
		
		@Override
		public ObjectList<T> subList(int fromIndex, int toIndex) {
			SanityChecks.checkArrayCapacity(size, fromIndex, toIndex-fromIndex);
			return new SubList(this, offset, fromIndex, toIndex);
		}
		
		protected void checkSubRange(int index) {
			if (index < 0 || index >= size)
				throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
		
		protected void checkAddSubRange(int index) {
			if (index < 0 || index > size)
				throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
		
		private class SubListIterator implements ObjectListIterator<T>
		{
			AbstractObjectList<T> list;
			int index;
			int lastReturned = -1;
			
			SubListIterator(AbstractObjectList<T> list, int index) {
				this.list = list;
				this.index = index;
			}
			
			@Override
			public boolean hasNext() {
				return index < list.size();
			}
			
			@Override
			public T next() {
				if(!hasNext()) throw new NoSuchElementException();
				int i = index++;
				return list.get((lastReturned = i));
			}
			
			@Override
			public boolean hasPrevious() {
				return index > 0;
			}
			
			@Override
			public T previous() {
				if(!hasPrevious()) throw new NoSuchElementException();
				index--;
				return list.get((lastReturned = index));
			}
			
			@Override
			public int nextIndex() {
				return index;
			}
			
			@Override
			public int previousIndex() {
				return index-1;
			}
			
			@Override
			public void remove() {
				if(lastReturned == -1) throw new IllegalStateException();
				list.remove(lastReturned);
				index = lastReturned;
				lastReturned = -1;
			}
			
			@Override
			public void set(T e) {
				if(lastReturned == -1) throw new IllegalStateException();
				list.set(lastReturned, e);
			}
			
			@Override
			public void add(T e) {
				list.add(index, e);
				index++;
				lastReturned = -1;
			}
			
			@Override
			public int skip(int amount) {
				if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
				int steps = Math.min(amount, size() - index);
				index += steps;
				if(steps > 0) lastReturned = Math.min(index-1, size()-1);
				return steps;
			}
			
			@Override
			public int back(int amount) {
				if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
				int steps = Math.min(amount, index);
				index -= steps;
				if(steps > 0) lastReturned = Math.min(index, size()-1);
				return steps;
			}
		}
	}
		
	private class ObjectListIter implements ObjectListIterator<T> {
		int index;
		int lastReturned = -1;
		
		ObjectListIter(int index) {
			this.index = index;
		}
		
		@Override
		public boolean hasNext() {
			return index < size();
		}
		
		@Override
		public T next() {
			if(!hasNext()) throw new NoSuchElementException();
			int i = index++;
			return get((lastReturned = i));
		}
		
		@Override
		public boolean hasPrevious() {
			return index > 0;
		}
		
		@Override
		public T previous() {
			if(!hasPrevious()) throw new NoSuchElementException();
			index--;
			return get((lastReturned = index));
		}
		
		@Override
		public int nextIndex() {
			return index;
		}
		
		@Override
		public int previousIndex() {
			return index-1;
		}
		
		@Override
		public void remove() {
			if(lastReturned == -1) throw new IllegalStateException();
			AbstractObjectList.this.remove(lastReturned);
			index = lastReturned;
			lastReturned = -1;
		}
		
		@Override
		public void set(T e) {
			if(lastReturned == -1) throw new IllegalStateException();
			AbstractObjectList.this.set(lastReturned, e);
		}
		
		@Override
		public void add(T e) {
			AbstractObjectList.this.add(index, e);
			index++;
			lastReturned = -1;
		}
		
		@Override
		public int skip(int amount) {
			if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
			int steps = Math.min(amount, size() - index);
			index += steps;
			if(steps > 0) lastReturned = Math.min(index-1, size()-1);
			return steps;
		}
		
		@Override
		public int back(int amount) {
			if(amount < 0) throw new IllegalStateException("Negative Numbers are not allowed");
			int steps = Math.min(amount, index);
			index -= steps;
			if(steps > 0) lastReturned = Math.min(index, size()-1);
			return steps;
		}
	}
}