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

package com.seibel.distanthorizons.core.util;

import com.google.common.annotations.VisibleForTesting;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListParent;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.util.LodUtil.AssertFailureException;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrays;

/**
 * A list of data points whose sole purpose is to {@link #reduce(int)} them.
 * Each data point, henceforth referred to as a "node", is represented by 2 packed longs.
 * The "data" long contains the data point itself, as encoded by
 * {@link RenderDataPointUtil#createDataPoint(int, int, int, int, int, int, int, int, int)}.
 * The "links" long contains 4 packed 16-bit integers, which "point" to other nodes
 * In the sense that the index represented by the integer is another node in this list.
 * The 4 links are: bigger, smaller, higher, and lower.
 * All nodes are stored in 2 parallel long[]'s, namely {@link #data} and {@link #links}. <br><br>
 *
 * All nodes are internally sorted in 2 different orders at the same time:
 * lowest-to-highest, and smallest-to-biggest.
 * Both of these orders are important for reduction logic.
 * Traversal in both orders is equally possible and important.
 *
 * @author Builderb0y
 */
public class RenderDataPointReducingList extends PhantomArrayListParent
{

	/**
	 * Setting this to true will cause the list to sanity-check
	 * its own links automatically every time it modifies itself.
	 * This is mostly just useful for debugging.
	 * This should be set to false in production,
	 * because these sanity checks are slow and happen often.
	 */
	private static final boolean ASSERTS = false;
	/**
	 * Number of special cases to use for step 1 of {@link #reduce(int)}.
	 * 2 works well for big globe worlds.
	 * 0 is probably better for vanilla, but vanilla has vastly fewer segments/nodes
	 * than big globe does, so the difference in efficiency matters a lot less.
	 */
	private static final int SPECIAL_CASES = 2;
	
	/** the bit offset of {@link #links} where the lower link is stored. */
	public static final int LOWER_SHIFT = 0;
	/** the bit offset of {@link #links} where the higher link is stored. */
	public static final int HIGHER_SHIFT = 16;
	/** the bit offset of {@link #links} where the smaller link is stored. */
	public static final int SMALLER_SHIFT = 32;
	/** the bit offset of {@link #links} where the bigger link is stored. */
	public static final int BIGGER_SHIFT = 48;
	/**
	 * a bit mask for extracting links from elements of {@link #links}.
	 * all links are 16 bits in length, so this constant has the lower 16 bits set,
	 * and all remaining bits cleared.
	 */
	public static final int LINK_MASK = 0xFFFF;
	/** a constant to indicate that a link is non-existent. */
	public static final int NULL = LINK_MASK;
	
	/** the default element of {@link #data} to indicate that there is no data. */
	public static final long DEFAUlT_DATA  =  0L;
	/** the default element of {@link #links} to indicate that a node is not linked to any other nodes. */
	public static final long DEFAULT_LINKS = -1L;
	
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Render Reducer");
	
	
	/**
	 * indexes of the nodes at the ends of this list.
	 * access these fields through the getters,
	 * not by the backing fields. the getters will
	 * perform automatic short <-> int conversions.
	 *
	 * @implNote these fields behave as if they were unsigned,
	 * and the getters will behave accordingly.
	 * not that DH supports a wide enough Y range
	 * to overflow these fields, but still.
	 */
	private short lowest, highest, smallest, biggest;
	private short sizeWithAir, sizeWithoutAir;
	private final LongArrayList links, data;
	/**
	 * a temporary array to be used for sorting nodes.
	 * the array is first populated such that every index
	 * up to our current size represents a valid index.
	 * then this array is sorted.
	 * finally, the nodes are re-linked according
	 * to the order of elements in this array.
	 */
	private final ShortArrayList sortingArray;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public RenderDataPointReducingList(IColumnDataView view) 
	{
		super(ARRAY_LIST_POOL, 0, 1, 2);
		
		int size = view.size();
		if (size == 0) 
		{
			this.setLowest(NULL);
			this.setHighest(NULL);
			this.setSmallest(NULL);
			this.setBiggest(NULL);
			this.links = this.pooledArraysCheckout.getLongArray(0, 0);
			this.data = this.pooledArraysCheckout.getLongArray(1, 0);
			this.sortingArray = this.pooledArraysCheckout.getShortArray(0, 0);
			if (ASSERTS) this.checkLinks();
			
			return;
		}
		
		// Allocate an array big enough to hold (2 * size - 1) nodes.
		// This is the number of nodes we would have if none
		// of the nodes in the provided view are touching,
		// and we need to add air nodes between all of them.
		// We will use this array for sorting the nodes,
		// first by lowest-to-highest, then by smallest-to-biggest.
		int arrayCapacity = (size << 1) - 1;
		this.sortingArray = this.pooledArraysCheckout.getShortArray(0, arrayCapacity);
		this.links = this.pooledArraysCheckout.getLongArray(0, arrayCapacity);
		java.util.Arrays.fill(this.links.elements(), DEFAULT_LINKS);
		this.data = this.pooledArraysCheckout.getLongArray(1, arrayCapacity);
		
		int sizeWithoutAir = 0;
		for (int index = 0; index < size; index++) 
		{
			long packedData = view.get(index);
			// first "pass" (if you can call it that) skips nodes with 0 height, and nodes that aren't visible.
			// air nodes will be inserted *after* the nodes have been sorted by Y level.
			if (isDataVisible(packedData) && RenderDataPointUtil.getYMin(packedData) < RenderDataPointUtil.getYMax(packedData)) 
			{
				this.setData(sizeWithoutAir, packedData);
				this.setSortingIndex(sizeWithoutAir, sizeWithoutAir);
				sizeWithoutAir++;
			}
		}

		// Check if all segments to merge are air or otherwise invisible (barriers).
		// If they are, then this list can stay empty.
		if (sizeWithoutAir == 0) 
		{
			this.setLowest(NULL);
			this.setHighest(NULL);
			this.setSmallest(NULL);
			this.setBiggest(NULL);
			if (ASSERTS) this.checkLinks();
			
			return;
		}

		//sort the nodes by Y level.
		this.sortByPosition(sizeWithoutAir);
		//next pass: link the nodes together, and insert air nodes as necessary.
		int sizeWithAir = sizeWithoutAir;
		for (int sortingIndex = 1; sortingIndex < sizeWithoutAir; sortingIndex++) 
		{
			int  lowerIndex = this.getSortingIndex(sortingIndex - 1);
			int higherIndex = this.getSortingIndex(sortingIndex);
			long  lowerData = this.getData(lowerIndex);
			long higherData = this.getData(higherIndex);
			int   lowerMaxY = RenderDataPointUtil.getYMax(lowerData);
			int  higherMinY = RenderDataPointUtil.getYMin(higherData);
			
			if (lowerMaxY == higherMinY) 
			{ 
				//the two nodes touch.
				this.setHigher(lowerIndex, higherIndex);
				this.setLower(higherIndex, lowerIndex);
			}
			else if (lowerMaxY < higherMinY)
			{ 
				//the two nodes do not touch.
				this.setData(
					sizeWithAir,
					RenderDataPointUtil.createDataPoint(
						0,
						0,
						0,
						0,
						higherMinY,
						lowerMaxY,
						RenderDataPointUtil.getLightSky(higherData),
						RenderDataPointUtil.getLightBlock(higherData),
						RenderDataPointUtil.getBlockMaterialId(higherData)
					)
				);
				
				this.setSortingIndex(sizeWithAir, sizeWithAir);
				this.setLower(higherIndex, sizeWithAir);
				this.setHigher(lowerIndex, sizeWithAir);
				this.setLower(sizeWithAir, lowerIndex);
				this.setHigher(sizeWithAir, higherIndex);
				sizeWithAir++;
			}
			else 
			{ 
				// the two nodes overlap.
				throw new IllegalArgumentException(RenderDataPointUtil.toString(lowerData) + " overlaps with " + RenderDataPointUtil.toString(higherData));
			}
		}
		this.lowest  = this.sortingArray.getShort(0);
		this.highest = this.sortingArray.getShort(sizeWithoutAir - 1);

		// now sort by size.
		this.sortBySize(sizeWithAir);
		for (int sortingIndex = 1; sortingIndex < sizeWithAir; sortingIndex++) 
		{
			int smallerIndex = this.getSortingIndex(sortingIndex - 1);
			int  biggerIndex = this.getSortingIndex(sortingIndex);
			this.setBigger(smallerIndex, biggerIndex);
			this.setSmaller(biggerIndex, smallerIndex);
		}
		
		this.smallest = this.sortingArray.getShort(0);
		this.biggest = this.sortingArray.getShort(sizeWithAir - 1);

		this.setSizeWithAir(sizeWithAir);
		this.setSizeWithoutAir(sizeWithoutAir);

		if (ASSERTS) this.checkLinks();
	}
	
	
	
	//========//
	// reduce //
	//========//
	
	/**
	 * merges and/or eliminates nodes until our {@link #sizeWithoutAir}
	 * is less than or equal to the provided target size.
	 * this method assumes that the list is already sorted by size.
	 * if it is not sorted, you should call {@link #sortBySizeAndReLink()} first.
	 * note also that the list is sorted in its constructor,
	 * so if this is a new, unmodified list, then it is safe to call this method. <br><br>
	 *
	 * algorithm:
	 * 	1: try to merge the smallest segment with the segment above or below it.
	 * 		this will only succeed if the adjacent node has the same alpha as it.
	 * 		1a: if there is only one adjacent node which matches this criteria,
	 * 			we will merge with that node. <br><br>
	 *
	 * 		1b: if both adjacent nodes match this criteria,
	 * 			attempt to merge with the smaller one.
	 * 			1b1: if both adjacent nodes are the same height,
	 * 				merge with the higher one. <br><br>
	 *
	 * 		1c: if there are no adjacent nodes which match this criteria,
	 * 			repeat step 1 with the next smallest segment instead.
	 * 			continue trying bigger and bigger segments until we either:
	 * 				* have a success, or
	 * 				* reach the end of this list.
	 * 	2: if we reach the end of the list before having a success, try again,
	 * 		but this time, we are allowed to erase a segment entirely without merging it
	 * 		if there are equal-alpha'd segments above and below it.
	 * 	3: if we still fail, force the lowest segment to merge with the segment above it,
	 * 		with no restrictions on alpha.
	 * 		the highest alpha of the two segments takes priority though.
	 * 	4: repeat until our size is less than or equal to the target size. <br><br>
	 * 	
	 *  <b>notes:</b> <br>
	 * 	changing the size of a node requires re-sorting that node,
	 * 	but it does not require re-sorting the whole list.
	 * 	additionally, because of the fact that nodes are sorted smallest to biggest,
	 * 	when a node is expanded, its new size will be
	 * 	strictly less than or equal to twice its old size.
	 * 	the significance of this is that in practice,
	 * 	nodes should not need to be moved very far to be re-sorted. <br><br>
	 *
	 * 	special case: there are a lot of segments of length 1 in big globe worlds.
	 * 	these will genuinely have a long way to move on re-sort.
	 * 	so, they are handled in a separate loop. <br><br>
	 *
	 * 	after step 1 is completed, step 2 can't change the
	 * 	list in a way which would give step 1 more work to do,
	 * 	so step 2 is repeated as many times as necessary,
	 * 	without jumping back to the start.
	 * 	step 3 however can change the list in a way which gives previous
	 * 	steps more work to do, so after step 3 merges something,
	 * 	we jump back to step 1 and start over.
	 */
	public void reduce(int target)
	{
		if (this.mergeVerySmallConnectedSegments(target)) return;
		
		if (this.mergeConnectedSegments(target)) return;
		if (this.removeLeastImportantSegments(target)) return;
		this.forceBottomToMerge(target);
	}
	
	
	
	//======================//
	// reduction operations //
	//======================//
	
	/**
	 * verifies that this list is in the "correct" state,
	 * and throws an {@link AssertFailureException} if it isn't.
	 */
	@VisibleForTesting
	public void checkLinks() 
	{
		LodUtil.assertTrue(this.getSizeWithAir() >= 0, "size with air < 0");
		LodUtil.assertTrue(this.getSizeWithoutAir() >= 0, "size without air < 0");
		LodUtil.assertTrue(this.getSizeWithoutAir() <= this.getSizeWithAir(), "more segments without air than with air");
		
		if (this.getSizeWithAir() == 0) 
		{
			LodUtil.assertTrue(this.getSmallest() == NULL, "size is 0, but we have a smallest node");
			LodUtil.assertTrue(this.getBiggest()  == NULL, "size is 0, but we have a biggest node");
			LodUtil.assertTrue(this.getLowest()   == NULL, "size is 0, but we have a lowest node");
			LodUtil.assertTrue(this.getHighest()  == NULL, "size is 0, but we have a highest node");
		}
		else 
		{
			int sizeWithAir = 0, sizeWithoutAir = 0;
			for (int index = this.getSmallest(); index != NULL; index = this.getBigger(index)) 
			{
				int smaller = this.getSmaller(index);
				int bigger = this.getBigger(index);
				LodUtil.assertTrue((smaller != NULL ? this.getBigger(smaller) : this.getSmallest()) == index, "one-way link");
				LodUtil.assertTrue((bigger != NULL ? this.getSmaller(bigger) : this.getBiggest()) == index, "one-way link");
				LodUtil.assertTrue(smaller == NULL || this.getSize(index) >= this.getSize(smaller), "node is not sorted by size");
				sizeWithAir++;
				
				if (this.isIndexVisible(index)) sizeWithoutAir++;
			}
			LodUtil.assertTrue(sizeWithAir == this.getSizeWithAir() && sizeWithoutAir == this.getSizeWithoutAir(), "node count does not match size");

			sizeWithAir = sizeWithoutAir = 0;
			for (int index = this.getLowest(); index != NULL; index = this.getHigher(index)) 
			{
				int lower = this.getLower(index);
				int higher = this.getHigher(index);
				LodUtil.assertTrue((lower != NULL ? this.getHigher(lower) : this.getLowest()) == index, "one-way link");
				LodUtil.assertTrue((higher != NULL ? this.getLower(higher) : this.getHighest()) == index, "one-way link");
				LodUtil.assertTrue(this.getMaxY(index) > this.getMinY(index), "node has inverted Y levels");
				LodUtil.assertTrue(lower == NULL || this.getMinY(index) == this.getMaxY(lower), "node does not touch its lower neighbor");
				sizeWithAir++;
				
				if (this.isIndexVisible(index)) sizeWithoutAir++;
			}
			LodUtil.assertTrue(sizeWithAir == this.getSizeWithAir() && sizeWithoutAir == this.getSizeWithoutAir(), "node count does not match size");
		}
	}

	/** removes the node at the given index from this list. */
	public void remove(int index) 
	{
		int lower  = this.getLower  (index);
		int higher = this.getHigher (index);
		int smaller= this.getSmaller(index);
		int bigger = this.getBigger (index);
		int alpha  = this.getAlpha  (index);
		
		
		if (lower != NULL) this.setHigher(lower, higher);
		else this.setLowest(higher);
		
		if (higher != NULL) this.setLower(higher, lower);
		else this.setHighest(lower);
		
		if (smaller != NULL) this.setBigger(smaller, bigger);
		else this.setSmallest(bigger);
		
		if (bigger != NULL) this.setSmaller(bigger, smaller);
		else this.setBiggest(smaller);
		
		this.setData(index, DEFAUlT_DATA);
		this.links.set(index, DEFAULT_LINKS);
		this.sizeWithAir--;
		
		if (isAlphaVisible(alpha)) this.sizeWithoutAir--;
	}
	
	/**
	 * refreshes the smallest-to-biggest order of this list.
	 * as a reminder, the list is internally sorted from smallest-to-biggest
	 * and lowest-to-highest at the same time. part of reduction logic
	 * can invalidate the smallest-to-biggest order, so this method re-computes it.
	 * this method does not touch the lowest-to-highest order of the list. <br><br>
	 *
	 * this method requires that all nodes are already sorted from
	 * lowest-to-highest, so it is not applicable to use this method in
	 * the constructor before the lowest-to-highest order is initialized.
	 */
	@VisibleForTesting
	public void sortBySizeAndReLink() 
	{
		if (this.getSizeWithAir() <= 1)
		{
			return;
		}
		
		
		LongArrayList datas = this.data;
		int writeIndex = 0;
		for (int readIndex = this.getLowest(); readIndex != NULL; readIndex = this.getHigher(readIndex)) 
		{
			if (datas.getLong(readIndex) != DEFAUlT_DATA) 
			{
				this.setSortingIndex(writeIndex++, readIndex);
			}
		}
		
		this.sortBySize(writeIndex);
		for (int index = 1; index < writeIndex; index++) 
		{
			int smaller = this.getSortingIndex(index - 1);
			int bigger = this.getSortingIndex(index);
			this.setSmaller(bigger, smaller);
			this.setBigger(smaller, bigger);
		}
		
		this.smallest = this.sortingArray.getShort(0);
		this.biggest = this.sortingArray.getShort(writeIndex - 1);
		this.setSmaller(this.getSmallest(), NULL);
		this.setBigger(this.getBiggest(), NULL);
	}
	
	/**
	 * sorts our {@link #sortingArray} in order of smallest-to-biggest,
	 * but does NOT update our links accordingly.
	 */
	@VisibleForTesting
	public void sortBySize(int size) 
	{
		ShortArrayList array = this.sortingArray;
		it.unimi.dsi.fastutil.Arrays.quickSort(
			0,
			size,
			// comparator
			(int index1, int index2) -> 
			{
				return Integer.compare(
					this.getSize(this.getSortingIndex(index1)),
					this.getSize(this.getSortingIndex(index2))
				);
			},
			// swapper
			(int index1, int index2) -> 
			{
				ShortArrays.swap(array.elements(), index1, index2);
			}
		);
	}
	
	/**
	 * sorts our {@link #sortingArray} in order of lowest-to-highest,
	 * but does NOT update our links accordingly.
	 */
	@VisibleForTesting
	public void sortByPosition(int size) 
	{
		ShortArrayList array = this.sortingArray;
		it.unimi.dsi.fastutil.Arrays.quickSort(
			0,
			size,
			// comparator
			(int index1, int index2) -> 
			{
				return Integer.compare(
					this.getMinY(this.getSortingIndex(index1)),
					this.getMinY(this.getSortingIndex(index2))
				);
			},
			// swapper
			(int index1, int index2) -> 
			{
				ShortArrays.swap(array.elements(), index1, index2);
			}
		);
	}
	
	/**
	 * moves the smaller node to the correct position in the list,
	 * under the assumption that all other nodes are already sorted.
	 * this method should be called when the smaller node is
	 * merged with another node, causing it to become bigger. <br><br>
	 *
	 * important: this method ONLY handles the case where a node
	 * is made bigger. it does NOT handle the case where a node
	 * is made smaller. if the node is made smaller, it will be
	 * left in its current position, even if that position is wrong.
	 */
	public void resortSize(int smaller) 
	{
		int bigger = this.getBigger(smaller);
		
		// check if the node needs to be moved at all,
		// and return if it doesn't.
		if (bigger == NULL || this.getSize(smaller) <= this.getSize(bigger))
		{
			return;
		}
		
		// first remove smaller from before bigger.
		int smallest = this.getSmaller(smaller);
		if (smallest != NULL) this.setBigger(smallest, bigger);
		else this.setSmallest(bigger);
		this.setSmaller(bigger, smallest);
		
		// next, find the position to re-insert the node.
		do bigger = this.getBigger(bigger);
		while (bigger != NULL && this.getSize(smaller) > this.getSize(bigger));
		
		// lastly, re-insert the node where it belongs.
		this.setSmaller(smaller, bigger != NULL ? this.getSmaller(bigger) : this.getBiggest());
		this.setBigger(smaller, bigger);
		
		if (bigger != NULL) this.setSmaller(bigger, smaller);
		else this.setBiggest(smaller);
		
		smallest = this.getSmaller(smaller);
		if (smallest != NULL) this.setBigger(smallest, smaller);
		else this.setSmallest(smaller);
	}
	
	/**
	 * shared logic for merging segments in step 1 documented in {@link #reduce(int)}. <br<br>
	 *
	 * returns the index of the next node to be used for iteration. <br<br>
	 *
	 * @param fastPath if true, we are in the "fast path" for removing
	 * segments whose size is less than or equal to {@link #SPECIAL_CASES}.
	 * this fast path functions somewhat differently from the normal path,
	 * the important things to note for this method are: <br<br>
	 *
	 * the fast path does not re-sort nodes when their size changes.
	 * this leaves the list in an invalid state, and it is up to the caller to re-sort
	 * the list via {@link #sortBySizeAndReLink()} after the fast path is done. <br<br>
	 *
	 * at the time of writing this, the fast path iterates in reverse order.
	 * as such, when fastPath is set to true, this method will return
	 * current's smaller neighbor, when fastPath is set to false,
	 * this method will return current's bigger neighbor instead.
	 */
	private int tryMergeStep1(int current, boolean fastPath) 
	{
		int result = fastPath ? this.getSmaller(current) : this.getBigger(current);
		int higher = this.getHigher(current);
		int lower  = this.getLower(current);
		int toExtendDownwards;
		int toRemove;
		
		
		if (higher != NULL && this.getAlpha(higher) == this.getAlpha(current)) 
		{
			if (lower != NULL && this.getAlpha(lower) == this.getAlpha(current)) 
			{
				if (this.getSize(higher) <= this.getSize(lower)) 
				{
					toExtendDownwards = higher;
					toRemove = current;
				}
				else 
				{
					toExtendDownwards = current;
					toRemove = lower;
				}
			}
			else 
			{
				toExtendDownwards = higher;
				toRemove = current;
			}
		}
		else
		{
			if (lower != NULL && this.getAlpha(lower) == this.getAlpha(current))
			{
				toExtendDownwards = current;
				toRemove = lower;
			}
			else
			{
				return result;
			}
		}
		
		// if we're about to remove the next node for iteration,
		// then we need to continue iterating at the node after that.
		// result will only be returned if fastPath is true,
		// so the node after that is always the smaller one. that's why I don't need to do
		// if (result == toRemove) result = fastPath ? this.getSmaller(result) : this.getBigger(result);
		if (result == toRemove) result = this.getSmaller(result);
		
		this.setMinY(toExtendDownwards, this.getMinY(toRemove));
		if (!fastPath) this.resortSize(toExtendDownwards);
		
		this.remove(toRemove);
		
		// if we're NOT on the fast path, and we reach this line,
		// then we have just modified the list in a way which may
		// invalidate assumptions made by the step 1 loop.
		// so, return smallest to signal that the loop should start over.
		// starting over is not usually a big deal,
		// because small nodes are usually merged quite quickly.
		// in my testing, I didn't see the step 1 loop run more
		// than twice as many times as the starting list size.
		return fastPath ? result : this.getSmallest();
	}
	
	/**
	 * returns the largest node whose height is strictly less than the provided size,
	 * or null if all contained nodes are greater than or equal to the provided size.
	 *
	 * special cases:
	 * if the list is empty, then null is returned,
	 * because the loop will not run and biggest will be null.
	 *
	 * if all nodes are less tall than size, then the largest node is returned,
	 * because the loop will run for all nodes, but will not return any of them,
	 * so the fallback path of returning the biggest node is used.
	 *
	 * if all nodes are at least as tall as size, then null is returned,
	 * because the loop will immediately return the
	 * smallest node's smaller neighbor, which is null.
	 */
	private int lowerNode(int size) 
	{
		for (int node = this.getSmallest(); node != NULL; node = this.getBigger(node)) 
		{
			if (this.getSize(node) >= size)
			{
				return this.getSmaller(node);
			}
		}
		return this.getBiggest();
	}
	
	/**
	 * handles special cases for step 1 of {@link #reduce(int)}.
	 * in other words, handles all the nodes whose size
	 * is less than or equal to {@link #SPECIAL_CASES}. <br><br>
	 *
	 * returns true if this step single-handedly brought
	 * the list's size down to less than or equal to target,
	 * or false if more steps need to be performed.
	 */
	private boolean mergeVerySmallConnectedSegments(int target)
	{
		for (int specialCase = 1; specialCase <= SPECIAL_CASES; specialCase++)
		{
			for (int current = this.lowerNode(specialCase + 1); current != NULL; )
			{
				if (this.getSizeWithoutAir() <= target)
				{
					this.sortBySizeAndReLink();
					if (ASSERTS) this.checkLinks();
					
					return true;
				}
				current = this.tryMergeStep1(current, true);
			}
			this.sortBySizeAndReLink();
			
			if (ASSERTS) this.checkLinks();
		}
		
		return false;
	}
	
	/**
	 * handles the general case for step 1 of {@link #reduce(int)}.
	 * in other words, handles all the nodes whose size
	 * is strictly greater than {@link #SPECIAL_CASES},
	 * and all the nodes which are smaller, but failed
	 * to be merged in {@link #mergeVerySmallConnectedSegments(int)}
	 *
	 * returns true if this step single-handedly brought
	 * the list's size down to less than or equal to target,
	 * or false if more steps need to be performed.
	 */
	private boolean mergeConnectedSegments(int target) 
	{
		for (int current = this.getSmallest(); current != NULL;) 
		{
			if (this.getSizeWithoutAir() <= target)
			{
				return true;
			}
			
			current = this.tryMergeStep1(current, false);
			if (ASSERTS) this.checkLinks();
		}
		return false;
	}
	
	/**
	 * handles step 2 of {@link #reduce(int)}, where nodes are allowed to be erased. <br><br>
	 *
	 * returns true if this step single-handedly brought
	 * the list's size down to less than or equal to target,
	 * or false if more steps need to be performed.
	 */
	private boolean removeLeastImportantSegments(int target)
	{
		for (int center = this.getSmallest(); center != NULL; )
		{
			if (this.getSizeWithoutAir() <= target)
			{
				return true;
			}
			
			int lower = this.getLower(center);
			int higher = this.getHigher(center);
			if (lower != NULL && higher != NULL && this.getAlpha(lower) == this.getAlpha(higher))
			{
				this.setMinY(higher, this.getMinY(lower));
				this.resortSize(higher);
				this.remove(lower);
				this.remove(center);
				if (ASSERTS) this.checkLinks();
				
				center = this.getSmallest();
			}
			else
			{
				center = this.getBigger(center);
			}
		}
		return false;
	}
	
	/**
	 * handles step 3 of {@link #reduce(int)}, where nodes
	 * are forced to merge in order to fit the desired target,
	 * even if they normally shouldn't merge because it would look bad. <br><br>
	 *
	 * returns after this step brings the list's
	 * size down to less than or equal to target.
	 */
	private void forceBottomToMerge(int target)
	{
		for (int lowest = this.getLowest(); lowest != NULL; )
		{
			if (this.getSizeWithoutAir() <= target)
			{
				return;
			}
			
			int lowY = this.getMinY(lowest);
			int higher = this.getHigher(lowest);
			inner:
			while (true)
			{
				if (higher == NULL)
				{
					//if we reach this line, then target is 0 or negative.
					this.setLowest(NULL);
					this.setHighest(NULL);
					this.setSmallest(NULL);
					this.setBiggest(NULL);
					this.setSizeWithAir(0);
					this.setSizeWithoutAir(0);
					
					if (ASSERTS) this.checkLinks();
					
					return;
				}
				
				// don't merge the lowest segment with an invisible segment.
				// in other words, we don't want
				//   visible
				//   invisible
				//   visible
				// to be replaced with
				//   visible
				//   invisible
				// instead, we want to eliminate the invisible segment too,
				// and set the minY of the top visible segment
				// to the minY of the bottom visible segment.
				if (this.isIndexVisible(higher))
				{
					this.setMinY(higher, lowY);
					this.resortSize(higher);
					this.remove(lowest);
					
					if (ASSERTS) this.checkLinks();
					
					lowest = this.getLowest();
					break inner;
				}
				else
				{
					this.remove(lowest);
					lowest = higher;
					higher = this.getHigher(higher);
					//don't update lowY.
				}
			}
		}
	}
	
	/**
	 * reduces the view to a single data point,
	 * whose min Y is the lowest of all data points in the provided view,
	 * and every other property of the returned data point
	 * matches those of the data point with the highest
	 * Y level in the provided view.
	 *
	 * @implNote this method does not allocate any objects.
	 */
	public static long reduceToOne(IColumnDataView view) 
	{
		int size = view.size();
		if (size <= 0)
		{
			return RenderDataPointUtil.EMPTY_DATA;
		}
		
		long highestDataPoint;
		long lowestDataPoint;
		int index = 0;
		//first loop: find the first visible segment.
		foundVisible:
		{
			for (; index < size; index++)
			{
				long dataPoint = view.get(index);
				if (isDataVisible(dataPoint))
				{
					highestDataPoint = dataPoint;
					lowestDataPoint = dataPoint;
					break foundVisible;
				}
			}
			//no visible segments, return void.
			return RenderDataPointUtil.EMPTY_DATA;
		}
		
		//second loop: merge the rest of the segments.
		for (; index < size; index++)
		{
			long dataPoint = view.get(index);
			if (isDataVisible(dataPoint))
			{
				int yMax = RenderDataPointUtil.getYMax(dataPoint);
				int yMin = RenderDataPointUtil.getYMin(dataPoint);
				
				if (yMax > RenderDataPointUtil.getYMax(highestDataPoint)) highestDataPoint = dataPoint;
				else if (yMin < RenderDataPointUtil.getYMin(lowestDataPoint)) lowestDataPoint = dataPoint;
			}
		}
		
		return (highestDataPoint & ~RenderDataPointUtil.DEPTH_SHIFTED_MASK) | (RenderDataPointUtil.getYMin(lowestDataPoint) << RenderDataPointUtil.DEPTH_SHIFT);
	}
	
	
	/** transfers the contents of this list to the provided view, in order of highest to lowest. */
	public void copyTo(ColumnArrayView view)
	{
		// reminder: DH explodes horribly when I copy the nodes
		// from lowest to highest instead of highest to lowest.
		int writeIndex = 0;
		for (int node = this.getHighest(); node != NULL; node = this.getLower(node))
		{
			if (this.isIndexVisible(node))
			{
				view.set(writeIndex++, this.getData(node));
			}
		}
		
		// this list could be empty if all the segments for merging are invisible,
		// but we must ensure that the view is non-empty.
		// so, if we didn't set any data points, add a void data point.
		if (writeIndex == 0)
		{
			view.set(writeIndex++, RenderDataPointUtil.EMPTY_DATA);
		}
		
		for (int size = view.size(); writeIndex < size; writeIndex++)
		{
			view.set(writeIndex, RenderDataPointUtil.EMPTY_DATA);
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public int getSmallest() { return Short.toUnsignedInt(this.smallest); }
	public int getBiggest() { return Short.toUnsignedInt(this.biggest); }
	
	public int getLowest() { return Short.toUnsignedInt(this.lowest); }
	public int getHighest() { return Short.toUnsignedInt(this.highest); }
	
	public int getSizeWithAir() { return Short.toUnsignedInt(this.sizeWithAir); }
	public int getSizeWithoutAir() { return Short.toUnsignedInt(this.sizeWithoutAir); }
	
	public int getSortingIndex(int index) { return Short.toUnsignedInt(this.sortingArray.getShort(index)); }
	
	public int getLower(int index) { return ((int) (this.links.getLong(index) >>> LOWER_SHIFT)) & LINK_MASK; }
	public int getHigher(int index) { return ((int) (this.links.getLong(index) >>> HIGHER_SHIFT)) & LINK_MASK; }
	
	public int getSmaller(int index) { return ((int) (this.links.getLong(index) >>> SMALLER_SHIFT)) & LINK_MASK; }
	public int getBigger(int index) { return ((int) (this.links.getLong(index) >>> BIGGER_SHIFT)) & LINK_MASK; }
	
	public long getData(int index) { return this.data.getLong(index); }
	
	public int getMinY(int index) { return RenderDataPointUtil.getYMin(this.getData(index)); }
	public int getMaxY(int index) { return RenderDataPointUtil.getYMax(this.getData(index)); }
	
	public int getSize(int index)
	{
		long data = this.getData(index);
		return RenderDataPointUtil.getYMax(data) - RenderDataPointUtil.getYMin(data);
	}
	
	public int getRed(int index) { return RenderDataPointUtil.getRed(this.getData(index)); }
	public int getGreen(int index) { return RenderDataPointUtil.getGreen(this.getData(index)); }
	public int getBlue(int index) { return RenderDataPointUtil.getBlue(this.getData(index)); }
	public int getAlpha(int index) { return RenderDataPointUtil.getAlpha(this.getData(index)); }
	
	public int getBlockLight(int index) { return RenderDataPointUtil.getLightBlock(this.getData(index)); }
	public int getSkyLight(int index) { return RenderDataPointUtil.getLightSky(this.getData(index)); }
	
	
	
	//=========//
	// setters //
	//=========//
	
	public void setSmallest(int smallest) { this.smallest = (short)(smallest); }
	public void setBiggest(int biggest) { this.biggest = (short)(biggest); }
	
	public void setLowest(int lowest) { this.lowest = (short)(lowest); }
	public void setHighest(int highest) { this.highest = (short)(highest); }
	
	public void setSizeWithAir(int sizeWithAir) { this.sizeWithAir = (short)(sizeWithAir); }
	public void setSizeWithoutAir(int sizeWithoutAir) { this.sizeWithoutAir = (short)(sizeWithoutAir); }

	public void setSortingIndex(int index, int to) { this.sortingArray.set(index, (short)to); }

	public void setLower(int index, int lowerIndex) 
	{
		this.links.set(index, (this.links.getLong(index) & ~(((long)(LINK_MASK)) << LOWER_SHIFT)) | (((long)(lowerIndex & LINK_MASK)) << LOWER_SHIFT));
	}
	public void setHigher(int index, int higherIndex) 
	{
		this.links.set(index, (this.links.getLong(index) & ~(((long)(LINK_MASK)) << HIGHER_SHIFT)) | (((long)(higherIndex & LINK_MASK)) << HIGHER_SHIFT));
	}

	public void setSmaller(int index, int smallerIndex)
	{
		this.links.set(index, (this.links.getLong(index) & ~(((long)(LINK_MASK)) << SMALLER_SHIFT)) | (((long)(smallerIndex & LINK_MASK)) << SMALLER_SHIFT));
	}
	public void setBigger(int index, int biggerIndex) 
	{
		this.links.set(index, (this.links.getLong(index) & ~(((long)(LINK_MASK)) << BIGGER_SHIFT)) | (((long)(biggerIndex & LINK_MASK)) << BIGGER_SHIFT));
	}

	public void setData(int index, long data) { this.data.set(index, data); }

	public void setMinY(int index, int minY) 
	{
		this.data.set(index, (this.data.getLong(index) & ~RenderDataPointUtil.DEPTH_SHIFTED_MASK) | ((minY & RenderDataPointUtil.DEPTH_MASK) << RenderDataPointUtil.DEPTH_SHIFT));
	}
	public void setMaxY(int index, int maxY) 
	{
		this.data.set(index, (this.data.getLong(index) & ~RenderDataPointUtil.HEIGHT_SHIFTED_MASK) | ((maxY & RenderDataPointUtil.HEIGHT_MASK) << RenderDataPointUtil.HEIGHT_SHIFT));
	}

	public void setRed(int index, int red) 
	{
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.RED_MASK << RenderDataPointUtil.RED_SHIFT)) | ((red & RenderDataPointUtil.RED_MASK) << RenderDataPointUtil.RED_SHIFT));
	}
	public void setGreen(int index, int green) 
	{
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.GREEN_MASK << RenderDataPointUtil.GREEN_SHIFT)) | ((green & RenderDataPointUtil.GREEN_MASK) << RenderDataPointUtil.GREEN_SHIFT));
	}

	public void setBlue(int index, int blue) {
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.BLUE_MASK << RenderDataPointUtil.BLUE_SHIFT)) | ((blue & RenderDataPointUtil.BLUE_MASK) << RenderDataPointUtil.BLUE_SHIFT));
	}
	public void setAlpha(int index, int alpha) 
	{
		alpha >>>= RenderDataPointUtil.ALPHA_DOWNSIZE_SHIFT;
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.ALPHA_MASK << RenderDataPointUtil.ALPHA_SHIFT)) | ((alpha & RenderDataPointUtil.ALPHA_MASK) << RenderDataPointUtil.ALPHA_SHIFT));
	}
	
	public void setBlockLight(int index, int blockLight) 
	{
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.BLOCK_LIGHT_MASK << RenderDataPointUtil.BLOCK_LIGHT_SHIFT)) | ((blockLight & RenderDataPointUtil.BLOCK_LIGHT_MASK) << RenderDataPointUtil.BLOCK_LIGHT_SHIFT));
	}
	public void setSkyLight(int index, int skyLight) 
	{
		this.data.set(index, (this.data.getLong(index) & ~(RenderDataPointUtil.SKY_LIGHT_MASK << RenderDataPointUtil.SKY_LIGHT_SHIFT)) | ((skyLight & RenderDataPointUtil.SKY_LIGHT_MASK) << RenderDataPointUtil.SKY_LIGHT_SHIFT));
	}
	
	
	//================//
	// helper methods //
	//================//
	
	public boolean isIndexVisible(int index) { return isDataVisible(this.getData(index)); }

	public static boolean isDataVisible(long data) { return isAlphaVisible(RenderDataPointUtil.getAlpha(data)); }

	public static boolean isAlphaVisible(int alpha) { return alpha >= 16; }
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString() 
	{
		StringBuilder builder = new StringBuilder(this.sizeWithAir << 8).append("lowest to highest:");
		for (int index = this.lowest; index != NULL; index = this.getHigher(index))
		{
			builder.append('\n').append(RenderDataPointUtil.toString(this.getData(index)));
		}
		
		builder.append("\nsmallest to biggest:");
		for (int index = this.smallest; index != NULL; index = this.getBigger(index))
		{
			builder.append('\n').append(RenderDataPointUtil.toString(this.getData(index)));
		}
		
		return builder.toString();
	}
	
}