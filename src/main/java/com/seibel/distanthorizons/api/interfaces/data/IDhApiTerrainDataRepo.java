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

package com.seibel.distanthorizons.api.interfaces.data;

import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;

/**
 * Used to interface with Distant Horizons' terrain data.
 *
 * @see IDhApiTerrainDataCache
 * 
 * @author James Seibel
 * @version 2023-6-22
 * @since API 1.0.0
 */
public interface IDhApiTerrainDataRepo
{
	
	//=========//
	// getters //
	//=========//
	
	/** @see IDhApiTerrainDataRepo#getSingleDataPointAtBlockPos(IDhApiLevelWrapper, int, int, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiTerrainDataPoint> getSingleDataPointAtBlockPos(IDhApiLevelWrapper levelWrapper, int blockPosX, int blockPosY, int blockPosZ) { return this.getSingleDataPointAtBlockPos(levelWrapper, blockPosX, blockPosY, blockPosZ, null); }
	/** 
	 * Returns the terrain datapoint at the given block position, at/or containing the given Y position. 
	 * @since API 3.0.0
	 */
	DhApiResult<DhApiTerrainDataPoint> getSingleDataPointAtBlockPos(IDhApiLevelWrapper levelWrapper, int blockPosX, int blockPosY, int blockPosZ, IDhApiTerrainDataCache dataCache);
	
	/** @see IDhApiTerrainDataRepo#getColumnDataAtBlockPos(IDhApiLevelWrapper, int, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiTerrainDataPoint[]> getColumnDataAtBlockPos(IDhApiLevelWrapper levelWrapper, int blockPosX, int blockPosZ) { return this.getColumnDataAtBlockPos(levelWrapper, blockPosX, blockPosZ, null); }
	/** 
	 * Returns every datapoint in the column located at the given block X and Z position top to bottom. 
	 * @since API 3.0.0 
	 */
	DhApiResult<DhApiTerrainDataPoint[]> getColumnDataAtBlockPos(IDhApiLevelWrapper levelWrapper, int blockPosX, int blockPosZ, IDhApiTerrainDataCache dataCache);
	
	/** @see IDhApiTerrainDataRepo#getAllTerrainDataAtChunkPos(IDhApiLevelWrapper, int, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtChunkPos(IDhApiLevelWrapper levelWrapper, int chunkPosX, int chunkPosZ) { return this.getAllTerrainDataAtChunkPos(levelWrapper, chunkPosX, chunkPosZ, null); }
	/**
	 * Returns every datapoint in the given chunk's X and Z position. <br><br>
	 *
	 * The returned array is ordered: [relativeBlockX][relativeBlockZ][columnIndex] <br>
	 * RelativeBlockX/Z are relative to the block position closest to negative infinity in the chunk's position. <br>
	 * The column data is ordered from top to bottom. Note: each column may have a different number of values. <br>
	 * 
	 * @since API 3.0.0
	 */
	DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtChunkPos(IDhApiLevelWrapper levelWrapper, int chunkPosX, int chunkPosZ, IDhApiTerrainDataCache dataCache);
	
	/** @see IDhApiTerrainDataRepo#getAllTerrainDataAtRegionPos(IDhApiLevelWrapper, int, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtRegionPos(IDhApiLevelWrapper levelWrapper, int regionPosX, int regionPosZ) { return this.getAllTerrainDataAtRegionPos(levelWrapper, regionPosX, regionPosZ, null); }
	/**
	 * Returns every datapoint in the given region's X and Z position. <br><br>
	 *
	 * The returned array is ordered: [relativeBlockX][relativeBlockZ][columnIndex] <br>
	 * RelativeBlockX/Z are relative to the block position closest to negative infinity in the region's position. <br>
	 * The column data is ordered from top to bottom. Note: each column may have a different number of values. <br>
	 * 
	 * @since API 3.0.0
	 */
	DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtRegionPos(IDhApiLevelWrapper levelWrapper, int regionPosX, int regionPosZ, IDhApiTerrainDataCache dataCache);
	
	/** @see IDhApiTerrainDataRepo#getAllTerrainDataAtDetailLevelAndPos(IDhApiLevelWrapper, byte, int, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtRegionPos(IDhApiLevelWrapper levelWrapper, byte detailLevel, int posX, int posZ) { return this.getAllTerrainDataAtDetailLevelAndPos(levelWrapper, detailLevel, posX, posZ, null); }
	/**
	 * Returns every datapoint in the column located at the given detail level and X/Z position. <br>
	 * This can be used to return terrain data for non-standard sizes (IE 2x2 blocks or 2x2 chunks).
	 *
	 * @param detailLevel a positive byte defining the detail level of the returned data. <br>
	 * Every increase doubles the width of the returned area. <br>
	 * Example values: 0 = block, 1 = 2x2 blocks, 2 = 4x4 blocks, ... 4 = chunk (16x16 blocks), ... 9 = region (512x512 blocks) <br>
	 * See {@link EDhApiDetailLevel} for more information.
	 * 
	 * @since API 3.0.0
	 */
	DhApiResult<DhApiTerrainDataPoint[][][]> getAllTerrainDataAtDetailLevelAndPos(IDhApiLevelWrapper levelWrapper, byte detailLevel, int posX, int posZ, IDhApiTerrainDataCache dataCache);
	
	
	
	/** @see IDhApiTerrainDataRepo#raycast(IDhApiLevelWrapper, double, double, double, float, float, float, int, IDhApiTerrainDataCache) */
	default DhApiResult<DhApiRaycastResult> raycast(
			IDhApiLevelWrapper levelWrapper,
			double rayOriginX, double rayOriginY, double rayOriginZ,
			float rayDirectionX, float rayDirectionY, float rayDirectionZ,
			int maxRayBlockLength)
	{
		return this.raycast(
				levelWrapper,
				rayOriginX, rayOriginY, rayOriginZ,
				rayDirectionX, rayDirectionY, rayDirectionZ,
				maxRayBlockLength,
				null);
	}
	
	/**
	 * Returns the datapoint and position of the LOD
	 * at the end of the given ray. <br><br>
	 *
	 * Will return "success" with a null datapoint if the ray reaches the max length without finding any data.
	 * 
	 * @since API 3.0.0
	 */
	DhApiResult<DhApiRaycastResult> raycast(
			IDhApiLevelWrapper levelWrapper,
			double rayOriginX, double rayOriginY, double rayOriginZ,
			float rayDirectionX, float rayDirectionY, float rayDirectionZ,
			int maxRayBlockLength,
			IDhApiTerrainDataCache dataCache);
	
	
	
	//=========//
	// setters //
	//=========//
	
	/**
	 * Sets the LOD data for the given chunk at the chunk's position. <br><br>
	 *
	 * Notes: <br>
	 * - Only works if the given {@link IDhApiLevelWrapper} points to a loaded level. <br>
	 * - If the player travels to this chunk, or the chunk is updated in some other way; your data will be replaced
	 *   by whatever the current chunk is. <br>
	 * - This method may not update the LOD data immediately. Any other chunks that have
	 *   been queued to update will be handled first.
	 *
	 * @param levelWrapper the level wrapper that the chunk should be saved to.
	 * @param chunkObjectArray see {@link IDhApiWorldGenerator#generateChunks} for what objects are expected.
	 * @throws ClassCastException if chunkObjectArray doesn't contain the right objects.
	 * The exception will contain the expected object(s).
	 */
	DhApiResult<Void> overwriteChunkDataAsync(IDhApiLevelWrapper levelWrapper, Object[] chunkObjectArray) throws ClassCastException;
	
	
	
	//=========//
	// helpers //
	//=========//
	
	/** 
	 * @return a {@link IDhApiTerrainDataCache} backed by {@link java.lang.ref.SoftReference}'s.
	 * @since API 3.0.0
	 */
	IDhApiTerrainDataCache getSoftCache();
	
}
