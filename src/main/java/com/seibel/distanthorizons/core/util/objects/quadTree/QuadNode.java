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

package com.seibel.distanthorizons.core.util.objects.quadTree;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.quadTree.iterators.QuadNodeDirectChildIterator;
import com.seibel.distanthorizons.core.util.objects.quadTree.iterators.QuadNodeDirectChildPosIterator;
import com.seibel.distanthorizons.core.util.objects.quadTree.iterators.QuadTreeNodeIterator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.function.Consumer;

public class QuadNode<T>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public final long sectionPos;
	public final byte minimumDetailLevel;
	public T value;
	
	
	/**
	 * North West <br>
	 * index 0 <br>
	 * relative pos (0,0)
	 */
	public QuadNode<T> nwChild;
	/**
	 * North East <br>
	 * index 1 <br>
	 * relative (1,0)
	 */
	public QuadNode<T> neChild;
	/**
	 * South West <br>
	 * index 2 <br>
	 * relative (0,1)
	 */
	public QuadNode<T> swChild;
	/**
	 * South East <br>
	 * index 3 <br>
	 * relative (1,1)
	 */
	public QuadNode<T> seChild;
	
	
	
	public QuadNode(long sectionPos, byte minimumDetailLevel)
	{
		this.sectionPos = sectionPos;
		this.minimumDetailLevel = minimumDetailLevel;
	}
	
	
	
	/**
	 * Use {@link QuadNode#getNonNullChildCount()} if you want the number of non-null child values.
	 *
	 * @return the number of non-null child nodes
	 */
	public int getTotalChildCount()
	{
		int count = 0;
		for (int i = 0; i < 4; i++)
		{
			if (this.getChildByIndex(i) != null)
			{
				count++;
			}
		}
		return count;
	}
	
	/** @return the number of children that have non-null values */
	public int getNonNullChildCount()
	{
		int count = 0;
		for (int i = 0; i < 4; i++)
		{
			QuadNode<T> child = this.getChildByIndex(i);
			if (child != null && (child.value != null || child.getNonNullChildCount() != 0))
			{
				count++;
			}
		}
		return count;
	}
	
	
	
	/**
	 * Returns the DhLodPos 1 detail level lower <br><br>
	 *
	 * Relative child positions returned for each index: <br>
	 * 0 = (0,0) - North West <br>
	 * 1 = (1,0) - South West <br>
	 * 2 = (0,1) - North East <br>
	 * 3 = (1,1) - South East <br>
	 *
	 * @param child0to3 must be an int between 0 and 3
	 */
	public QuadNode<T> getChildByIndex(int child0to3) throws IllegalArgumentException
	{
		switch (child0to3)
		{
			case 0:
				return nwChild;
			case 1:
				return swChild;
			case 2:
				return neChild;
			case 3:
				return seChild;
			
			default:
				throw new IllegalArgumentException("child0to3 must be between 0 and 3");
		}
	}
	
	
	/**
	 * @param sectionPos must be 1 detail level lower than this node's detail level
	 * @return the node at the given position
	 * @throws IllegalArgumentException if childSectionPos has the wrong detail level or is outside the bounds of this node
	 */
	public QuadNode<T> getNode(long sectionPos) throws IllegalArgumentException { return this.getOrSetValue(sectionPos, false, null); }
	
	/**
	 * @param sectionPos must be 1 detail level lower than this node's detail level
	 * @return the value at the given position before the new value was set
	 * @throws IllegalArgumentException if childSectionPos has the wrong detail level or is outside the bounds of this node
	 */
	public T setValue(long sectionPos, T newValue) throws IllegalArgumentException
	{
		QuadNode<T> previousNode = this.getNode(sectionPos);
		if (previousNode != null)
		{
			T previousValue = previousNode.value;
			previousNode.value = newValue;
			return previousValue;
		}
		else
		{
			this.getOrSetValue(sectionPos, true, newValue);
			return null;
		}
	}
	
	/**
	 * @param inputSectionPos must be 1 detail level lower than this node's detail level
	 * @return the node at the given position before the new node was set (if the new node should be set)
	 * @throws IllegalArgumentException if childSectionPos has the wrong detail level or is outside the bounds of this
	 */
	private QuadNode<T> getOrSetValue(long inputSectionPos, boolean replaceValue, T newValue) throws IllegalArgumentException
	{
		// debug validation
		
		if (!DhSectionPos.contains(this.sectionPos, inputSectionPos))
		{
			LOGGER.error((replaceValue ? "set " : "get ") + inputSectionPos + " center block: " + DhSectionPos.getCenterBlockPos(inputSectionPos) + ", this pos: " + this.sectionPos + " this center block: " + DhSectionPos.getCenterBlockPos(this.sectionPos));
			throw new IllegalArgumentException("Input section pos " + inputSectionPos + " outside of this quadNode's pos: " + this.sectionPos + ", this node's blockPos: " + DhSectionPos.convertToDetailLevel(this.sectionPos, LodUtil.BLOCK_DETAIL_LEVEL) + " block width: " + DhSectionPos.getBlockWidth(this.sectionPos) + " input detail level: " + DhSectionPos.convertToDetailLevel(inputSectionPos, LodUtil.BLOCK_DETAIL_LEVEL) + " width: " + DhSectionPos.getBlockWidth(inputSectionPos));
		}
		
		if (DhSectionPos.getDetailLevel(inputSectionPos) > DhSectionPos.getDetailLevel(this.sectionPos))
		{
			throw new IllegalArgumentException("detail level higher than this node. Node Detail level: " + DhSectionPos.getDetailLevel(this.sectionPos) + " input detail level: " + DhSectionPos.getDetailLevel(inputSectionPos));
		}
		
		if (DhSectionPos.getDetailLevel(inputSectionPos) == DhSectionPos.getDetailLevel(this.sectionPos) && inputSectionPos != this.sectionPos)
		{
			throw new IllegalArgumentException("Node and input detail level are equal, however positions are not; this tree doesn't contain the requested position. Node pos: " + this.sectionPos + ", input pos: " + inputSectionPos);
		}
		
		if (DhSectionPos.getDetailLevel(inputSectionPos) < this.minimumDetailLevel)
		{
			throw new IllegalArgumentException("Input position is requesting a detail level lower than what this node can provide. Node minimum detail level: " + this.minimumDetailLevel + ", input pos: " + inputSectionPos);
		}
		
		
		
		// get/set logic
		if (DhSectionPos.getDetailLevel(inputSectionPos) == DhSectionPos.getDetailLevel(this.sectionPos))
		{
			// this node is the requested position
			if (replaceValue)
			{
				this.value = newValue;
			}
			return this;
		}
		else
		{
			// this node is a parent to the position requested,
			// recurse to the next node

//			LOGGER.info((replaceValue ? "set " : "get ")+inputSectionPos+" center block: "+inputSectionPos.getCenter().getCornerBlockPos()+", this pos: "+this.sectionPos+" this center block: "+this.sectionPos.getCenter().getCornerBlockPos());
			
			long nwPos = DhSectionPos.getChildByIndex(this.sectionPos, 0);
			long swPos = DhSectionPos.getChildByIndex(this.sectionPos, 1);
			long nePos = DhSectionPos.getChildByIndex(this.sectionPos, 2);
			long sePos = DhSectionPos.getChildByIndex(this.sectionPos, 3);
			
			// look for the child that contains the input position (there may be a faster way to do this, but this works for now)
			QuadNode<T> childNode;
			if (DhSectionPos.contains(nwPos, inputSectionPos))
			{
				// TODO merge duplicate code
				if (replaceValue && this.nwChild == null)
				{
					// if no node exists for this position, but we want to insert a new value at this position, create a new node
					this.nwChild = new QuadNode<>(nwPos, this.minimumDetailLevel);
				}
				childNode = this.nwChild;
				
				// childNode should only be null when replaceValue = false and the end of a node chain has been reached
				return (childNode != null) ? childNode.getOrSetValue(inputSectionPos, replaceValue, newValue) : null;
			}
			else if (DhSectionPos.contains(swPos, inputSectionPos))
			{
				// TODO merge duplicate code
				if (replaceValue && this.swChild == null)
				{
					// if no node exists for this position, but we want to insert a new value at this position, create a new node
					this.swChild = new QuadNode<>(swPos, this.minimumDetailLevel);
				}
				childNode = this.swChild;
				
				// childNode should only be null when replaceValue = false and the end of a node chain has been reached
				return (childNode != null) ? childNode.getOrSetValue(inputSectionPos, replaceValue, newValue) : null;
			}
			else if (DhSectionPos.contains(nePos, inputSectionPos))
			{
				// TODO merge duplicate code
				if (replaceValue && this.neChild == null)
				{
					// if no node exists for this position, but we want to insert a new value at this position, create a new node
					this.neChild = new QuadNode<>(nePos, this.minimumDetailLevel);
				}
				childNode = this.neChild;
				
				// childNode should only be null when replaceValue = false and the end of a node chain has been reached
				return (childNode != null) ? childNode.getOrSetValue(inputSectionPos, replaceValue, newValue) : null;
			}
			else if (DhSectionPos.contains(sePos, inputSectionPos))
			{
				// TODO merge duplicate code
				if (replaceValue && this.seChild == null)
				{
					// if no node exists for this position, but we want to insert a new value at this position, create a new node
					this.seChild = new QuadNode<>(sePos, this.minimumDetailLevel);
				}
				childNode = this.seChild;
				
				// childNode should only be null when replaceValue = false and the end of a node chain has been reached
				return (childNode != null) ? childNode.getOrSetValue(inputSectionPos, replaceValue, newValue) : null;
			}
			else
			{
				throw new IllegalStateException("input position not contained by any node children. This should've been caught by the this.sectionPos.contains(inputPos) assert before this point.");
			}
		}
	}
	
	
	
	//===========//
	// iterators //
	//===========//
	
	public Iterator<QuadNode<T>> getNodeIterator() { return new QuadTreeNodeIterator<>(this, false); }
	public Iterator<QuadNode<T>> getLeafNodeIterator() { return new QuadTreeNodeIterator<>(this, true); }
	
	/** positions can point to null children */
	public LongIterator getChildPosIterator() { return new QuadNodeDirectChildPosIterator<>(this); }
	public Iterator<QuadNode<T>> getChildNodeIterator() { return new QuadNodeDirectChildIterator<>(this); }
	
	
	
	//==========//
	// deletion //
	//==========//
	
	public void deleteAllChildren() { this.deleteAllChildren(null); }
	/** @param removedItemConsumer is only fired for non-null nodes, however the value passed in may be null */
	public void deleteAllChildren(Consumer<? super T> removedItemConsumer)
	{
		for (int i = 0; i < 4; i++)
		{
			QuadNode<T> childNode = this.getChildByIndex(i);
			if (childNode != null)
			{
				childNode.deleteAllChildren(removedItemConsumer);
			}
		}
		
		
		
		if (this.nwChild != null && removedItemConsumer != null)
		{
			removedItemConsumer.accept(this.nwChild.value);
		}
		this.nwChild = null;
		
		if (this.neChild != null && removedItemConsumer != null)
		{
			removedItemConsumer.accept(this.neChild.value);
		}
		this.neChild = null;
		
		if (this.seChild != null && removedItemConsumer != null)
		{
			removedItemConsumer.accept(this.seChild.value);
		}
		this.seChild = null;
		
		if (this.swChild != null && removedItemConsumer != null)
		{
			removedItemConsumer.accept(this.swChild.value);
		}
		this.swChild = null;
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString() { return "pos: " + DhSectionPos.toString(this.sectionPos) + ", children #: " + this.getTotalChildCount() + ", value: " + this.value; }
	
}
