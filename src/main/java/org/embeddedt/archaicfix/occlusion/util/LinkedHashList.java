package org.embeddedt.archaicfix.occlusion.util;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import java.util.*;

@SuppressWarnings("unchecked")
public class LinkedHashList<E extends Object> extends AbstractCollection<E> implements List<E>, Cloneable, java.io.Serializable {

	private static final long serialVersionUID = -642033533165934945L;

	protected static final class Entry {

		protected Entry next;
		protected Entry prev;
		protected final Object key;
		protected final int hash;
		protected Entry nextInBucket;

		protected Entry(Object key, int keyHash) {

			this.key = key;
			this.hash = keyHash;
		}
	}

	protected static int roundUpToPowerOf2(int number) {

		return number >= Ints.MAX_POWER_OF_TWO ? Ints.MAX_POWER_OF_TWO : (number > 2) ? Integer.highestOneBit((number - 1) << 1) : 2;
	}

	protected transient Entry head;
	protected transient Entry tail;
	protected transient int size;
	protected transient int mask;
	protected transient Entry[] hashTable;
	protected transient int modCount;

	public LinkedHashList() {

		hashTable = new Entry[8];
		mask = 7;
	}

	public LinkedHashList(int size) {

		size = roundUpToPowerOf2(size);
		hashTable = new Entry[size];
		mask = size - 1;
	}

	public LinkedHashList(Collection<E> col) {

		int size = roundUpToPowerOf2(col.size());
		hashTable = new Entry[size];
		mask = size - 1;
		addAll(col);
	}

	protected int hash(Object n) {

		int h = n == null ? 0 : n.hashCode();
		h ^= (h >>> 20) ^ (h >>> 12);
		return h ^ (h >>> 7) ^ (h >>> 4);
	}

	@Override
	public int size() {

		return size;
	}

	protected boolean add(E obj, int hash) {

		if (seek(obj, hash) != null) {
			return false;
		}

		Entry e;
		++modCount;
		insert(e = new Entry(obj, hash));
		rehashIfNecessary();
		e.prev = tail;
		e.next = null;
		if (tail != null) {
			tail.next = e;
		} else {
			head = e;
		}
		tail = e;
		return true;
	}

	@Override
	public boolean add(E obj) {

		int hash = hash(obj);
		return add(obj, hash);
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
		Entry e = index(index);
		delete(e);
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

		++modCount;
		Entry e = index(index);
		Entry n = new Entry(obj, hash);
		n.next = e.next;
		n.prev = e;
		e.next = n;
		if (n.next != null) {
			n.next.prev = n;
		}
		insert(n);
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
		return (E) index(index).key;
	}

	@Override
	public int indexOf(Object o) {

		Entry v = seek(o, hash(o));
		if (v == null) {
			return -1;
		}
		Entry n = head;
		for (int i = 0; n != tail; ++i) {
			if (v == n) {
				return i;
			}
			n = n.next;
		}
		return size;
	}

	@Override
	public int lastIndexOf(Object o) {

		return indexOf(o);
	}

	public boolean push(E obj) {

		int hash = hash(obj);
		return add(obj, hash);
	}

	public E pop() {

		Entry e = tail;
		if (e != null) {
			unlink(e);
			return (E) e.key;
		}
		return null;
	}

	public E peek() {

		return tail != null ? (E) tail.key : null;
	}

	public E poke() {

		return head != null ? (E) head.key : null;
	}

	public boolean unshift(E obj) {

		int hash = hash(obj);
		if (seek(obj, hash) != null) {
			return false;
		}

		Entry e;
		++modCount;
		insert(e = new Entry(obj, hash));
		rehashIfNecessary();
		e.next = head;
		e.prev = null;
		if (head != null) {
			head.prev = e;
		} else {
			tail = e;
		}
		head = e;
		return true;
	}

	public E shift() {

		Entry e = head;
		if (e != null) {
			unlink(e);
			return (E) e.key;
		}
		return null;
	}

	@Override
	public boolean contains(Object obj) {

		return seek(obj, hash(obj)) != null;
	}

	@Override
	public boolean remove(Object obj) {

		Entry e = seek(obj, hash(obj));
		if (e == null) {
			return false;
		}

		unlink(e);
		return true;
	}

	@Override
	public E remove(int index) {

		checkElementIndex(index);

		Entry oldValue = index(index);
		unlink(oldValue);

		return (E) oldValue.key;
	}

	protected Entry index(int index) {

		Entry x;
		if (index < (size >> 1)) {
			x = head;
			for (int i = index; i-- > 0;) {
				x = x.next;
			}
		} else {
			x = tail;
			for (int i = size - 1; i-- > index;) {
				x = x.prev;
			}
		}
		return x;
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
		++size;
	}

	protected boolean linkBefore(E obj, Entry succ) {

		int hash = hash(obj);
		if (seek(obj, hash) != null) {
			return false;
		}

		final Entry pred = succ.prev;
		final Entry newNode = new Entry(obj, hash);
		modCount++;
		insert(newNode);
		rehashIfNecessary();
		newNode.next = succ;
		newNode.prev = pred;
		succ.prev = newNode;
		if (pred == null) {
			head = newNode;
		} else {
			pred.next = newNode;
		}
		return true;
	}

	protected void delete(Entry entry) {

		l: {
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
		--size;
	}

	protected E unlink(Entry x) {

		final E element = (E) x.key;
		final Entry next = x.next;
		final Entry prev = x.prev;

		if (prev == null) {
			head = next;
		} else {
			prev.next = next;
			x.prev = null;
		}

		if (next == null) {
			tail = prev;
		} else {
			next.prev = prev;
			x.next = null;
		}

		delete(x);
		modCount++;
		return element;
	}

	protected void rehashIfNecessary() {

		Entry[] old = hashTable, newTable;
		if (size > old.length * 2 && old.length < Ints.MAX_POWER_OF_TWO) {
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

	private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

		// Write out element count, and any hidden stuff
		int expectedModCount = modCount;
		s.defaultWriteObject();

		// Write out size as capacity for behavioural compatibility with clone()
		s.writeInt(size);

		// Write out all elements in the proper order.
		Entry n = head;
		for (int i = 0; i < size; i++) {
			s.writeObject(n.key);
			n = n.next;
		}

		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {

		head = tail = null;
		hashTable = new Entry[8];
		mask = 7;
		size = 0;

		// Read in size, and any hidden stuff
		s.defaultReadObject();

		// Read in capacity
		int size = s.readInt();

		if (size > 0) {

			// Read in all elements in the proper order.
			for (int i = 0; i < size; i++) {
				add((E) s.readObject());
			}
		}
	}

	@Override
	public LinkedHashList<E> clone() {

		return new LinkedHashList<E>(this);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {

		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public Iterator<E> iterator() {

		return listIterator();
	}

	@Override
	public ListIterator<E> listIterator() {

		return listIterator(0);
	}

	@Override
	public ListIterator<E> listIterator(int index) {

		checkPositionIndex(index);
		return new ListItr(index);
	}

	public Iterator<E> descendingIterator() {

		return new DescendingIterator();
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

	@Override
	public void sort(Comparator<? super E> comparator) {
		ArrayList<E> sorter = new ArrayList<>(this);
		sorter.sort(comparator);
		this.clear();
		this.addAll(sorter);
	}

	protected class ListItr implements ListIterator<E> {

		protected Entry lastReturned = null;
		protected Entry next;
		protected int nextIndex;
		protected int expectedModCount = modCount;

		protected ListItr(int index) {

			next = (index == size) ? null : index(index);
			nextIndex = index;
		}

		@Override
		public boolean hasNext() {

			return nextIndex < size;
		}

		@Override
		public E next() {

			checkForComodification();
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			lastReturned = next;
			next = next.next;
			nextIndex++;
			return (E) lastReturned.key;
		}

		@Override
		public boolean hasPrevious() {

			return nextIndex > 0;
		}

		@Override
		public E previous() {

			checkForComodification();
			if (!hasPrevious()) {
				throw new NoSuchElementException();
			}

			lastReturned = next = (next == null) ? tail : next.prev;
			nextIndex--;
			return (E) lastReturned.key;
		}

		@Override
		public int nextIndex() {

			return nextIndex;
		}

		@Override
		public int previousIndex() {

			return nextIndex - 1;
		}

		@Override
		public void remove() {

			checkForComodification();
			if (lastReturned == null) {
				throw new IllegalStateException();
			}

			Entry lastNext = lastReturned.next;
			unlink(lastReturned);
			if (next == lastReturned) {
				next = lastNext;
			} else {
				nextIndex--;
			}
			lastReturned = null;
			expectedModCount++;
		}

		@Override
		public void set(E e) {

			checkForComodification();
			if (lastReturned == null) {
				throw new IllegalStateException();
			}

			linkBefore(e, lastReturned);
			unlink(lastReturned);
			lastReturned = (next == null) ? tail : next.prev;
			expectedModCount += 2;
		}

		@Override
		public void add(E e) {

			checkForComodification();
			lastReturned = null;
			if (next == null) {
				push(e);
			} else {
				linkBefore(e, next);
			}
			nextIndex++;
			expectedModCount++;
		}

		protected final void checkForComodification() {

			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}

	}

	protected class DescendingIterator implements Iterator<E> {

		protected final ListItr itr = new ListItr(size());

		@Override
		public boolean hasNext() {

			return itr.hasPrevious();
		}

		@Override
		public E next() {

			return itr.previous();
		}

		@Override
		public void remove() {

			itr.remove();
		}

	}

}
