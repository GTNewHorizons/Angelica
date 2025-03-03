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

package com.seibel.distanthorizons.api.enums.config;

import java.util.ArrayList;
import java.util.Collections;

import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * BLOCK <Br>
 * TWO_BLOCKS <Br>
 * FOUR_BLOCKS <br>
 * HALF_CHUNK <Br>
 * CHUNK <br>
 *
 * @author James Seibel
 * @author Leonardo Amato
 * @since API 2.0.0
 * @version 2024-4-6
 */
public enum EDhApiMaxHorizontalResolution
{
	/** render 256 LODs for each chunk */
	BLOCK(16, 0),
	
	/** render 64 LODs for each chunk */
	TWO_BLOCKS(8, 1),
	
	/** render 16 LODs for each chunk */
	FOUR_BLOCKS(4, 2),
	
	/** render 4 LODs for each chunk */
	HALF_CHUNK(2, 3),
	
	/** render 1 LOD for each chunk */
	CHUNK(1, 4);
	
	
	
	/**
	 * How many DataPoints should
	 * be drawn per side, per LodChunk
	 */
	public final int dataPointLengthCount;
	
	/** How wide each LOD DataPoint is */
	public final int dataPointWidth;
	
	/**
	 * This is the same as detailLevel in LodQuadTreeNode,
	 * lowest is 0 highest is 9
	 */
	public final byte detailLevel;
	
	/* Start/End X/Z give the block positions
	 * for each individual dataPoint in a LodChunk */
	public final int[] startX;
	public final int[] startZ;
	
	public final int[] endX;
	public final int[] endZ;
	
	
	/**
	 * 1st dimension: LodDetail.detailLevel <br>
	 * 2nd dimension: An array of all LodDetails that are less than or <br>
	 * equal to that detailLevel
	 */
	private static EDhApiMaxHorizontalResolution[][] lowerDetailArrays;
	
	
	
	
	EDhApiMaxHorizontalResolution(int newLengthCount, int newDetailLevel)
	{
		this.detailLevel = (byte) newDetailLevel;
		this.dataPointLengthCount = newLengthCount;
		this.dataPointWidth = 16 / this.dataPointLengthCount;
		
		this.startX = new int[this.dataPointLengthCount * this.dataPointLengthCount];
		this.endX = new int[this.dataPointLengthCount * this.dataPointLengthCount];
		
		this.startZ = new int[this.dataPointLengthCount * this.dataPointLengthCount];
		this.endZ = new int[this.dataPointLengthCount * this.dataPointLengthCount];
		
		
		int index = 0;
		for (int x = 0; x < newLengthCount; x++)
		{
			for (int z = 0; z < newLengthCount; z++)
			{
				this.startX[index] = x * this.dataPointWidth;
				this.startZ[index] = z * this.dataPointWidth;
				
				this.endX[index] = (x * this.dataPointWidth) + this.dataPointWidth;
				this.endZ[index] = (z * this.dataPointWidth) + this.dataPointWidth;
				
				index++;
			}
		}
		
	}// constructor
	
	
	
	//================//
	// static methods //
	//================//
	
	/**
	 * Returns an array of all LodDetails that have a detail level
	 * that is less than or equal to the given LodDetail
	 */
	public static EDhApiMaxHorizontalResolution[] getSelfAndLowerDetails(EDhApiMaxHorizontalResolution detail)
	{
		if (lowerDetailArrays == null)
		{
			// run first time setup
			lowerDetailArrays = new EDhApiMaxHorizontalResolution[EDhApiMaxHorizontalResolution.values().length][];
			
			// go through each LodDetail
			for (EDhApiMaxHorizontalResolution currentDetail : EDhApiMaxHorizontalResolution.values())
			{
				ArrayList<EDhApiMaxHorizontalResolution> lowerDetails = new ArrayList<>();
				
				// find the details lower than currentDetail
				for (EDhApiMaxHorizontalResolution compareDetail : EDhApiMaxHorizontalResolution.values())
				{
					if (currentDetail.detailLevel <= compareDetail.detailLevel)
					{
						lowerDetails.add(compareDetail);
					}
				}
				
				// have the highest detail item first in the list
				Collections.sort(lowerDetails);
				Collections.reverse(lowerDetails);
				
				lowerDetailArrays[currentDetail.detailLevel] = lowerDetails.toArray(new EDhApiMaxHorizontalResolution[lowerDetails.size()]);
			}
		}
		
		return lowerDetailArrays[detail.detailLevel];
	}
	
	/** Returns what detail level should be used at a given distance and maxDistance. */
	public static EDhApiMaxHorizontalResolution getDetailForDistance(EDhApiMaxHorizontalResolution maxDetailLevel, int distance, int maxDistance)
	{
		EDhApiMaxHorizontalResolution[] lowerDetails = getSelfAndLowerDetails(maxDetailLevel);
		int distanceBetweenDetails = maxDistance / lowerDetails.length;
		int index = MathUtil.clamp(0, distance / distanceBetweenDetails, lowerDetails.length - 1);
		
		return lowerDetails[index];
	}
	
}
