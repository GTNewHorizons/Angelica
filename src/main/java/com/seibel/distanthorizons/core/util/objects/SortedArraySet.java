/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.util.objects;

import java.util.*;

public class SortedArraySet<E> implements SortedSet<E>
{
	private final ArrayList<E> list;
	private final Comparator<? super E> comparator;
	
	public SortedArraySet()
	{
		list = new ArrayList<>();
		comparator = null;
	}
	
	public SortedArraySet(Comparator<? super E> comparator)
	{
		list = new ArrayList<>();
		this.comparator = comparator;
	}
	
	public SortedArraySet(Collection<? extends E> collection)
	{
		list = new ArrayList<>(collection);
		comparator = null;
		list.sort(null);
	}
	
	public SortedArraySet(Collection<? extends E> collection, Comparator<? super E> comparator)
	{
		list = new ArrayList<>(collection);
		this.comparator = comparator;
		list.sort(comparator);
	}
	
	@Override
	public Comparator<? super E> comparator()
	{
		return comparator;
	}
	
	@Override
	public E first()
	{
		return list.get(0);
	}
	
	@Override
	public E last()
	{
		return list.get(list.size() - 1);
	}
	
	@Override
	public int size()
	{
		return list.size();
	}
	
	@Override
	public boolean isEmpty()
	{
		return list.isEmpty();
	}
	
	@Override
	public boolean contains(Object o)
	{
		return list.contains(o);
	}
	
	@Override
	public Iterator<E> iterator()
	{
		return list.iterator();
	}
	
	public ListIterator<E> listIterator()
	{
		return list.listIterator();
	}
	
	public ListIterator<E> listIterator(int index)
	{
		return list.listIterator(index);
	}
	
	
	@Override
	public Object[] toArray()
	{
		return list.toArray();
	}
	
	@Override
	public <T> T[] toArray(T[] a)
	{
		return list.toArray(a);
	}
	
	@Override
	public boolean add(E e)
	{
		int index = Collections.binarySearch(list, e, comparator);
		if (index < 0)
		{
			index = ~index;
		}
		list.add(index, e);
		return true;
	}
	
	@Override
	public boolean remove(Object o)
	{
		return list.remove(o);
	}
	
	@Override
	public boolean containsAll(Collection<?> c)
	{
		return list.containsAll(c);
	}
	
	@Override
	public boolean addAll(Collection<? extends E> c)
	{
		boolean changed = false;
		for (E e : c)
		{
			changed |= add(e);
		}
		return changed;
	}
	
	@Override
	public boolean retainAll(Collection<?> c)
	{
		return list.retainAll(c);
	}
	
	@Override
	public boolean removeAll(Collection<?> c)
	{
		return list.removeAll(c);
	}
	
	@Override
	public void clear()
	{
		list.clear();
	}
	
	@Override
	public String toString()
	{
		return "SortedArraySet{" +
				"list=" + list +
				", comparator=" + comparator +
				'}';
	}
	
	@Override
	public SortedSet<E> subSet(E fromElement, E toElement)
	{
		int fromIndex = Collections.binarySearch(list, fromElement, comparator);
		if (fromIndex < 0) fromIndex = ~fromIndex;
		int toIndex = Collections.binarySearch(list, toElement, comparator);
		if (toIndex < 0) toIndex = ~toIndex;
		return new SortedArraySet<>(list.subList(fromIndex, toIndex), comparator);
	}
	
	@Override
	public SortedSet<E> headSet(E toElement)
	{
		int toIndex = Collections.binarySearch(list, toElement, comparator);
		if (toIndex < 0) toIndex = ~toIndex;
		return new SortedArraySet<>(list.subList(0, toIndex), comparator);
	}
	
	@Override
	public SortedSet<E> tailSet(E fromElement)
	{
		int fromIndex = Collections.binarySearch(list, fromElement, comparator);
		if (fromIndex < 0) fromIndex = ~fromIndex;
		return new SortedArraySet<>(list.subList(fromIndex, list.size()), comparator);
	}
	
}
