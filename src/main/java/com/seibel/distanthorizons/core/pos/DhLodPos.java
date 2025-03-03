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

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A MC world position that is relative to a given detail level.
 *
 * @author Leetom
 * @version 2022-11-6
 * 
 * @deprecated TODO replace entirely with DhSectionPos, 
 *                  we don't need to have a full fledged position object for 
 *                  positions inside a LOD, we only need this position object
 *                  to get to/from the LOD section.
 */
@Deprecated
public class DhLodPos implements Comparable<DhLodPos>
{
	public final byte detailLevel;
	
	public final int x;
	public final int z;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhLodPos(byte detailLevel, int x, int z)
	{
		this.detailLevel = detailLevel;
		this.x = x;
		this.z = z;
	}
	public DhLodPos(long sectionPos) { this(DhSectionPos.getDetailLevel(sectionPos), DhSectionPos.getX(sectionPos), DhSectionPos.getZ(sectionPos)); }
	
	
	
	//=========//
	// getters //
	//=========//
	
	/** gets the X position closest to negative infinity */
	public DhLodUnit getMinX() { return new DhLodUnit(this.detailLevel, this.x); }
	/** gets the Z position closest to negative infinity */
	public DhLodUnit getMinZ() { return new DhLodUnit(this.detailLevel, this.z); }
	
	// Get the width of this pos, measured in the mc block unit. (i.e. detail 0)
	public int getBlockWidth() { return this.getWidthAtDetail((byte) 0); }
	
	// Get the width of this pos, measured in the target detail level.
	public int getWidthAtDetail(byte targetLevel)
	{
		if (targetLevel > this.detailLevel)
		{
			LodUtil.assertNotReach("getWidthAtDetail for pos "+this+", given target detail level of bounds: ["+targetLevel+"], this: ["+this.detailLevel+"]");
		}
		
		return BitShiftUtil.powerOfTwo(this.detailLevel - targetLevel);
	}
	
	public DhBlockPos2D getCenterBlockPos()
	{
		return new DhBlockPos2D(
				this.getMinX().toBlockWidth() + BitShiftUtil.half(this.getBlockWidth()),
				this.getMinZ().toBlockWidth() + BitShiftUtil.half(this.getBlockWidth()));
	}
	public DhBlockPos2D getCornerBlockPos() { return new DhBlockPos2D(this.getMinX().toBlockWidth(), this.getMinZ().toBlockWidth()); }
	
	/** converts this position to a lower detail level, angled towards the corner position. */
	public DhLodPos getCornerLodPos(byte newDetail)
	{
		LodUtil.assertTrue(newDetail <= this.detailLevel);
		return new DhLodPos(newDetail,
				this.x * BitShiftUtil.powerOfTwo(this.detailLevel - newDetail),
				this.z * BitShiftUtil.powerOfTwo(this.detailLevel - newDetail));
	}
	
	/**
	 * Returns the DhLodPos 1 detail level lower <br><br>
	 *
	 * Relative child positions returned for each index: <br>
	 * 0 = (0,0) <br>
	 * 1 = (1,0) <br>
	 * 2 = (0,1) <br>
	 * 3 = (1,1) <br>
	 *
	 * @param child0to3 must be an int between 0 and 3
	 */
	public DhLodPos getChildPosByIndex(int child0to3) throws IllegalArgumentException, IllegalStateException
	{
		if (child0to3 < 0 || child0to3 > 3)
			throw new IllegalArgumentException("child0to3 must be between 0 and 3");
		if (this.detailLevel <= 0)
			throw new IllegalStateException("detailLevel must be greater than 0");
		
		return new DhLodPos((byte) (this.detailLevel - 1),
				this.x * 2 + (child0to3 & 1),
				this.z * 2 + BitShiftUtil.half(child0to3 & 2));
	}
	/** Returns this position's child index in its parent */
	public int getChildIndexOfParent() { return (this.x & 1) + BitShiftUtil.square(this.z & 1); }
	
	/** @see DhLodPos#getSectionPosWithSectionDetailLevel(byte) */
	public DhLodPos getDhSectionRelativePositionForDetailLevel() throws IllegalArgumentException { return this.getDhSectionRelativePositionForDetailLevel(this.detailLevel); }
	/**
	 * Returns a DhLodPos with the given detail level and an X/Z position somewhere between (0,0) and (63,63).
	 * This is done to access specific sections from a {@link FullDataSourceV2} where LOD columns are stored
	 * in 64 x 64 blocks.
	 *
	 * @throws IllegalArgumentException if this position's detail level is lower than the output detail level
	 */
	public DhLodPos getDhSectionRelativePositionForDetailLevel(byte outputDetailLevel) throws IllegalArgumentException
	{
		final int xInputOriginal = this.x;
		final int zInputOriginal = this.z;
		
		byte detailLevelDifference = (byte) (outputDetailLevel - this.detailLevel);
		if (outputDetailLevel < this.detailLevel)
		{
			throw new IllegalArgumentException("The output Detail Level [" + outputDetailLevel + "] is less than this " + DhLodPos.class.getSimpleName() + "'s detail level [" + this.detailLevel + "].");
		}
		
		
		
		// negative values need to be offset by the detail level difference squared (in blocks)
		// to skip over -0 (relative position) to -1 (relative position)
		int blockOffset = BitShiftUtil.powerOfTwo(detailLevelDifference) - 1;
		blockOffset = Math.max(1, blockOffset);
		
		int xInput = xInputOriginal;
		xInput += (xInputOriginal < 0) ? blockOffset : 0;
		
		int zInput = zInputOriginal;
		zInput += (zInputOriginal < 0) ? blockOffset : 0;
		
		// convert the input positions into the new detail level
		int xRelativePos = xInput / BitShiftUtil.powerOfTwo(detailLevelDifference);
		int zRelativePos = zInput / BitShiftUtil.powerOfTwo(detailLevelDifference);
		
		// convert the positions into section relative space (0-63)
		xRelativePos = xInputOriginal >= 0 ? (xRelativePos % 64) : 63 + (xRelativePos % 64);
		zRelativePos = zInputOriginal >= 0 ? (zRelativePos % 64) : 63 + (zRelativePos % 64);
		
		return new DhLodPos(outputDetailLevel, xRelativePos, zRelativePos);
	}
	
	/**
	 * @param sectionDetailLevel This is different from the normal LOD Detail level, see {@link DhSectionPos} for more information
	 * @throws IllegalArgumentException if this position's detail level is lower than the output detail level
	 */
	public long getSectionPosWithSectionDetailLevel(byte sectionDetailLevel) throws IllegalArgumentException
	{
		if (sectionDetailLevel < this.detailLevel)
		{
			throw new IllegalArgumentException("The section Detail Level [" + sectionDetailLevel + "] is less than this " + DhLodPos.class.getSimpleName() + "'s detail level [" + this.detailLevel + "].");
		}
		
		DhLodPos lodPos = new DhLodPos(this.detailLevel, this.x, this.z);
		lodPos = lodPos.convertToDetailLevel(sectionDetailLevel);
		return DhSectionPos.encode(lodPos.detailLevel, lodPos.x, lodPos.z);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	/** Returns a new DhLodPos with the given detail level. */
	public DhLodPos convertToDetailLevel(byte newDetailLevel)
	{
		if (newDetailLevel >= this.detailLevel)
		{
			return new DhLodPos(newDetailLevel,
					Math.floorDiv(this.x, BitShiftUtil.powerOfTwo(newDetailLevel - this.detailLevel)),
					Math.floorDiv(this.z, BitShiftUtil.powerOfTwo(newDetailLevel - this.detailLevel)));
		}
		else
		{
			return new DhLodPos(newDetailLevel,
					this.x * BitShiftUtil.powerOfTwo(this.detailLevel - newDetailLevel),
					this.z * BitShiftUtil.powerOfTwo(this.detailLevel - newDetailLevel));
		}
	}
	
	/** @return true if the two positions overlap exactly */
	public boolean overlapsExactly(DhLodPos other)
	{
		if (this.equals(other))
		{
			return true;
		}
		else if (this.detailLevel == other.detailLevel)
		{
			return false;
		}
		else if (this.detailLevel > other.detailLevel)
		{
			return this.equals(other.convertToDetailLevel(this.detailLevel));
		}
		else
		{
			return other.equals(this.convertToDetailLevel(other.detailLevel));
		}
	}
	
	/** Only valid for DhLodUnits for an equal or greater detail level */
	public DhLodPos addLodUnit(DhLodUnit width)
	{
		if (width.detailLevel < this.detailLevel)
			throw new IllegalArgumentException("add called with width.detailLevel < pos detail");
		
		return new DhLodPos(this.detailLevel,
				this.x + width.createFromDetailLevel(this.detailLevel).numberOfLodSectionsWide,
				this.z + width.createFromDetailLevel(this.detailLevel).numberOfLodSectionsWide);
	}
	
	/** Equivalent to adding a DhLodUnit with the same detail level as this DhLodPos */
	public DhLodPos addOffset(int xOffset, int zOffset) { return new DhLodPos(this.detailLevel, this.x + xOffset, this.z + zOffset); }
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (obj == null || this.getClass() != obj.getClass())
		{
			return false;
		}
		else
		{
			DhLodPos otherPos = (DhLodPos) obj;
			return this.detailLevel == otherPos.detailLevel && this.x == otherPos.x && this.z == otherPos.z;
		}
	}
	
	@Override
	public int hashCode() { return Objects.hash(this.detailLevel, this.x, this.z); }
	
	@Override
	public int compareTo(@NotNull DhLodPos obj)
	{
		if (this.detailLevel != obj.detailLevel)
		{
			return Integer.compare(this.detailLevel, obj.detailLevel);
		}
		else if (this.x != obj.x)
		{
			return Integer.compare(this.x, obj.x);
		}
		else
		{
			return Integer.compare(this.z, obj.z);
		}
	}
	
	@Override
	public String toString() { return "[" + this.detailLevel + "*" + this.x + "," + this.z + "]"; }
	
}
