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

package com.seibel.distanthorizons.core.util.objects.quadTree.iterators;

import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class QuadNodeDirectChildIterator<T> implements Iterator<QuadNode<T>>
{
	private final QuadNodeChildIndexIterator<T> childIndexIterator;
	private final QuadNode<T> parentNode;
	
	
	public QuadNodeDirectChildIterator(QuadNode<T> parentNode)
	{
		this.parentNode = parentNode;
		this.childIndexIterator = new QuadNodeChildIndexIterator<>(this.parentNode, false);
	}
	
	
	
	@Override
	public boolean hasNext() { return this.childIndexIterator.hasNext(); }
	
	@Override
	public QuadNode<T> next()
	{
		if (!this.hasNext())
		{
			throw new NoSuchElementException();
		}
		
		
		int childIndex = this.childIndexIterator.next();
		QuadNode<T> node = this.parentNode.getChildByIndex(childIndex);
		return node;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(Consumer<? super QuadNode<T>> action) { Iterator.super.forEachRemaining(action); }
	
}