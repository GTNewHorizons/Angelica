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

package com.seibel.distanthorizons.api.objects.data;

import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains a list of {@link DhApiTerrainDataPoint} representing the blocks in a Minecraft chunk.
 *
 * @author Builderb0y, James Seibel
 * @version 2024-7-21
 * @since API 2.0.0
 * 
 * @see IDhApiWrapperFactory
 * @see DhApiTerrainDataPoint
 * @see IDhApiWorldGenerator
 */
public class DhApiChunk 
{
	public final int chunkPosX;
	public final int chunkPosZ;
	
	public final int bottomYBlockPos;
	public final int topYBlockPos;
	
	private final List<List<DhApiTerrainDataPoint>> dataPoints;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** 
	 * Deprecated due to the topYBlockPos and bottomYBlockPos variables being put in the wrong order. 
	 * They should have been in bottom -> top order. 
	 * 
	 * @see DhApiChunk#create(int, int, int, int) 
	 */
	@Deprecated
	public DhApiChunk(int chunkPosX, int chunkPosZ, int topYBlockPos, int bottomYBlockPos) 
	{ this(chunkPosX, chunkPosZ, bottomYBlockPos, topYBlockPos, false); }
	
	/**
	 * @since API 3.0.0 
	 */
	public static DhApiChunk create(int chunkPosX, int chunkPosZ, int bottomYBlockPos, int topYBlockPos)
	{ return new DhApiChunk(chunkPosX, chunkPosZ, bottomYBlockPos, topYBlockPos, false); }
	
	/** 
	 * Only visible to internal DH methods 
	 * @param ignoredParameter is only present to differentiate the two constructors and isn't actually used
	 */
	private DhApiChunk(int chunkPosX, int chunkPosZ, int bottomYBlockPos, int topYBlockPos, boolean ignoredParameter)
	{
		this.chunkPosX = chunkPosX;
		this.chunkPosZ = chunkPosZ;
		this.bottomYBlockPos = bottomYBlockPos;
		this.topYBlockPos = topYBlockPos;
		
		// populate the array to prevent null pointers
		this.dataPoints = new ArrayList<>(16 * 16); // 256
		for (int i = 0; i < (16*16); i++)
		{
			this.dataPoints.add(i, null);
		}
	}
	
	
	
	//=================//
	// getters/setters //
	//=================//
	
	/**
	 * @param relX a block position between 0 and 15 (inclusive) representing the X axis in the chunk
	 * @param relZ a block position between 0 and 15 (inclusive) representing the Z axis in the chunk
	 * @return the {@link DhApiTerrainDataPoint}'s representing the blocks at the relative X and Z position in the chunk.
	 * @throws IndexOutOfBoundsException if relX or relZ are outside the chunk
	 */
	public List<DhApiTerrainDataPoint> getDataPoints(int relX, int relZ) throws IndexOutOfBoundsException
	{
		throwIfRelativePosOutOfBounds(relX, relZ);
		return this.dataPoints.get((relZ << 4) | relX); 
	}
	
	/**
	 * @param relX a block position between 0 and 15 (inclusive) representing the X axis in the chunk
	 * @param relZ a block position between 0 and 15 (inclusive) representing the Z axis in the chunk
	 * @param dataPoints Represents the blocks at the relative X and Z position in the chunk.
	 *                  Cannot contain null objects or data points with any detail level but 0 (block-sized).
	 * @throws IndexOutOfBoundsException if relX or relZ are outside the chunk
	 */
	public void setDataPoints(int relX, int relZ, List<DhApiTerrainDataPoint> dataPoints) throws IndexOutOfBoundsException, IllegalArgumentException
	{
		//==================//
		// basic validation //
		//==================//
		
		// heavier validation is done in the world generator if requested
		
		int internalArrayIndex = (relZ << 4) | relX;
		throwIfRelativePosOutOfBounds(relX, relZ);
		
		if (dataPoints == null)
		{
			// we don't allow null columns
			throw new IllegalArgumentException("Null columns aren't allowed. If you want to remove all data from a column please clear the list or pass in an empty list.");
		}
		
		
		
		//================//
		// set datapoints //
		//================//
		
		List<DhApiTerrainDataPoint> column = this.dataPoints.get(internalArrayIndex);
		if (column == null)
		{
			column = new ArrayList<>();
			this.dataPoints.set(internalArrayIndex, column);
		}
		column.addAll(dataPoints); 
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** Included to prevent users accidentally setting columns outside the chunk */
	private static void throwIfRelativePosOutOfBounds(int relX, int relZ)
	{
		if (relX < 0 || relX > 15 ||
			relZ < 0 || relZ > 15)
		{
			throw new IndexOutOfBoundsException("Relative block positions must be between 0 and 15 (inclusive) the block pos: ("+relX+","+relZ+") is outside of those boundaries.");
		}
	}
	
}