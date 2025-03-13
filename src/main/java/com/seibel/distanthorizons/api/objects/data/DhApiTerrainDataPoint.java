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

import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;

import java.util.ArrayList;

/**
 * Holds a single datapoint of terrain data.
 *
 * @author James Seibel
 * @version 2024-7-20
 * @since API 1.0.0
 */
public class DhApiTerrainDataPoint
{
	/**
	 * 0 = block <br>
	 * 1 = 2x2 blocks <br>
	 * 2 = 4x4 blocks <br>
	 * 4 = chunk (16x16 blocks) <br>
	 * 9 = region (512x512 blocks) <br>
	 * 
	 * @see EDhApiDetailLevel
	 */
	public final byte detailLevel;
	
	public final int blockLightLevel;
	public final int skyLightLevel;
	public final int bottomYBlockPos;
	public final int topYBlockPos;
	
	public final IDhApiBlockStateWrapper blockStateWrapper;
	public final IDhApiBiomeWrapper biomeWrapper;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/**
	 * Deprecated due to the topYBlockPos and bottomYBlockPos variables being put in the wrong order. 
	 * They should have been in bottom -> top order. 
	 *
	 * @see DhApiTerrainDataPoint#create(byte, int, int, int, int, IDhApiBlockStateWrapper, IDhApiBiomeWrapper) 
	 */
	@Deprecated
	public DhApiTerrainDataPoint(
			byte detailLevel, 
			int blockLightLevel, int skyLightLevel, 
			int topYBlockPos, int bottomYBlockPos, 
			IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper)
	{
		this(detailLevel, blockLightLevel, skyLightLevel,
			bottomYBlockPos, topYBlockPos,
			blockStateWrapper, biomeWrapper, 
			false);
	}
	
	/**
	 * @since API 3.0.0 
	 */
	public static DhApiTerrainDataPoint create(
			byte detailLevel,
			int blockLightLevel, int skyLightLevel,
			int bottomYBlockPos, int topYBlockPos,
			IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper
		)
	{ 
		return new DhApiTerrainDataPoint(
			detailLevel, blockLightLevel, skyLightLevel,
			bottomYBlockPos, topYBlockPos,
			blockStateWrapper, biomeWrapper,
			false); 
	}
	
	/**
	 * Only visible to internal DH methods 
	 * @param ignoredParameter is only present to differentiate the two constructors and isn't actually used
	 */
	private DhApiTerrainDataPoint(
			byte detailLevel,
			int blockLightLevel, int skyLightLevel,
			int bottomYBlockPos, int topYBlockPos,
			IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper,
			boolean ignoredParameter
		)
	{
		this.detailLevel = detailLevel;
		
		this.blockLightLevel = blockLightLevel;
		this.skyLightLevel = skyLightLevel;
		this.bottomYBlockPos = bottomYBlockPos;
		this.topYBlockPos = topYBlockPos;
		
		this.blockStateWrapper = blockStateWrapper;
		this.biomeWrapper = biomeWrapper;
	}
	
}
