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

import java.util.*;
import java.util.function.Consumer;

public class QuadTreeNodeIterator<T> implements Iterator<QuadNode<T>>
{
	/** lowest numerical value, inclusive */
	private final byte highestDetailLevel;
	
	
	private final Queue<QuadNode<T>> validNodesForDetailLevel = new ArrayDeque<>();
	private final Queue<QuadNode<T>> iteratorNodeQueue = new ArrayDeque<>();
	private byte iteratorDetailLevel = 0;
	
	private final boolean onlyReturnLeafValues;
	
	
	
	public QuadTreeNodeIterator(QuadNode<T> rootNode, boolean onlyReturnLeafValues)
	{
		this.onlyReturnLeafValues = onlyReturnLeafValues;
		// TODO the naming conversion for these are flipped in a lot of places
		this.highestDetailLevel = rootNode.minimumDetailLevel;
		this.iteratorDetailLevel = DhSectionPos.getDetailLevel(rootNode.sectionPos);
		
		
		if (!this.onlyReturnLeafValues)
		{
			// set the start for the iterator
			this.validNodesForDetailLevel.add(rootNode);
			this.iteratorNodeQueue.add(rootNode);
		}
		else
		{
			// fully populate the iterator
			
			// this isn't the best way to do this, especially for large trees, 
			// but it is simple and functions well enough for now
			
			
			Queue<QuadNode<T>> parentNodeQueue = new ArrayDeque<>();
			parentNodeQueue.add(rootNode);
			
			// walk through the whole tree and add each leaf node to the iterator queue
			while (parentNodeQueue.peek() != null)
			{
				QuadNode<T> parentNode = parentNodeQueue.poll();
				for (int i = 0; i < 4; i++)
				{
					QuadNode<T> childNode = parentNode.getChildByIndex(i);
					if (childNode != null)
					{
						if (childNode.getTotalChildCount() == 0)
						{
							this.iteratorNodeQueue.add(childNode);
						}
						else
						{
							parentNodeQueue.add(childNode);
						}
					}
				}
			}
		}
		
	}// constructor
	
	
	
	@Override
	public boolean hasNext() { return this.iteratorNodeQueue.size() != 0; }
	
	@Override
	public QuadNode<T> next()
	{
		if (this.iteratorDetailLevel < this.highestDetailLevel)
		{
			throw new NoSuchElementException("Highest detail level reached [" + this.highestDetailLevel + "].");
		}
		if (this.iteratorNodeQueue.size() == 0)
		{
			throw new NoSuchElementException();
		}
		
		
		// get the current iterator node
		QuadNode<T> currentNode = this.iteratorNodeQueue.poll();
		
		
		// the iterator queue should be fully populated for leaf values,
		// for all values, it needs to be populated for each detail level
		if (this.iteratorNodeQueue.size() == 0 && !onlyReturnLeafValues)
		{
			// populate the next detail level list
			
			this.iteratorDetailLevel--;
			// only continue if we can go down farther
			if (this.iteratorDetailLevel >= this.highestDetailLevel)
			{
				Queue<QuadNode<T>> parentNodes = new LinkedList<>(this.validNodesForDetailLevel);
				this.validNodesForDetailLevel.clear();
				
				// populate the list of nodes for this level
				for (QuadNode<T> parentNode : parentNodes)
				{
					for (int i = 0; i < 4; i++)
					{
						QuadNode<T> childNode = parentNode.getChildByIndex(i);
						if (childNode != null)
						{
							this.iteratorNodeQueue.add(childNode);
							this.validNodesForDetailLevel.add(childNode);
						}
					}
				}
			}
		}
		
		return currentNode;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(Consumer<? super QuadNode<T>> action) { Iterator.super.forEachRemaining(action); }
	
}
