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

package com.seibel.distanthorizons.core.dataObjects.transformers;

import java.util.Collections;
import java.util.List;

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class LodDataBuilder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IBlockStateWrapper AIR = SingletonInjector.INSTANCE.get(IWrapperFactory.class).getAirBlockStateWrapper();
	
	private static boolean getTopErrorLogged = false;
	
	
	
	//============//
	// converters //
	//============//
	
	public static FullDataSourceV2 createFromChunk(IChunkWrapper chunkWrapper)
	{
		// only block lighting is needed here, sky lighting is populated at the data source stage
		LodUtil.assertTrue(chunkWrapper.isDhBlockLightingCorrect());
		
		
		
		int sectionPosX = getXOrZSectionPosFromChunkPos(chunkWrapper.getChunkPos().getX());
		int sectionPosZ = getXOrZSectionPosFromChunkPos(chunkWrapper.getChunkPos().getZ());
		long pos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		
		FullDataSourceV2 dataSource = FullDataSourceV2.createEmpty(pos);
		dataSource.isEmpty = false;
		// chunk updates always propagate up
		dataSource.applyToParent = true;
		
		
		
		// compute the chunk dataSource offset
		// this offset is used to determine where in the dataSource this chunk's data should go
		int chunkOffsetX = chunkWrapper.getChunkPos().getX();
		if (chunkWrapper.getChunkPos().getX() < 0)
		{
			// expected offset positions:
			// chunkPos -> offset
			//  5 -> 1
			//  4 -> 0 ---
			//  3 -> 3
			//  2 -> 2
			//  1 -> 1
			//  0 -> 0 ===
			// -1 -> 3
			// -2 -> 2
			// -3 -> 1
			// -4 -> 0 ---
			// -5 -> 3
			chunkOffsetX = ((chunkOffsetX) % FullDataSourceV2.NUMB_OF_CHUNKS_WIDE);
			if (chunkOffsetX != 0)
			{
				chunkOffsetX += FullDataSourceV2.NUMB_OF_CHUNKS_WIDE;
			}
		}
		else
		{
			chunkOffsetX %= FullDataSourceV2.NUMB_OF_CHUNKS_WIDE;
		}
		chunkOffsetX *= LodUtil.CHUNK_WIDTH;
		
		int chunkOffsetZ = chunkWrapper.getChunkPos().getZ();
		if (chunkWrapper.getChunkPos().getZ() < 0)
		{
			chunkOffsetZ = ((chunkOffsetZ) % FullDataSourceV2.NUMB_OF_CHUNKS_WIDE);
			if (chunkOffsetZ != 0)
			{
				chunkOffsetZ += FullDataSourceV2.NUMB_OF_CHUNKS_WIDE;
			}
		}
		else
		{
			chunkOffsetZ %= FullDataSourceV2.NUMB_OF_CHUNKS_WIDE;
		}
		chunkOffsetZ *= LodUtil.CHUNK_WIDTH;
		
		
		
		//==========================//
		// populate the data source //
		//==========================//
		
		EDhApiWorldCompressionMode worldCompressionMode = Config.Common.LodBuilding.worldCompression.get();
		boolean ignoreHiddenBlocks = (worldCompressionMode != EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
		boolean ignoreNonCollidingBlocks = (Config.Client.Advanced.Graphics.Quality.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
		
		try
		{
			IMutableBlockPosWrapper mcBlockPos = chunkWrapper.getMutableBlockPosWrapper();
			IBlockStateWrapper previousBlockState = null;
			
			int minBuildHeight = chunkWrapper.getMinNonEmptyHeight();
			for (int relBlockX = 0; relBlockX < LodUtil.CHUNK_WIDTH; relBlockX++)
			{
				for (int relBlockZ = 0; relBlockZ < LodUtil.CHUNK_WIDTH; relBlockZ++)
				{
					LongArrayList longs =  dataSource.get(
							relBlockX + chunkOffsetX,
							relBlockZ + chunkOffsetZ);
					if (longs == null)
					{
						longs = new LongArrayList(chunkWrapper.getHeight() / 4);
					}
					else
					{
						longs.clear();
					}
					
					int lastY = chunkWrapper.getExclusiveMaxBuildHeight();
					IBiomeWrapper biome = chunkWrapper.getBiome(relBlockX, lastY, relBlockZ);
					IBlockStateWrapper blockState = AIR;
					int mappedId = dataSource.mapping.addIfNotPresentAndGetId(biome, blockState);
					
					
					byte blockLight;
					byte skyLight;
					if (lastY < chunkWrapper.getExclusiveMaxBuildHeight())
					{
						// FIXME: The lastY +1 offset is to reproduce the old behavior. Remove this when we get per-face lighting
						blockLight = (byte) chunkWrapper.getDhBlockLight(relBlockX, lastY + 1, relBlockZ);
						skyLight = (byte) chunkWrapper.getDhSkyLight(relBlockX, lastY + 1, relBlockZ);
					}
					else
					{
						//we are at the height limit. There are no torches here, and sky is not obscured.
						blockLight = LodUtil.MIN_MC_LIGHT;
						skyLight = LodUtil.MAX_MC_LIGHT;
					}
					
					
					// determine the starting Y Pos
					int y = Math.max(
							// max between both heightmaps to account for solid invisible blocks (glass)
							// and non-solid opaque blocks (at one point this was stairs, not sure what would fit this now)
							chunkWrapper.getLightBlockingHeightMapValue(relBlockX, relBlockZ),
							chunkWrapper.getSolidHeightMapValue(relBlockX, relBlockZ)
						);
					// go up until we reach open air or the world limit
					IBlockStateWrapper topBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
					while (!topBlockState.isAir() && y < chunkWrapper.getExclusiveMaxBuildHeight())
					{
						try
						{
							// This is necessary in some edge cases with snow layers and some other blocks that may not appear in the height map but do block light.
							// Interestingly this doesn't appear to be the case in the DhLightingEngine, if this same logic is added there the lighting breaks for the affected blocks.
							y++;
							topBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
						}
						catch (Exception e)
						{
							if (!getTopErrorLogged)
							{
								LOGGER.warn("Unexpected issue in LodDataBuilder, future errors won't be logged. Chunk [" + chunkWrapper.getChunkPos() + "] with max height: [" + chunkWrapper.getExclusiveMaxBuildHeight() + "] had issue getting block at pos [" + relBlockX + "," + y + "," + relBlockZ + "] error: " + e.getMessage(), e);
								getTopErrorLogged = true;
							}
							
							y--;
							break;
						}
					}
					
					boolean forceSingleBlock = false;
					for (; y >= minBuildHeight; y--)
					{
						IBiomeWrapper newBiome = chunkWrapper.getBiome(relBlockX, y, relBlockZ);
						IBlockStateWrapper newBlockState = previousBlockState = chunkWrapper.getBlockState(relBlockX, y, relBlockZ, mcBlockPos, previousBlockState);
						byte newBlockLight = (byte) chunkWrapper.getDhBlockLight(relBlockX, y + 1, relBlockZ);
						byte newSkyLight = (byte) chunkWrapper.getDhSkyLight(relBlockX, y + 1, relBlockZ);

						// save the biome/block change
						if (!newBiome.equals(biome) || !newBlockState.equals(blockState) || forceSingleBlock)
						{
							forceSingleBlock = false;
							// Check if the  previous block colors this block
							// If so, we must make this block a single entry, aka add the next block even if it is the same
							if (ignoreNonCollidingBlocks && !blockState.isAir()
								&& !blockState.isSolid() && !blockState.isLiquid() && blockState.getOpacity() != LodUtil.BLOCK_FULLY_OPAQUE)
							{
								forceSingleBlock = true;
							}
							longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getInclusiveMinBuildHeight(), blockLight, skyLight));
							biome = newBiome;
							blockState = newBlockState;

							mappedId = dataSource.mapping.addIfNotPresentAndGetId(biome, blockState);
							blockLight = newBlockLight;
							skyLight = newSkyLight;
							lastY = y;
						}
					}
					longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getInclusiveMinBuildHeight(), blockLight, skyLight));
					
					dataSource.setSingleColumn(longs,
							relBlockX + chunkOffsetX,
							relBlockZ + chunkOffsetZ,
							EDhApiWorldGenerationStep.LIGHT,
							worldCompressionMode);
				}
			}
			
			if (ignoreHiddenBlocks) 
			{
				cullHiddenBlocks(dataSource, chunkOffsetX, chunkOffsetZ);
			}
		}
		catch (DataCorruptedException e)
		{
			LOGGER.error("Unable to convert chunk at pos ["+chunkWrapper.getChunkPos()+"] to an LOD. Error: "+e.getMessage(), e);
			return null;
		}
		
		LodUtil.assertTrue(!dataSource.isEmpty);
		return dataSource;
	}
	
	private static void cullHiddenBlocks(FullDataSourceV2 dataSource, int chunkOffsetX, int chunkOffsetZ)
	{
		for (int relZ = 1; relZ < LodUtil.CHUNK_WIDTH - 1; relZ++)
		{
			for (int relX = 1; relX < LodUtil.CHUNK_WIDTH - 1; relX++)
			{
				LongArrayList
						centerColumn = dataSource.get(relX + chunkOffsetX, relZ + chunkOffsetZ),
						posXColumn = dataSource.get(relX + chunkOffsetX + 1, relZ + chunkOffsetZ),
						negXColumn = dataSource.get(relX + chunkOffsetX - 1, relZ + chunkOffsetZ),
						posZColumn = dataSource.get(relX + chunkOffsetX, relZ + chunkOffsetZ + 1),
						negZColumn = dataSource.get(relX + chunkOffsetX, relZ + chunkOffsetZ - 1);
				int
						centerIndex = centerColumn.size() - 1,
						posXIndex = posXColumn.size() - 1,
						negXIndex = negXColumn.size() - 1,
						posZIndex = posZColumn.size() - 1,
						negZIndex = negZColumn.size() - 1;
				for (; centerIndex >= 0; centerIndex--)
				{
					long currentPoint = centerColumn.getLong(centerIndex);
					
					// translucent data points are not eligible to be culled.
					if (isTranslucent(dataSource, currentPoint))
					{
						continue;
					}
					
					// the top segment should never be culled.
					if (centerIndex == 0 
						|| isTranslucent(dataSource, centerColumn.getLong(centerIndex - 1))
						)
					{
						continue;
					}
					
					// the bottom segment can sometimes be culled.
					// assume it will not be seen from below,
					// because this would imply the player is in the void.
					if (centerIndex + 1 < centerColumn.size() 
						&& isTranslucent(dataSource, centerColumn.getLong(centerIndex + 1))
						)
					{
						continue;
					}
					
					posXIndex = checkOcclusion(dataSource, currentPoint, posXColumn, posXIndex);
					if (posXIndex < 0)
					{
						posXIndex = ~posXIndex;
						continue;
					}
					
					negXIndex = checkOcclusion(dataSource, currentPoint, negXColumn, negXIndex);
					if (negXIndex < 0)
					{
						negXIndex = ~negXIndex;
						continue;
					}
					
					posZIndex = checkOcclusion(dataSource, currentPoint, posZColumn, posZIndex);
					if (posZIndex < 0)
					{
						posZIndex = ~posZIndex;
						continue;
					}
					
					negZIndex = checkOcclusion(dataSource, currentPoint, negZColumn, negZIndex);
					if (negZIndex < 0)
					{
						negZIndex = ~negZIndex;
						continue;
					}
					
					// current point is fully surrounded. remove it.
					centerColumn.removeLong(centerIndex);
					// make the above data point cover the area that the current point used to occupy.
					long above = centerColumn.getLong(centerIndex - 1);
					above = FullDataPointUtil.setBottomY(above, FullDataPointUtil.getBottomY(currentPoint));
					above = FullDataPointUtil.setHeight(above, FullDataPointUtil.getHeight(currentPoint) + FullDataPointUtil.getHeight(above));
					centerColumn.set(centerIndex - 1, above);
				}
			}
		}
	}

	/**
	checks if centerPoint is "covered" by opaque data points in adjacentColumn.
	centerPoint counts as covered if, and only if, for all Y levels in its height range,
	there exists an opaque data point in adjacentColumn which overlaps with that Y level.

	@param source used to lookup blocks (and their opacities) based on their IDs.
	@param centerPoint the point being checked to see if it's fully covered.
	@param adjacentColumn the data points which might cover centerPoint.
	@param adjacentIndex the starting index in adjacentColumn to start scanning at.
	indices greater than adjacentIndex have already been checked and confirmed to
	not overlap or only overlap partially with centerPoint's Y range.

	@return if centerPoint is covered, returns the index of the segment which finishes covering it.
	the start of the covering may be a smaller index. in this case, the returned index may be used
	as the adjacentIndex provided to this method on the next iteration which yields a new centerPoint.

	if centerPoint is NOT covered, returns the bitwise negation of the index of the
	segment which did not cover it. this guarantees that the returned value is negative.
	the caller should check for negative return values and manually un-negate them to proceed with the loop.

	in other words, this function returns the index of the next adjacent data 
	point to use in the loop, AND a boolean indicating whether or not the 
	centerPoint is covered;	both are packed into the same int, and returned.
	*/
	private static int checkOcclusion(FullDataSourceV2 source, long centerPoint, LongArrayList adjacentColumn, int adjacentIndex)
	{
		int bottomOfCenter = FullDataPointUtil.getBottomY(centerPoint);
		int topOfCenter = bottomOfCenter + FullDataPointUtil.getHeight(centerPoint);
		for (; adjacentIndex >= 0; adjacentIndex--)
		{
			long adjacentPoint = adjacentColumn.getLong(adjacentIndex);
			int topOfAdjacent = FullDataPointUtil.getBottomY(adjacentPoint) + FullDataPointUtil.getHeight(adjacentPoint);
			if (topOfAdjacent <= bottomOfCenter)
			{
				continue;
			}
			else if (isTranslucent(source, adjacentPoint))
			{
				return ~adjacentIndex;
			}
			else if (topOfAdjacent >= topOfCenter)
			{
				return adjacentIndex;
			}
		}
		
		throw new LodUtil.AssertFailureException("Adjacent column ends before center column does.");
	}

	private static boolean isTranslucent(FullDataSourceV2 source, long point) {
		return source.mapping.getBlockStateWrapper(FullDataPointUtil.getId(point)).getOpacity() < LodUtil.BLOCK_FULLY_OPAQUE;
	}


	
	/** @throws ClassCastException if an API user returns the wrong object type(s) */
	public static FullDataSourceV2 createFromApiChunkData(DhApiChunk apiChunk, boolean runAdditionalValidation) throws ClassCastException, DataCorruptedException, IllegalArgumentException
	{
		// get the section position
		int sectionPosX = getXOrZSectionPosFromChunkPos(apiChunk.chunkPosX);
		int sectionPosZ = getXOrZSectionPosFromChunkPos(apiChunk.chunkPosZ);
		long pos = DhSectionPos.encode(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, sectionPosX, sectionPosZ);
		
		// chunk relative block position in the data source
		int relSourceBlockX = Math.floorMod(apiChunk.chunkPosX, 4) * LodUtil.CHUNK_WIDTH;
		int relSourceBlockZ = Math.floorMod(apiChunk.chunkPosZ, 4) * LodUtil.CHUNK_WIDTH;
		
		FullDataSourceV2 dataSource = FullDataSourceV2.createEmpty(pos);
		for (int relBlockZ = 0; relBlockZ < LodUtil.CHUNK_WIDTH; relBlockZ++)
		{
			for (int relBlockX = 0; relBlockX < LodUtil.CHUNK_WIDTH; relBlockX++)
			{
				List<DhApiTerrainDataPoint> columnDataPoints = apiChunk.getDataPoints(relBlockX, relBlockZ);
				LodDataBuilder.correctDataColumnOrder(columnDataPoints);
				if (runAdditionalValidation)
				{
					validateOrThrowApiDataColumn(columnDataPoints);
				}
				
				LongArrayList packedDataPoints = convertApiDataPointListToPackedLongArray(columnDataPoints, dataSource, apiChunk.bottomYBlockPos);
				
				// TODO add the ability for API users to define a different compression mode
				//  or add a "unkown" compression mode
				dataSource.setSingleColumn(
						packedDataPoints, 
						relBlockX + relSourceBlockX, relBlockZ + relSourceBlockZ, 
						EDhApiWorldGenerationStep.LIGHT, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
				dataSource.isEmpty = false;
			}
		}
		return dataSource;
	}
	
	
	
	//================//
	// public helpers //
	//================//
	
	/** @see FullDataPointUtil */
	public static LongArrayList convertApiDataPointListToPackedLongArray(
			@Nullable List<DhApiTerrainDataPoint> columnDataPoints, FullDataSourceV2 dataSource, 
			int bottomYBlockPos) throws DataCorruptedException
	{
		// this null check does 2 nice things at the same time:
		// if columnDataPoints is null,
		// then packedDataPoints will be of length 0
		// AND the below loop won't run.
		int size = (columnDataPoints != null) ? columnDataPoints.size() : 0;
		
		// TODO make missing air LODs
		// TODO merge duplicate datapoints
		LongArrayList packedDataPoints = new LongArrayList(new long[size]);
		for (int index = 0; index < size; index++)
		{
			DhApiTerrainDataPoint dataPoint = columnDataPoints.get(index);
			
			int id = dataSource.mapping.addIfNotPresentAndGetId(
					(IBiomeWrapper) (dataPoint.biomeWrapper),
					(IBlockStateWrapper) (dataPoint.blockStateWrapper)
			);
			
			packedDataPoints.set(index, FullDataPointUtil.encode(
					id,
					dataPoint.topYBlockPos - dataPoint.bottomYBlockPos,
					dataPoint.bottomYBlockPos - bottomYBlockPos,
					(byte) (dataPoint.blockLightLevel),
					(byte) (dataPoint.skyLightLevel)
			));
		}
		
		return packedDataPoints;
	}
	
	/** also corrects the order if it's backwards */
	public static void correctDataColumnOrder(List<DhApiTerrainDataPoint> dataPoints)
	{
		// order doesn't need to be checked if there is 0 or 1 items
		if (dataPoints.size() > 1)
		{
			// DH expects datapoints to be in a top-down order
			DhApiTerrainDataPoint first = dataPoints.get(0);
			DhApiTerrainDataPoint last = dataPoints.get(dataPoints.size() - 1);
			if (first.bottomYBlockPos < last.bottomYBlockPos)
			{
				// flip the array if it's in bottom-up order
				Collections.reverse(dataPoints);
			}
			
		}
	}
	
	public static void validateOrThrowApiDataColumn(List<DhApiTerrainDataPoint> dataPoints) throws IllegalArgumentException
	{
		// check that each datapoint is valid
		int lastBottomYPos = Integer.MIN_VALUE;
		for (int i = 0; i < dataPoints.size(); i++) // standard for-loop used instead of an enhanced for-loop to slightly reduce GC overhead due to iterator allocation
		{
			DhApiTerrainDataPoint dataPoint = dataPoints.get(i);
			
			if (dataPoint == null)
			{
				throw new IllegalArgumentException("Datapoint: ["+i+"] is null DhApiTerrainDataPoints are not allowed. If you want to represent empty terrain, please use AIR.");
			}
			
			if (dataPoint.detailLevel != 0)
			{
				throw new IllegalArgumentException("Datapoint: ["+i+"] has the wrong detail level ["+dataPoint.detailLevel+"], all data points must be block sized; IE their detail level must be [0].");
			}
			
			
			
			int bottomYPos = dataPoint.bottomYBlockPos;
			int topYPos = dataPoint.topYBlockPos;
			int height = (dataPoint.topYBlockPos - dataPoint.bottomYBlockPos);
			
			// is the datapoint right side up?
			if (bottomYPos > topYPos)
			{
				throw new IllegalArgumentException("Datapoint: ["+i+"] is upside down. Top Pos: ["+topYPos+"], bottom pos: ["+bottomYPos+"].");
			}
			// valid height?
			if (height <= 0 || height >= RenderDataPointUtil.MAX_WORLD_Y_SIZE)
			{
				throw new IllegalArgumentException("Datapoint: ["+i+"] has invalid height. Height must be in the range [1 - "+RenderDataPointUtil.MAX_WORLD_Y_SIZE+"] (inclusive).");
			}
			
			// is this datapoint overlapping the last one?
			if (lastBottomYPos > topYPos)
			{
				throw new IllegalArgumentException("DhApiTerrainDataPoint ["+i+"] is overlapping with the last datapoint, this top Y: ["+topYPos+"], lastBottomYPos: ["+lastBottomYPos+"].");
			}
			// is there a gap between the last datapoint?
			if (topYPos != lastBottomYPos
				&& lastBottomYPos != Integer.MIN_VALUE)
			{
				throw new IllegalArgumentException("DhApiTerrainDataPoint ["+i+"] has a gap between it and index ["+(i-1)+"]. Empty spaces should be filled by air, otherwise DH's downsampling won't calculate lighting correctly.");
			}
			
			
			lastBottomYPos = bottomYPos; 
		}
		
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	public static int getXOrZSectionPosFromChunkPos(int chunkXOrZPos)
	{
		// get the section position
		int sectionPos = chunkXOrZPos;
		// negative positions start at -1 so the logic there is slightly different
		sectionPos = (sectionPos < 0) ? ((sectionPos + 1) / FullDataSourceV2.NUMB_OF_CHUNKS_WIDE) - 1 : (sectionPos / FullDataSourceV2.NUMB_OF_CHUNKS_WIDE);
		return sectionPos;
	}
	
}
