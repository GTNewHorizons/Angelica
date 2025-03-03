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

package com.seibel.distanthorizons.core.pos;

import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;

/**
 * Often used to measure LOD widths
 *
 * @author Leetom
 * @version 2022-11-6
 */
public class DhLodUnit
{
	/** The detail level of this LOD Unit */
	public final byte detailLevel;
	/** How many LOD columns wide this LOD Unit represents */
	public final int numberOfLodSectionsWide;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhLodUnit(byte detailLevel, int numberOfLodSectionsWide)
	{
		this.detailLevel = detailLevel;
		this.numberOfLodSectionsWide = numberOfLodSectionsWide;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	/** @return the size of this LOD unit in Minecraft blocks */
	public int toBlockWidth() { return BitShiftUtil.pow(this.numberOfLodSectionsWide, this.detailLevel); }
	/** @return the LOD Unit relative to the given block width and detail level */
	public static DhLodUnit fromBlockWidth(int blockWidth, byte targetDetailLevel) { return new DhLodUnit(targetDetailLevel, Math.floorDiv(blockWidth, BitShiftUtil.powerOfTwo(targetDetailLevel))); }
	
	/**
	 * if the targetDetailLevel and this object's detail are the same,
	 * this will be returned instead of creating a new object
	 */
	public DhLodUnit createFromDetailLevel(byte targetDetailLevel)
	{
		if (this.detailLevel == targetDetailLevel)
		{
			// no need to create a new object, this one is already the right detail level
			return this;
		}
		else if (this.detailLevel > targetDetailLevel)
		{
			//TODO check if this is correct
			return new DhLodUnit(targetDetailLevel, this.numberOfLodSectionsWide * BitShiftUtil.powerOfTwo(this.detailLevel - targetDetailLevel));
		}
		else
		{
			return new DhLodUnit(targetDetailLevel, Math.floorDiv(this.numberOfLodSectionsWide, BitShiftUtil.powerOfTwo(targetDetailLevel - this.detailLevel)));
		}
	}
	
}
