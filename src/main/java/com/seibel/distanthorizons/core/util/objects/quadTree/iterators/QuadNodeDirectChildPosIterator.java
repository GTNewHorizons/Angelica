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

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.NoSuchElementException;
import java.util.function.LongConsumer;

public class QuadNodeDirectChildPosIterator<T> implements LongIterator
{
	private final QuadNodeChildIndexIterator<T> childIndexIterator;
	private final QuadNode<T> parentNode;
	
	
	public QuadNodeDirectChildPosIterator(QuadNode<T> parentNode)
	{
		this.parentNode = parentNode;
		this.childIndexIterator = new QuadNodeChildIndexIterator<>(this.parentNode, true);
	}
	
	
	
	@Override
	public boolean hasNext() { return this.childIndexIterator.hasNext(); }
	
	@Override
	public long nextLong()
	{
		if (!this.hasNext())
		{
			throw new NoSuchElementException();
		}
		
		
		int childIndex = this.childIndexIterator.next();
		long sectionPos = DhSectionPos.getChildByIndex(this.parentNode.sectionPos, childIndex);
		return sectionPos;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(LongConsumer action) { LongIterator.super.forEachRemaining(action); }
	
}