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
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;

import java.util.function.LongConsumer;

/**
 * The position object used to define LOD objects in the quad trees. <br><br>
 *
 * A section contains 64 x 64 LOD columns at a given quality.
 * The Section detail level is different from the LOD detail level.
 * For the specifics of how they compare can be viewed in the constants {@link #SECTION_BLOCK_DETAIL_LEVEL},
 * {@link #SECTION_CHUNK_DETAIL_LEVEL}, and {@link #SECTION_REGION_DETAIL_LEVEL}).<br><br>
 *
 * <strong>Why does the smallest render section represent 4x4 MC chunks (section detail level 6)? </strong> <br>
 * A section defines what unit the quad tree works in, because of that we don't want that unit to be too big or too small. <br>
 * <strong>Too small</strong>, and we'll have 1,000s of sections running around, all needing individual files and render buffers.<br>
 * <strong>Too big</strong>, and the LOD dropoff will be very noticeable.<br>
 * With those thoughts in mind we decided on a smallest section size of 64 data points square (IE 4x4 chunks).
 *
 * @author Leetom
 */
public class DhSectionPos
{
	/**
	 * The lowest detail level a Section position can hold.
	 * This section DetailLevel holds 64 x 64 Block level (detail level 0) LODs.
	 */
	public static final byte SECTION_MINIMUM_DETAIL_LEVEL = 6;
	
	public static final byte SECTION_BLOCK_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.BLOCK_DETAIL_LEVEL;
	public static final byte SECTION_CHUNK_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.CHUNK_DETAIL_LEVEL;
	public static final byte SECTION_REGION_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.REGION_DETAIL_LEVEL;
	
	
	
	public static final int DETAIL_LEVEL_WIDTH = 8;
	public static final int X_POS_WIDTH = 28;
	public static final int Z_POS_WIDTH = 28;
	public static final int X_POS_MISSING_WIDTH = 32 - 28;
	public static final int Z_POS_MISSING_WIDTH = 32 - 28;
	
	
	public static final int DETAIL_LEVEL_OFFSET = 0;
	public static final int POS_X_OFFSET = DETAIL_LEVEL_OFFSET + DETAIL_LEVEL_WIDTH;
	/** indicates the Y position where the LOD starts relative to the level's minimum height */
	public static final int POS_Z_OFFSET = POS_X_OFFSET + X_POS_WIDTH;
	
	public static final long DETAIL_LEVEL_MASK = Byte.MAX_VALUE;
	public static final int POS_X_MASK = (int) Math.pow(2, X_POS_WIDTH) - 1;
	public static final int POS_Z_MASK = (int) Math.pow(2, Z_POS_WIDTH) - 1;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** 
	 * This class just holds utility methods for handling a packed
	 * {@link DhSectionPos} and shouldn't be constructed. <Br><br>
	 * 
	 * Use one of the {@link DhSectionPos#encode(byte, int, int)} methods instead
	 */
	private DhSectionPos() { }
	
	
	
	/** 
	 * Note: 
	 * no validation is done for whether the detail level is positive 
	 * or if the X/Z positions can be represented by available bits. 
	 */
	public static long encode(byte detailLevel, int x, int z)
	{
		long data = 0;
		data |= detailLevel & DETAIL_LEVEL_MASK;
		data |= (long) (x & POS_X_MASK) << POS_X_OFFSET;
		data |= (long) (z & POS_Z_MASK) << POS_Z_OFFSET;
		return data;
	}
	
	/** Returns the section pos at the requested detail level containing the given BlockPos */
	public static long encodeContaining(byte outputSectionDetailLevel, DhBlockPos pos)
	{
		int sectionPosX = getXOrZSectionPosFromChunkOrBlockPos(pos.getX(), false);
		int sectionPosZ = getXOrZSectionPosFromChunkOrBlockPos(pos.getZ(), false);
		long blockPos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		return convertToDetailLevel(blockPos, outputSectionDetailLevel);
	}
	/** Returns the section pos at the requested detail level containing the given ChunkPos */
	public static long encodeContaining(byte outputSectionDetailLevel, DhChunkPos pos)
	{
		int sectionPosX = getXOrZSectionPosFromChunkOrBlockPos(pos.getX(), true);
		int sectionPosZ = getXOrZSectionPosFromChunkOrBlockPos(pos.getZ(), true);
		long blockPos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		return convertToDetailLevel(blockPos, outputSectionDetailLevel);
	}
	private static int getXOrZSectionPosFromChunkOrBlockPos(int chunkXOrZPos, boolean isChunkPos)
	{
		int sectionPos = chunkXOrZPos;
		int fullDataSourceWidth = isChunkPos ? FullDataSourceV2.NUMB_OF_CHUNKS_WIDE : (FullDataSourceV2.NUMB_OF_CHUNKS_WIDE * LodUtil.CHUNK_WIDTH);
		
		// negative positions start at -1 so the logic there is slightly different
		sectionPos = (sectionPos < 0) 
				? ((sectionPos + 1) / fullDataSourceWidth) - 1 
				: (sectionPos / fullDataSourceWidth);
		return sectionPos;
	}
	
	
	
	//============//
	// converters //
	//============//

	/** uses the absolute detail level aka detail levels like {@link LodUtil#CHUNK_DETAIL_LEVEL} instead of the dhSectionPos detailLevels. */
	public static long convertToDetailLevel(long pos, byte newDetailLevel)
	{
		byte detailLevel = getDetailLevel(pos);
		int x = getX(pos);
		int z = getZ(pos);
		
		// logic originally taken from DhLodPos
		if (newDetailLevel >= detailLevel)
		{
			x = Math.floorDiv(x, BitShiftUtil.powerOfTwo(newDetailLevel - detailLevel));
			z = Math.floorDiv(z, BitShiftUtil.powerOfTwo(newDetailLevel - detailLevel));
		}
		else
		{
			x = x * BitShiftUtil.powerOfTwo(detailLevel - newDetailLevel);
			z = z * BitShiftUtil.powerOfTwo(detailLevel - newDetailLevel);
		}

		return encode(newDetailLevel, x, z);
	}
	
	
	
	//==================//
	// property getters //
	//==================//
	
	public static byte getDetailLevel(long pos) { return (byte) ((pos >> DETAIL_LEVEL_OFFSET) & DETAIL_LEVEL_MASK); }
	public static int getX(long pos)
	{
		// unpack the position
		int x = (int) ((pos >> POS_X_OFFSET) & POS_X_MASK);
		// add the missing 2's compliment most-significant bits (if not done negative numbers will parse incorrectly)
		x = (x << X_POS_MISSING_WIDTH) >> X_POS_MISSING_WIDTH;
		return x;
	}
	public static int getZ(long pos)
	{
		int Z = (int) ((pos >> POS_Z_OFFSET) & POS_Z_MASK);
		Z = (Z << Z_POS_MISSING_WIDTH) >> Z_POS_MISSING_WIDTH;
		return Z;
	}
	
	
	
	//=========//
	// getters //
	//=========//

	/** @return the block X pos that represents the smallest X coordinate of this section */
	public static int getMinCornerBlockX(long pos)
	{
		// detail level 1 (2x2 blocks) is a special case,
		// if this isn't done it will return (1,1) instead of (0,0)
		int halfBlockWidth = (getDetailLevel(pos) != 1) ? (DhSectionPos.getBlockWidth(pos) / 2) : 0;
		return DhSectionPos.getCenterBlockPosX(pos) - halfBlockWidth;
	}
	/** @return the block Z pos that represents the smallest Z coordinate of this section */
	public static int getMinCornerBlockZ(long pos)
	{
		int halfBlockWidth = (getDetailLevel(pos) != 1) ? (DhSectionPos.getBlockWidth(pos) / 2) : 0;
		return DhSectionPos.getCenterBlockPosZ(pos) - halfBlockWidth;
	}
	
	/** 
	 * A detail level of X lower than this section's detail level will return: <br>
	 * 0 -> 1 <br>
	 * 1 -> 2 <br>
	 * 2 -> 4 <br>
	 * 3 -> 8 <br>
	 * etc.
	 * 
	 * @return how many {@link DhSectionPos}'s at the given detail level it would take to span the width of this section.
	 */
	public static int getWidthCountForLowerDetailedSection(long pos, byte returnDetailLevel)
	{
		byte detailLevel = getDetailLevel(pos);
		
		LodUtil.assertTrue(returnDetailLevel <= detailLevel, "returnDetailLevel must be less than sectionDetail");
		byte offset = (byte) (detailLevel - returnDetailLevel);
		return BitShiftUtil.powerOfTwo(offset);
	}

	/** @return how wide this section is in blocks */
	public static int getBlockWidth(long pos) { return BitShiftUtil.powerOfTwo(getDetailLevel(pos)); }
	/** @return how wide this section is in chunks */
	public static int getChunkWidth(long pos) { return DhSectionPos.getBlockWidth(pos) / LodUtil.CHUNK_WIDTH; }
	

	public static DhBlockPos2D getCenterBlockPos(long pos) { return new DhBlockPos2D(getCenterBlockPosX(pos), getCenterBlockPosZ(pos)); }

	public static int getCenterBlockPosX(long pos) { return getCenterBlockPosXOrZ(pos, true); }
	public static int getCenterBlockPosZ(long pos) { return getCenterBlockPosXOrZ(pos, false); }
	private static int getCenterBlockPosXOrZ(long pos, boolean returnX)
	{
		byte detailLevel = getDetailLevel(pos);
		int x = getX(pos);
		int z = getZ(pos);
		
		
		int centerBlockPos = returnX ? x : z;

		if (detailLevel == 0)
		{
			// already at block detail level, no conversion necessary
			return centerBlockPos;
		}
		
		// we can't get the center of the position at block level, only attempt to get the position offset for detail levels above 0
		int positionOffset = 0;
		if (detailLevel != 1)
		{
			positionOffset = BitShiftUtil.powerOfTwo(detailLevel - 1);
		}
		
		return (centerBlockPos * BitShiftUtil.powerOfTwo(detailLevel)) + positionOffset;
	}
	
	public static int getManhattanBlockDistance(long pos, DhBlockPos2D blockPos)
	{
		return Math.abs(getCenterBlockPosX(pos) - blockPos.x)
				+ Math.abs(getCenterBlockPosZ(pos) - blockPos.z);
	}
	
	/**
	 * Returns the signed distance from a given block to a given section. <br>
	 * Essentially acts like a distance from the block to the nearest edge of the section,
	 * except inside the section it's negative. <br>
	 * Useful for detail level insensitive distance comparisons.
	 */
	public static int getChebyshevSignedBlockDistance(long pos, DhBlockPos2D blockPos)
	{
		return Math.max(
				Math.abs(getCenterBlockPosX(pos) - blockPos.x),
				Math.abs(getCenterBlockPosZ(pos) - blockPos.z)
		) - getBlockWidth(pos) / 2;
	}
	
	
	
	//==================//
	// parent child pos //
	//==================//

	/**
	 * Returns a position 1 detail level lower. <br><br>
	 *
	 * Relative child positions returned for each index: <br>
	 * 0 = (0,0) - North West <br>
	 * 1 = (1,0) - South West <br>
	 * 2 = (0,1) - North East <br>
	 * 3 = (1,1) - South East <br>
	 *
	 * @param child0to3 must be an int between 0 and 3
	 */
	public static long getChildByIndex(long pos, int child0to3) throws IllegalArgumentException, IllegalStateException
	{
		byte detailLevel = getDetailLevel(pos);
		int x = getX(pos);
		int z = getZ(pos);
		
		if (child0to3 < 0 || child0to3 > 3)
		{
			throw new IllegalArgumentException("child0to3 must be between 0 and 3");
		}
		if (detailLevel <= 0)
		{
			throw new IllegalStateException("section detail must be greater than 0");
		}

		return DhSectionPos.encode((byte) (detailLevel - 1),
				x * 2 + (child0to3 & 1),
				z * 2 + BitShiftUtil.half(child0to3 & 2));
	}
	/** Returns this position's child index in its parent */
	public static int getChildIndexOfParent(long pos) { return (getX(pos) & 1) + BitShiftUtil.square(getZ(pos) & 1); }
	
	public static long getParentPos(long pos) { return DhSectionPos.encode((byte) (getDetailLevel(pos) + 1), BitShiftUtil.half(getX(pos)), BitShiftUtil.half(getZ(pos))); }
	


	public static long getAdjacentPos(long pos, EDhDirection dir) throws IllegalArgumentException
	{
		if (dir == EDhDirection.UP || dir == EDhDirection.DOWN)
		{
			throw new IllegalArgumentException("getAdjacentPos can't be UP or DOWN, direction given: ["+dir.name()+"].");
		}
		
		return DhSectionPos.encode(getDetailLevel(pos),
				getX(pos) + dir.getNormal().x,
				getZ(pos) + dir.getNormal().z);
	}
	
	@Deprecated
	public static DhLodPos getSectionBBoxPos(long pos) { return new DhLodPos(getDetailLevel(pos), getX(pos), getZ(pos)); }



	//=============//
	// comparisons //
	//=============//
	
	public static boolean contains(long aPos, long bPos)
	{
		int aMinX = getMinCornerBlockX(aPos);
		int aMinZ = getMinCornerBlockZ(aPos);
		
		int bMinX = getMinCornerBlockX(bPos);
		int bMinZ = getMinCornerBlockZ(bPos);

		int aBlockWidth = getBlockWidth(aPos) - 1; // minus 1 to account for zero based positional indexing
		int aMaxX = aMinX + aBlockWidth;
		int aMaxZ = aMinZ + aBlockWidth;

		return aMinX <= bMinX && bMinX <= aMaxX &&
				aMinZ <= bMinZ && bMinZ <= aMaxZ;
	}
	
	public static boolean contains(long aPos, DhBlockPos blockPos)
	{
		int sectionMinX = getMinCornerBlockX(aPos);
		int sectionMinZ = getMinCornerBlockZ(aPos);
		
		int blockX = blockPos.getX();
		int blockZ = blockPos.getZ();
		
		int sectionBlockWidth = getBlockWidth(aPos) - 1; // minus 1 to account for zero based positional indexing
		int sectionMaxX = sectionMinX + sectionBlockWidth;
		int sectionMaxZ = sectionMinZ + sectionBlockWidth;
		
		return sectionMinX <= blockX && blockX <= sectionMaxX &&
				sectionMinZ <= blockZ && blockZ <= sectionMaxZ;
	}



	//===========//
	// iterators //
	//===========//

	/** Applies the given consumer to all 4 of this position's children. */
	public static void forEachChild(long pos, LongConsumer callback) throws IllegalArgumentException, IllegalStateException
	{
		for (int i = 0; i < 4; i++)
		{
			callback.accept(getChildByIndex(pos, i));
		}
	}

	/** Applies the given consumer to all children of the position at the given section detail level. */
	public static void forEachChildDownToDetailLevel(long pos, byte minSectionDetailLevel, ICancelablePrimitiveLongConsumer callback) throws IllegalArgumentException, IllegalStateException
	{
		boolean stop = callback.accept(pos);
		if (stop || minSectionDetailLevel == getDetailLevel(pos))
		{
			return;
		}
		
		for (int i = 0; i < 4; i++)
		{
			forEachChildDownToDetailLevel(getChildByIndex(pos, i), minSectionDetailLevel, callback);
		}
	}

	/** Applies the given consumer to all children of the position at the given section detail level. */
	public static void forEachChildAtDetailLevel(long pos, byte sectionDetailLevel, LongConsumer callback) throws IllegalArgumentException, IllegalStateException
	{
		if (sectionDetailLevel == getDetailLevel(pos))
		{
			callback.accept(pos);
			return;
		}

		for (int i = 0; i < 4; i++)
		{
			forEachChildAtDetailLevel(getChildByIndex(pos, i), sectionDetailLevel, callback);
		}
	}

	/** Applies the given consumer to all children of the position at the given section detail level. */
	public static void forEachPosUpToDetailLevel(long pos, byte maxSectionDetailLevel, LongConsumer callback)
	{
		callback.accept(pos);
		if (maxSectionDetailLevel == getDetailLevel(pos))
		{
			return;
		}
		
		forEachPosUpToDetailLevel(getParentPos(pos), maxSectionDetailLevel, callback);
	}
	
	
	
	//==============//
	// Base methods //
	//==============//
	
	/** Example: "6*1,-3" */
	public static String toString(long pos) { return getDetailLevel(pos) + "*" + getX(pos) + "," + getZ(pos); }
	public static int hashCode(long pos) { return Long.hashCode(pos); }
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** Used instead of {@link java.util.function.Function} to prevent unnecessary (un)wrapping. */
	@FunctionalInterface
	public interface ICancelablePrimitiveLongConsumer
	{
		/** @return true if this method should cancel further consumers. */
		boolean accept(long value);
	}
	
}
