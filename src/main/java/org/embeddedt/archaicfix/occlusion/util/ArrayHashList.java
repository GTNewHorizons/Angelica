package org.embeddedt.archaicfix.occlusion.util;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

@SuppressWarnings("unchecked")
public class ArrayHashList<E extends Object> extends AbstractCollection<E> implements List<E>, Cloneable, java.io.Serializable {

	private static final long serialVersionUID = 3230581060536180693L;

	protected static final class Entry {

		public final Object key;
		public final int hash;
		public Entry nextInBucket;

		protected Entry(Object key, int keyHash) {

			this.key = key;
			this.hash = keyHash;
		}
	}

	protected static int hash(Object n) {

		int h = n == null ? 0 : n.hashCode();
		h ^= (h >>> 20) ^ (h >>> 12);
		return h ^ (h >>> 7) ^ (h >>> 4);
	}

	private static int roundUpToPowerOf2(int number) {

		return number >= Ints.MAX_POWER_OF_TWO ? Ints.MAX_POWER_OF_TWO : (number > 2) ? Integer.highestOneBit((number - 1) << 1) : 2;
	}

	private transient Object[] elementData;
	protected transient int size;
	protected transient int mask;
	protected transient Entry[] hashTable;
	protected transient int modCount;

	public ArrayHashList() {

		elementData = new Object[10];
		hashTable = new Entry[8];
		mask = 7;
	}

	public ArrayHashList(int size) {

		elementData = new Object[size];
		size = roundUpToPowerOf2(size) >> 1;
		hashTable = new Entry[size];
		mask = size - 1;
	}

	public ArrayHashList(Collection<E> col) {

		int size = col.size();
		elementData = new Object[size];
		size = roundUpToPowerOf2(size) >> 1;
		hashTable = new Entry[size];
		mask = size - 1;
		addAll(col);
	}

	@Override
	public int size() {

		return size;
	}

	protected void add(E obj, int hash) {

		ensureCapacityInternal(size + 1);
		elementData[size++] = obj;
		insert(new Entry(obj, hash));
		rehashIfNecessary();
	}

	@Override
	public boolean add(E obj) {

		int hash = hash(obj);
		if (seek(obj, hash) != null) {
			return false;
		}

		add(obj, hash);

		return true;
	}

	@Override
	public E set(int index, E obj) {

		checkElementIndex(index);

		int hash = hash(obj);
		if (seek(obj, hash) != null) {
			// return null;
			throw new IllegalArgumentException("Duplicate entries not allowed");
		}

		++modCount;
		Entry e = seek(elementData[index], hash(elementData[index]));
		delete(e);
		elementData[index] = obj;
		insert(new Entry(obj, hash));

		return (E) e.key;
	}

	@Override
	public void add(int index, E obj) {

		checkPositionIndex(index);

		int hash = hash(obj);
		if (seek(obj, hash) != null) {
			throw new IllegalArgumentException("Duplicate entries not allowed");
		}

		if (index == size) {
			add(obj, hash);
			return;
		}

		ensureCapacityInternal(++size);
		System.arraycopy(elementData, index, elementData, index + 1, size - index - 1);
		elementData[index] = obj;
		insert(new Entry(obj, hash));
		rehashIfNecessary();
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {

		if (c.size() == 0) {
			return false;
		}

		for (E e : c) {
			add(index++, e);
		}

		return true;
	}

	@Override
	public E get(int index) {

		checkElementIndex(index);
		return index(index);
	}

	@Override
	public int indexOf(Object obj) {

		Entry e = seek(obj, hash(obj));
		if (e == null) {
			return -1;
		}

		Object o = e.key;
		Object[] data = elementData;
		int i = size;
		while (i-- > 0) {
			if (data[i] == o) {
				break;
			}
		}
		return i;
	}

	@Override
	public int lastIndexOf(Object o) {

		return indexOf(o);
	}

	@Override
	public boolean contains(Object obj) {

		return seek(obj, hash(obj)) != null;
	}

	@Override
	public E remove(int index) {

		checkElementIndex(index);

		E oldValue = index(index);
		delete(seek(oldValue, hash(oldValue)));
		fastRemove(index);

		return oldValue;
	}

	@Override
	public boolean remove(Object obj) {

		Entry e = seek(obj, hash(obj));
		if (e == null) {
			return false;
		}

		Object o = e.key;
		Object[] data = elementData;
		for (int i = size; i-- > 0;) {
			if (data[i] == o) {
				fastRemove(i);
				break;
			}
		}
		delete(e);
		return true;
	}

	private void fastRemove(int index) {

		modCount++;
		int numMoved = size - index - 1;
		if (numMoved > 0) {
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		}
		elementData[--size] = null; // clear to let GC do its work
	}

	// { following methods (until the next }) copied mostly verbatim from ArrayList
	@Override
	public void clear() {

		modCount++;

		// clear to let GC do its work
		for (int i = 0; i < size; i++) {
			elementData[i] = null;
		}

		for (int i = hashTable.length; i-- > 0;) {
			hashTable[i] = null;
		}

		size = 0;
	}

	/**
	 * Trims the capacity of this <tt>ArrayHashList</tt> instance to be the list's current size. An application can use this operation to minimize the storage
	 * of an <tt>ArrayHashList</tt> instance.
	 */
	public void trimToSize() {

		++modCount;
		if (size < elementData.length) {
			elementData = Arrays.copyOf(elementData, size);
		}
	}

	/**
	 * Increases the capacity of this <tt>ArrayHashList</tt> instance, if necessary, to ensure that it can hold at least the number of elements specified by the
	 * minimum capacity argument.
	 *
	 * @param minCapacity
	 *            the desired minimum capacity
	 */
	public void ensureCapacity(int minCapacity) {

		if (minCapacity > 0) {
			ensureCapacityInternal(minCapacity);
		}
	}

	private void ensureCapacityInternal(int minCapacity) {

		++modCount;
		// overflow-conscious code
		if (minCapacity - elementData.length > 0) {
			grow(minCapacity);
		}
	}

	/**
	 * The maximum size of array to allocate. Some VMs reserve some header words in an array. Attempts to allocate larger arrays may result in OutOfMemoryError:
	 * Requested array size exceeds VM limit
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	/**
	 * Increases the capacity to ensure that it can hold at least the number of elements specified by the minimum capacity argument.
	 *
	 * @param minCapacity
	 *            the desired minimum capacity
	 */
	private void grow(int minCapacity) {

		// overflow-conscious code
		int oldCapacity = elementData.length;
		int newCapacity = oldCapacity + (oldCapacity >> 1);
		if (newCapacity - minCapacity < 0) {
			newCapacity = minCapacity;
		}
		if (newCapacity - MAX_ARRAY_SIZE > 0) {
			newCapacity = hugeCapacity(minCapacity);
		}
		// minCapacity is usually close to size, so this is a win:
		elementData = Arrays.copyOf(elementData, newCapacity);
	}

	private static int hugeCapacity(int minCapacity) {

		if (minCapacity < 0) {
			throw new OutOfMemoryError();
		}
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

		// Write out element count, and any hidden stuff
		int expectedModCount = modCount;
		s.defaultWriteObject();

		// Write out size as capacity for behavioural compatibility with clone()
		s.writeInt(size);

		// Write out all elements in the proper order.
		for (int i = 0; i < size; i++) {
			s.writeObject(elementData[i]);
		}

		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {

		elementData = new Object[10];
		hashTable = new Entry[8];
		mask = 7;

		// Read in size, and any hidden stuff
		s.defaultReadObject();

		// Read in capacity
		int size = s.readInt();

		if (size > 0) {
			// be like clone(), allocate array based upon size not capacity
			ensureCapacityInternal(size);

			// Read in all elements in the proper order.
			for (int i = 0; i < size; i++) {
				add((E) s.readObject());
			}
		}
	}

	// }

	E index(int index) {

		return (E) elementData[index];
	}

	protected Entry seek(Object obj, int hash) {

		for (Entry entry = hashTable[hash & mask]; entry != null; entry = entry.nextInBucket) {
			if (hash == entry.hash && Objects.equal(obj, entry.key)) {
				return entry;
			}
		}

		return null;
	}

	protected void insert(Entry entry) {

		int bucket = entry.hash & mask;
		entry.nextInBucket = hashTable[bucket];
		hashTable[bucket] = entry;
	}

	protected void delete(Entry entry) {

		l: synchronized (hashTable) {
			int bucket = entry.hash & mask;
			Entry prev = null, cur = hashTable[bucket];
			if (cur == entry) {
				hashTable[bucket] = cur.nextInBucket;
				break l;
			}
			for (; true; cur = cur.nextInBucket) {
				if (cur == entry) {
					prev.nextInBucket = entry.nextInBucket;
					break l;
				}
				prev = cur;
			}
		}
	}

	protected void rehashIfNecessary() {

		Entry[] old = hashTable, newTable;
		if (size > old.length * 2 && old.length < Ints.MAX_POWER_OF_TWO) {
			synchronized (hashTable) {
				int newTableSize = old.length * 2, newMask = newTableSize - 1;
				newTable = new Entry[newTableSize];

				for (int bucket = old.length; bucket-- > 0;) {
					Entry entry = old[bucket];
					while (entry != null) {
						Entry nextEntry = entry.nextInBucket;
						int keyBucket = entry.hash & newMask;
						entry.nextInBucket = newTable[keyBucket];
						newTable[keyBucket] = entry;
						entry = nextEntry;
					}
				}
				hashTable = newTable;
				mask = newMask;
			}
		}
	}

	@Override
	public ArrayHashList<E> clone() {

		return new ArrayHashList<E>(this);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<E> iterator() {

		return new Itr();
	}

	@Override
	public ListIterator<E> listIterator() {

		return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {

		return new ListItr(index);
	}

	protected boolean isElementIndex(int index) {

		return index >= 0 && index < size;
	}

	protected boolean isPositionIndex(int index) {

		return index >= 0 && index <= size;
	}

	protected String outOfBoundsMsg(int index) {

		return "Index: " + index + ", Size: " + size;
	}

	protected void checkElementIndex(int index) {

		if (!isElementIndex(index)) {
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}
	}

	protected void checkPositionIndex(int index) {

		if (!isPositionIndex(index)) {
			throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}
	}

	private class Itr implements Iterator<E> {

		int cursor; // index of next element to return
		int lastRet = -1; // index of last element returned; -1 if no such
		int expectedModCount = modCount;

		@Override
		public boolean hasNext() {

			return cursor != size;
		}

		@Override
		public E next() {

			checkForComodification();
			int i = cursor;
			if (i >= size) {
				throw new NoSuchElementException();
			}
			Object[] elementData = ArrayHashList.this.elementData;
			if (i >= elementData.length) {
				throw new ConcurrentModificationException();
			}
			cursor = i + 1;
			return (E) elementData[lastRet = i];
		}

		@Override
		public void remove() {

			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();

			try {
				ArrayHashList.this.remove(lastRet);
				cursor = lastRet;
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		final void checkForComodification() {

			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}
	}

	private class ListItr extends Itr implements ListIterator<E> {

		ListItr(int index) {

			super();
			cursor = index;
		}

		@Override
		public boolean hasPrevious() {

			return cursor != 0;
		}

		@Override
		public int nextIndex() {

			return cursor;
		}

		@Override
		public int previousIndex() {

			return cursor - 1;
		}

		@Override
		@SuppressWarnings("unchecked")
		public E previous() {

			checkForComodification();
			int i = cursor - 1;
			if (i < 0) {
				throw new NoSuchElementException();
			}
			Object[] elementData = ArrayHashList.this.elementData;
			if (i >= elementData.length) {
				throw new ConcurrentModificationException();
			}
			cursor = i;
			return (E) elementData[lastRet = i];
		}

		@Override
		public void set(E e) {

			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();

			try {
				ArrayHashList.this.set(lastRet, e);
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {

			checkForComodification();

			try {
				int i = cursor;
				ArrayHashList.this.add(i, e);
				cursor = i + 1;
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

}
