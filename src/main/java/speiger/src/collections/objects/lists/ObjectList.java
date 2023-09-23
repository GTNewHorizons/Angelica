package speiger.src.collections.objects.lists;

import java.util.List;

import java.util.Objects;
import java.util.Comparator;
import java.util.function.UnaryOperator;

import speiger.src.collections.objects.collections.ObjectCollection;
import speiger.src.collections.objects.collections.ObjectSplititerator;
import speiger.src.collections.ints.functions.consumer.IntObjectConsumer;
import speiger.src.collections.objects.utils.ObjectArrays;
import speiger.src.collections.objects.utils.ObjectSplititerators;

/**
 * A Type Specific List interface that reduces boxing/unboxing and adds a couple extra quality of life features
 * @param <T> the keyType of elements maintained by this Collection
 */
public interface ObjectList<T> extends ObjectCollection<T>, List<T>
{
	/**
	 * A Helper function that will only add elements if it is not present.
	 * @param e the element to add
	 * @return true if the list was modified
	 */
	public default boolean addIfAbsent(T e) {
		if(indexOf(e) == -1) return add(e);
		return false;
	}

	/**
	 * A Helper function that will only add elements if it is present.
	 * @param e the element to add
	 * @return true if the list was modified
	 */
	public default boolean addIfPresent(T e) {
		if(indexOf(e) != -1) return add(e);
		return false;
	}

	/**
	 * A Type-Specific addAll Function to reduce (un)boxing
	 * @param c the elements that need to be added
	 * @param index index at which the specified elements is to be inserted
	 * @return true if the list was modified
	 * @see List#addAll(int, java.util.Collection)
	 */
	public boolean addAll(int index, ObjectCollection<T> c);

	/**
	 * A Type-Specific and optimized addAll function that allows a faster transfer of elements
	 * @param c the elements that need to be added
	 * @return true if the list was modified
	 */
	public boolean addAll(ObjectList<T> c);

	/**
	 * A Type-Specific and optimized addAll function that allows a faster transfer of elements
	 * @param c the elements that need to be added
	 * @param index index at which the specified elements is to be inserted
	 * @return true if the list was modified
	 */
	public boolean addAll(int index, ObjectList<T> c);

	/**
	 * A function to replace all values in the list
	 * @param o the action to replace the values
	 * @throws NullPointerException if o is null
	 */
	@Override
	public default void replaceAll(UnaryOperator<T> o) {
		Objects.requireNonNull(o);
		ObjectListIterator<T> iter = listIterator();
		while (iter.hasNext()) iter.set(o.apply(iter.next()));
	}

	/**
	 * A function to fast add elements to the list
	 * @param a the elements that should be added
	 * @throws IndexOutOfBoundsException if from is outside of the lists range
	 */
	public default void addElements(T... a) { addElements(size(), a, 0, a.length); }

	/**
	 * A function to fast add elements to the list
	 * @param from the index where the elements should be added into the list
	 * @param a the elements that should be added
	 * @throws IndexOutOfBoundsException if from is outside of the lists range
	 */
	public default void addElements(int from, T... a) { addElements(from, a, 0, a.length); }

	/**
	 * A function to fast add elements to the list
	 * @param from the index where the elements should be added into the list
	 * @param a the elements that should be added
	 * @param offset the start index of the array should be read from
	 * @param length how many elements should be read from
	 * @throws IndexOutOfBoundsException if from is outside of the lists range
	 */
	public void addElements(int from, T[] a, int offset, int length);

	/**
	 * A function to fast fetch elements from the list
	 * @param from index where the list should be fetching elements from
	 * @param a the array where the values should be inserted to
	 * @return the inputArray
	 * @throws NullPointerException if the array is null
	 * @throws IndexOutOfBoundsException if from is outside of the lists range
	 * @throws IllegalStateException if offset or length are smaller then 0 or exceed the array length
	 */
	public default T[] getElements(int from, T[] a) { return getElements(from, a, 0, a.length); }

	/**
	 * A function to fast fetch elements from the list
	 * @param from index where the list should be fetching elements from
	 * @param a the array where the values should be inserted to
	 * @param offset the startIndex of where the array should be written to
	 * @param length the number of elements the values should be fetched from
	 * @return the inputArray
	 * @throws NullPointerException if the array is null
	 * @throws IndexOutOfBoundsException if from is outside of the lists range
	 * @throws IllegalStateException if offset or length are smaller then 0 or exceed the array length
	 */
	public T[] getElements(int from, T[] a, int offset, int length);

	/**
	 * a function to fast remove elements from the list.
	 * @param from the start index of where the elements should be removed from (inclusive)
	 * @param to the end index of where the elements should be removed to (exclusive)
	 */
	public void removeElements(int from, int to);

	/**
	 * A Highly Optimized remove function that removes the desired element.
	 * But instead of shifting the elements to the left it moves the last element to the removed space.
	 * @param index the index of the element to be removed
	 * @return the element previously at the specified position
	 */
	public T swapRemove(int index);

	/**
	 * A Highly Optimized remove function that removes the desired element.
	 * But instead of shifting the elements to the left it moves the last element to the removed space.
	 * @param e the element that should be removed
	 * @return true if the element was removed
	 */
	public boolean swapRemove(T e);

	/**
	 * A function to fast extract elements out of the list, this removes the elements that were fetched.
	 * @param from the start index of where the elements should be fetched from (inclusive)
	 * @param to the end index of where the elements should be fetched to (exclusive)
	 * @param type the type of the OutputArray
	 * @return a array of the elements that were fetched
	 * @param <K> the keyType of elements maintained by this Collection
 	 */
	public <K> K[] extractElements(int from, int to, Class<K> type);

	/**
	 * Sorts the elements specified by the Natural order either by using the Comparator or the elements
	 * @see List#sort(Comparator)
	 */
	@Override
	public default void sort(Comparator<? super T> c) {
		T[] array = (T[])toArray();
		if(c != null) ObjectArrays.stableSort(array, c);
		else ObjectArrays.stableSort(array);
		ObjectListIterator<T> iter = listIterator();
		for (int i = 0,m=size();i<m && iter.hasNext();i++) {
			iter.next();
			iter.set(array[i]);
		}
	}

	/**
	 * Sorts the elements specified by the Natural order either by using the Comparator or the elements using a unstable sort
 	 * @param c the sorter of the elements, can be null
	 * @see List#sort(Comparator)
	 */
	public default void unstableSort(Comparator<? super T> c) {
		T[] array = (T[])toArray();
		if(c != null) ObjectArrays.unstableSort(array, c);
		else ObjectArrays.unstableSort(array);
		ObjectListIterator<T> iter = listIterator();
		for (int i = 0,m=size();i<m && iter.hasNext();i++) {
			iter.next();
			iter.set(array[i]);
		}
	}

	/**
	 * A Indexed forEach implementation that allows you to keep track of how many elements were already iterated over.
	 * @param action The action to be performed for each element
	 * @throws NullPointerException if the specified action is null
	 */
	@Override
	public default void forEachIndexed(IntObjectConsumer<T> action) {
		Objects.requireNonNull(action);
		for(int i = 0,m=size();i<m;action.accept(i, get(i++)));
	}
	/**
	 * A Type-Specific Iterator of listIterator
	 * @see List#listIterator
	 */
	@Override
	public ObjectListIterator<T> listIterator();

	/**
	 * A Type-Specific Iterator of listIterator
	 * @see List#listIterator(int)
	 */
	@Override
	public ObjectListIterator<T> listIterator(int index);

	/**
	 * A Type-Specific List of subList
	 * @see List#subList(int, int)
	 */
	@Override
	public ObjectList<T> subList(int from, int to);

	/**
	 * A function to ensure the elements are within the requested size.
	 * If smaller then the stored elements they get removed as needed.
	 * If bigger it is ensured that enough room is provided depending on the implementation
	 * @param size the requested amount of elements/room for elements
	 */
	public void size(int size);

	@Override
	public ObjectList<T> copy();
	/**
	 * A Type Specific Type Splititerator to reduce boxing/unboxing
	 * @return type specific splititerator
	 */
	@Override
	default ObjectSplititerator<T> spliterator() { return ObjectSplititerators.createSplititerator(this, 0); }
}
