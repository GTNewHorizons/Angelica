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

package com.seibel.distanthorizons.core.wrapperInterfaces.chunk;

import com.seibel.distanthorizons.core.generation.AdjacentChunkHolder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public interface IChunkWrapper extends IBindable
{
	/** useful for debugging, but can slow down chunk operations quite a bit due to being called every time. */
	boolean RUN_RELATIVE_POS_INDEX_VALIDATION = ModInfo.IS_DEV_BUILD;
	
	
	
	DhChunkPos getChunkPos();
	
	default int getHeight() { return this.getExclusiveMaxBuildHeight() - this.getInclusiveMinBuildHeight(); }
	/** inclusive (IE if returning -64 the min block can be placed at -64) */
	int getInclusiveMinBuildHeight();
	/** exclusive (IE if returning 320 the max block can be placed at 319) */
	int getExclusiveMaxBuildHeight();
	
	/**
	 * returns the Y level for the last non-empty section in this chunk,
	 * or {@link IChunkWrapper#getInclusiveMinBuildHeight()} if this chunk is completely empty.
	 */
	int getMinNonEmptyHeight();
	/**
	 * returns the Y level for the first non-empty section in this chunk,
	 * or {@link IChunkWrapper#getExclusiveMaxBuildHeight()} if this chunk is completely empty.
	 */
	int getMaxNonEmptyHeight();
	
	/** @return The highest y position of a solid block at the given relative chunk position. */
	int getSolidHeightMapValue(int xRel, int zRel);
	/**
	 * @return The highest y position of a light-blocking or translucent block at the given relative chunk position. <br>
	 * Note: this includes water.
	 */
	int getLightBlockingHeightMapValue(int xRel, int zRel);
	
	int getMaxBlockX();
	int getMaxBlockZ();
	int getMinBlockX();
	int getMinBlockZ();
	
	void setIsDhBlockLightCorrect(boolean isDhLightCorrect);
	void setIsDhSkyLightCorrect(boolean isDhLightCorrect);
	
	boolean isDhBlockLightingCorrect();
	boolean isDhSkyLightCorrect();
	
	
	int getDhSkyLight(int relX, int relY, int relZ);
	void setDhSkyLight(int relX, int relY, int relZ, int lightValue);
	void clearDhSkyLighting();
	
	int getDhBlockLight(int relX, int relY, int relZ);
	void setDhBlockLight(int relX, int relY, int relZ, int lightValue);
	void clearDhBlockLighting();
	
	
	/** Note: don't modify this array, it will only be generated once and then shared between uses */
	ArrayList<DhBlockPos> getWorldBlockLightPosList();
	
	
	default boolean blockPosInsideChunk(DhBlockPos blockPos) { return this.blockPosInsideChunk(blockPos.getX(), blockPos.getY(), blockPos.getZ()); }
	default boolean blockPosInsideChunk(int x, int y, int z)
	{
		return (x >= this.getMinBlockX() && x <= this.getMaxBlockX()
				&& y >= this.getInclusiveMinBuildHeight() && y < this.getExclusiveMaxBuildHeight()
				&& z >= this.getMinBlockZ() && z <= this.getMaxBlockZ());
	}
	default boolean blockPosInsideChunk(DhBlockPos2D blockPos)
	{
		return (blockPos.x >= this.getMinBlockX() && blockPos.x <= this.getMaxBlockX()
				&& blockPos.z >= this.getMinBlockZ() && blockPos.z <= this.getMaxBlockZ());
	}
	
	String toString();
	
	
	default IBlockStateWrapper getBlockState(DhBlockPos pos) { return this.getBlockState(pos.getX(), pos.getY(), pos.getZ()); }
	IBlockStateWrapper getBlockState(int relX, int relY, int relZ);
	/** @see IChunkWrapper#getBlockState(int, int, int, IMutableBlockPosWrapper, IBlockStateWrapper)  */
	default IBlockStateWrapper getBlockState(DhBlockPos pos, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess) { return this.getBlockState(pos.getX(), pos.getY(), pos.getZ(), mcBlockPos, guess); }
	/** 
	 * Can be faster than {@link IChunkWrapper#getBlockState} in some cases
	 * due to directly passing in several shared objects.
	 */
	IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess);
	
	IMutableBlockPosWrapper getMutableBlockPosWrapper();
	
	IBiomeWrapper getBiome(int relX, int relY, int relZ);
	
	boolean isStillValid();
	
	
	
	//========================//
	// default helper methods //
	//========================//
	
	/** used to prevent accidentally attempting to get/set values outside this chunk's boundaries */
	default void throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(int x, int y, int z) throws IndexOutOfBoundsException
	{
		if (!RUN_RELATIVE_POS_INDEX_VALIDATION)
		{
			return;
		}
		
		
		// FIXME +1 is to handle the fact that LodDataBuilder adds +1 to all block lighting calculations, also done in the constructor
		int minHeight = this.getInclusiveMinBuildHeight();
		int maxHeight = this.getExclusiveMaxBuildHeight() + 1;
		
		if (x < 0 || x >= LodUtil.CHUNK_WIDTH
				|| z < 0 || z >= LodUtil.CHUNK_WIDTH
				|| y < minHeight || y > maxHeight)
		{
			String errorMessage = "Relative position [" + x + "," + y + "," + z + "] out of bounds. \n" +
					"X/Z must be between 0 and 15 (inclusive) \n" +
					"Y must be between [" + minHeight + "] and [" + maxHeight + "] (inclusive).";
			throw new IndexOutOfBoundsException(errorMessage);
		}
	}
	/** used to prevent accidentally attempting to get/set values outside this chunk's boundaries */
	default void throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(int x, int z) throws IndexOutOfBoundsException
	{
		if (!RUN_RELATIVE_POS_INDEX_VALIDATION)
		{
			return;
		}
		
		
		// FIXME +1 is to handle the fact that LodDataBuilder adds +1 to all block lighting calculations, also done in the constructor
		int minHeight = this.getInclusiveMinBuildHeight();
		int maxHeight = this.getExclusiveMaxBuildHeight() + 1;
		
		if (x < 0 || x >= LodUtil.CHUNK_WIDTH
				|| z < 0 || z >= LodUtil.CHUNK_WIDTH)
		{
			String errorMessage = "Relative position [" + x + "," + z + "] out of bounds. \n" +
					"X/Z must be between 0 and 15 (inclusive).";
			throw new IndexOutOfBoundsException(errorMessage);
		}
	}
	
	
	/**
	 * Converts a 3D position into a 1D array index. <br><br>
	 *
	 * Source: <br>
	 * <a href="https://stackoverflow.com/questions/7367770/how-to-flatten-or-index-3d-array-in-1d-array">stackoverflow</a>
	 */
	default int relativeBlockPosToIndex(int xRel, int y, int zRel)
	{
		int yRel = y - this.getInclusiveMinBuildHeight();
		return (zRel * LodUtil.CHUNK_WIDTH * this.getHeight()) + (yRel * LodUtil.CHUNK_WIDTH) + xRel;
	}
	
	/**
	 * Converts a 3D position into a 1D array index. <br><br>
	 *
	 * Source: <br>
	 * <a href="https://stackoverflow.com/questions/7367770/how-to-flatten-or-index-3d-array-in-1d-array">stackoverflow</a>
	 */
	default DhBlockPos indexToRelativeBlockPos(int index)
	{
		final int zRel = index / (LodUtil.CHUNK_WIDTH * this.getHeight());
		index -= (zRel * LodUtil.CHUNK_WIDTH * this.getHeight());
		
		final int y = index / LodUtil.CHUNK_WIDTH;
		final int yRel = y + this.getInclusiveMinBuildHeight();
		
		final int xRel = index % LodUtil.CHUNK_WIDTH;
		return new DhBlockPos(xRel, yRel, zRel);
	}
	
	
	/** This is a bad hash algorithm since it only uses the heightmap, but can be used for rough debugging. */
	default int roughHashCode()
	{
		int hash = 31;
		int primeMultiplier = 227;
		
		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				hash = (hash * primeMultiplier) + Integer.hashCode(this.getLightBlockingHeightMapValue(x, z));
			}
		}
		
		return hash;
	}
	
	default int getBlockBiomeHashCode()
	{
		int hash = 31;
		int primeBlockMultiplier = 227;
		int primeBiomeMultiplier = 701;
		int primeHeightMultiplier = 137;
		
		int minBuildHeight = this.getMinNonEmptyHeight();
		int maxBuildHeight = this.getMaxNonEmptyHeight();
		
		IMutableBlockPosWrapper mcBlockPos = this.getMutableBlockPosWrapper();
		IBlockStateWrapper previousBlockState = null;
		
		
		// most blocks (only some blocks are sampled since checking every block is a very slow operation)
		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x+=2)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z+=2)
			{
				for (int y = minBuildHeight; y < maxBuildHeight; y+=2)
				{
					previousBlockState = this.getBlockState(x, y, z, mcBlockPos, previousBlockState);
					
					hash = (hash * primeBlockMultiplier) + previousBlockState.hashCode();
					hash = (hash * primeBiomeMultiplier) + this.getBiome(x, y, z).hashCode();
					hash = (hash * primeHeightMultiplier) + y;
				}
			}
		}
		
		// surface (this should cover most cases for when users modify chunks)
		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				int lightBlockingY = this.getLightBlockingHeightMapValue(x, z);
				previousBlockState = this.getBlockState(x, lightBlockingY, z, mcBlockPos, previousBlockState);
				
				hash = (hash * primeBlockMultiplier) + previousBlockState.hashCode();
				hash = (hash * primeBiomeMultiplier) + this.getBiome(x, lightBlockingY, z).hashCode();
				hash = (hash * primeHeightMultiplier) + lightBlockingY;
				
				int solidY = this.getSolidHeightMapValue(x, z);
				if (solidY != lightBlockingY)
				{
					previousBlockState = this.getBlockState(x, lightBlockingY, z, mcBlockPos, previousBlockState);
					
					hash = (hash * primeBlockMultiplier) + previousBlockState.hashCode();
					hash = (hash * primeBiomeMultiplier) + this.getBiome(x, solidY, z).hashCode();
					hash = (hash * primeHeightMultiplier) + solidY;
				}
			}
		}
		
		// light emitting blocks (if the light changes then the LOD definitely needs to be updated)
		final DhBlockPosMutable relPos = new DhBlockPosMutable(); 
		ArrayList<DhBlockPos> lightPosList = this.getWorldBlockLightPosList();
		for (int i = 0; i < lightPosList.size(); i++)
		{
			DhBlockPos pos = lightPosList.get(i);
			pos.mutateToChunkRelativePos(relPos);
			
			hash = (hash * primeBlockMultiplier) + this.getBlockState(relPos.getX(), relPos.getY(), relPos.getZ()).hashCode();
			hash = (hash * primeHeightMultiplier) + relPos.getY();
		}
		
		
		return hash;
	}
	
	default List<BeaconBeamDTO> getAllActiveBeacons(ArrayList<IChunkWrapper> neighbourChunkList)
	{
		ArrayList<BeaconBeamDTO> beaconBeamList = new ArrayList<>();
		
		AdjacentChunkHolder adjacentChunkHolder = new AdjacentChunkHolder(this, neighbourChunkList);
		
		// find the beacon block positions,
		// since beacons emit light we only need to check the positions that emit light
		final DhBlockPosMutable relPos = new DhBlockPosMutable();
		ArrayList<DhBlockPos> blockPosList = this.getWorldBlockLightPosList();
		for (int i = 0; i < blockPosList.size(); i++)
		{
			DhBlockPos pos = blockPosList.get(i);
			pos.mutateToChunkRelativePos(relPos);
			
			
			IBlockStateWrapper block = this.getBlockState(relPos);
			if (block.isBeaconBlock())
			{
				// check if this beacon is active and if so what color it should be
				Color beaconColor = getBeaconColor(pos, adjacentChunkHolder);
				if (beaconColor != null)
				{
					// beacon is active
					BeaconBeamDTO beam = new BeaconBeamDTO(blockPosList.get(i), beaconColor);
					beaconBeamList.add(beam);
				}
			}
		}
		
		return beaconBeamList;
	}
	/** @return Null if the position isn't valid for a beacon beam. */
	@Nullable
	static Color getBeaconColor(DhBlockPos beaconPos, AdjacentChunkHolder chunkHolder) 
	{
		DhBlockPos beaconRelPos = beaconPos.createChunkRelativePos();
		DhBlockPosMutable baseRelPos = new DhBlockPosMutable(0, beaconRelPos.getY() -1, 0);
		
		
		
		//===========================//
		// check for the base blocks //
		//===========================//
		
		for (int x = -1; x<= 1; x++) 
		{
			for (int z = -1; z <= 1; z++)
			{
				baseRelPos.setX(beaconRelPos.getX() + x);
				baseRelPos.setZ(beaconRelPos.getZ() + z);
				baseRelPos.mutateToChunkRelativePos(baseRelPos);

				// if no chunk is loaded assume the beacon is complete in that direction
				IChunkWrapper chunk = chunkHolder.getByBlockPos(beaconPos.getX() + x, beaconPos.getZ() + z);
				if (chunk != null)
				{
					IBlockStateWrapper block = chunk.getBlockState(baseRelPos.getX(), baseRelPos.getY(), baseRelPos.getZ());
					if (!block.isBeaconBaseBlock())
					{
						return null;
					}
				}
			}
		}
		
		
		
		//=========================//
		// get the beacon color    //
		// and check for occlusion //
		//=========================//
		
		int red = 0;
		int green = 0;
		int blue = 0;
		boolean beaconTintBlockFound = false;
		
		IChunkWrapper centerChunk = chunkHolder.getByBlockPos(beaconPos.getX(), beaconPos.getZ());
		int maxY = centerChunk.getMaxNonEmptyHeight();
		for (int y = beaconRelPos.getY() +1; y <= maxY; y++)
		{
			IBlockStateWrapper block = centerChunk.getBlockState(beaconRelPos.getX(), y, beaconRelPos.getZ());
			if (!block.isAir() && block.getOpacity() == LodUtil.BLOCK_FULLY_OPAQUE)
			{
				return null;
			}
			
			if (block.isBeaconTintBlock())
			{
 				red += block.getBeaconTintColor().getRed();
				green += block.getBeaconTintColor().getGreen();
				blue += block.getBeaconTintColor().getBlue();
				
				if (beaconTintBlockFound)
				{
					red /= 2;
					green /= 2;
					blue /= 2;
				}
				beaconTintBlockFound = true;
			}
		}
		
		return beaconTintBlockFound ? new Color(red, green, blue) : Color.WHITE;
	}
	
	
}
