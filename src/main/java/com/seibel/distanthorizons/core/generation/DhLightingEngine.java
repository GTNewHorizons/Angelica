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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

/**
 * This logic was roughly based on
 * <a href="https://github.com/PaperMC/Starlight/blob/acc8ed9634bbe27ec68e8842e420948bfa9707e7/TECHNICAL_DETAILS.md">Starlight's technical documentation</a>
 * although there were some changes due to how our lighting engine works in comparison to Minecraft's.
 */
public class DhLightingEngine
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	public static final DhLightingEngine INSTANCE = new DhLightingEngine();
	
	/** 
	 * Minor garbage collection optimization. <br>
	 * Since these objects are always mutated anyway, using a {@link ThreadLocal} will allow us to
	 * only create as many of these {@link DhBlockPos} as necessary.
	 */
	private static final ThreadLocal<DhBlockPosMutable> PRIMARY_BLOCK_POS_REF = ThreadLocal.withInitial(() -> new DhBlockPosMutable());
	private static final ThreadLocal<DhBlockPosMutable> SECONDARY_BLOCK_POS_REF = ThreadLocal.withInitial(() -> new DhBlockPosMutable());
	
	/** if enabled will render each block light value when the chunk lighting engine is run */
	private static final boolean RENDER_BLOCK_LIGHT_WIREFRAME = false;
	/** if enabled will render each sky light value when the chunk lighting engine is run */
	private static final boolean RENDER_SKY_LIGHT_WIREFRAME = false;
	
	/**
	 * Used for dataSource lighting. <br> 
	 * Packed as alternating x and z offsets. 
	 */
	private static final byte[] ADJACENT_DIRECTION_OFFSETS = new byte[]
			{
					-1, 0,
					+1, 0,
					0, -1,
					0, +1
			};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private DhLightingEngine() { }
	
	
	
	//================//
	// chunk lighting //
	//================//
	
	/**
	 * Populates both block and sky lighting. 
	 * @see DhLightingEngine#lightChunk(IChunkWrapper, ArrayList, int, boolean, boolean) 
	 */
	public void lightChunk(
			@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList,
			int maxSkyLight)
	{ this.lightChunk(centerChunk, nearbyChunkList, maxSkyLight, true, true); }
	
	/**
	 * Only populates block lights. 
	 * @see DhLightingEngine#lightChunk(IChunkWrapper, ArrayList, int, boolean, boolean) 
	 */
	public void bakeChunkBlockLighting(
			@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList,
			int maxSkyLight)
	{ this.lightChunk(centerChunk, nearbyChunkList, maxSkyLight, true, false); }
	
	
	/**
	 * Note: depending on the implementation of {@link IChunkWrapper#setDhBlockLight(int, int, int, int)} and {@link IChunkWrapper#setDhSkyLight(int, int, int, int)}
	 * the light values may be stored in the wrapper itself instead of the wrapped chunk object.
	 * If that is the case unwrapping the chunk will undo any work this method did.
	 *
	 * @param centerChunk the chunk we want to apply lighting to
	 * @param nearbyChunkList should also contain centerChunk
	 * @param maxSkyLight should be a value between 0 and 15
	 */
	private void lightChunk(
			@NotNull IChunkWrapper centerChunk, @NotNull ArrayList<IChunkWrapper> nearbyChunkList, 
			int maxSkyLight, boolean updateBlockLight, boolean updateSkyLight)
	{
		DhChunkPos centerChunkPos = centerChunk.getChunkPos();
		AdjacentChunkHolder adjacentChunkHolder = new AdjacentChunkHolder(centerChunk);
		
		
		// try-finally to handle the stableArray resources
		StableLightPosStack blockLightWorldPosQueue = null;
		StableLightPosStack skyLightWorldPosQueue = null;
		try
		{
			blockLightWorldPosQueue = StableLightPosStack.borrowStableLightPosArray();
			skyLightWorldPosQueue = StableLightPosStack.borrowStableLightPosArray();
			
			
			
			// generate the list of chunk pos we need,
			// currently a 3x3 grid
			HashSet<DhChunkPos> requestedAdjacentPositions = new HashSet<>(9);
			for (int xOffset = -1; xOffset <= 1; xOffset++)
			{
				for (int zOffset = -1; zOffset <= 1; zOffset++)
				{
					DhChunkPos adjacentPos = new DhChunkPos(centerChunkPos.getX() + xOffset, centerChunkPos.getZ() + zOffset);
					requestedAdjacentPositions.add(adjacentPos);
				}
			}
			
			
			// find all adjacent chunks
			// and get any necessary info from them
			for (int chunkIndex = 0; chunkIndex < nearbyChunkList.size(); chunkIndex++) // using iterators in high traffic areas can cause GC issues due to allocating a bunch of iterators, use an indexed for-loop instead
			{
				IChunkWrapper chunk = nearbyChunkList.get(chunkIndex);
				if (chunk != null && requestedAdjacentPositions.contains(chunk.getChunkPos()))
				{
					// remove the newly found position
					requestedAdjacentPositions.remove(chunk.getChunkPos());
					
					// add the adjacent chunk
					adjacentChunkHolder.add(chunk);
					
					// get and set the adjacent chunk's initial block lights
					final DhBlockPosMutable relLightBlockPos = PRIMARY_BLOCK_POS_REF.get();
					
					
					
					//==================//
					// set block lights //
					//==================//
					
					if (updateBlockLight)
					{
						ArrayList<DhBlockPos> blockLightPosList = chunk.getWorldBlockLightPosList();
						for (int blockLightIndex = 0; blockLightIndex < blockLightPosList.size(); blockLightIndex++) // using iterators in high traffic areas can cause GC issues due to allocating a bunch of iterators, use an indexed for-loop instead
						{
							DhBlockPos blockLightPos = blockLightPosList.get(blockLightIndex);
							blockLightPos.mutateToChunkRelativePos(relLightBlockPos);
							
							// get the light
							IBlockStateWrapper blockState = chunk.getBlockState(relLightBlockPos);
							int lightValue = blockState.getLightEmission();
							blockLightWorldPosQueue.push(blockLightPos.getX(), blockLightPos.getY(), blockLightPos.getZ(), lightValue);
							
							// set the light
							chunk.setDhBlockLight(relLightBlockPos.getX(), relLightBlockPos.getY(), relLightBlockPos.getZ(), lightValue);
						}
					}
					
					
					
					//================//
					// set sky lights //
					//================//
					
					// get and set the adjacent chunk's initial skylights,
					// if the dimension has skylights
					if (updateSkyLight && maxSkyLight > 0)
					{
						IMutableBlockPosWrapper mcBlockPos = chunk.getMutableBlockPosWrapper();
						IBlockStateWrapper previousBlockState = null;
						
						int maxY = chunk.getMaxNonEmptyHeight();
						int minY = chunk.getInclusiveMinBuildHeight();
						
						// get the adjacent chunk's sky lights
						for (int relX = 0; relX < LodUtil.CHUNK_WIDTH; relX++) // relative block pos
						{
							for (int relZ = 0; relZ < LodUtil.CHUNK_WIDTH; relZ++)
							{
								// set each pos' sky light all the way down until an opaque block is hit
								for (int y = maxY; y >= minY; y--)
								{
									IBlockStateWrapper block = previousBlockState = chunk.getBlockState(relX, y, relZ, mcBlockPos, previousBlockState);
									if (block != null && block.getOpacity() != LodUtil.BLOCK_FULLY_TRANSPARENT)
									{
										// keep moving down until we find a non-transparent block
										break;
									}
									
									
									// add sky light to the queue
									DhBlockPos skyLightPos = new DhBlockPos(chunk.getMinBlockX() + relX, y, chunk.getMinBlockZ() + relZ);
									skyLightWorldPosQueue.push(skyLightPos.getX(), skyLightPos.getY(), skyLightPos.getZ(), maxSkyLight);
									
									// set the chunk's sky light
									skyLightPos.mutateToChunkRelativePos(relLightBlockPos);
									chunk.setDhSkyLight(relLightBlockPos.getX(), relLightBlockPos.getY(), relLightBlockPos.getZ(), maxSkyLight);
								}
							}
						}
					}
				}
				
				
				if (requestedAdjacentPositions.isEmpty())
				{
					// we found every chunk we needed, we don't need to keep iterating
					break;
				}
			}
			
			// block light
			if (updateBlockLight)
			{
				// done to prevent a rare issue where the light values are incorrectly set to -1
				// TODO why could that happen?
				centerChunk.clearDhBlockLighting();
				
				this.propagateChunkLightPosList(blockLightWorldPosQueue, adjacentChunkHolder,
						(neighbourChunk, relBlockPos) -> neighbourChunk.getDhBlockLight(relBlockPos.getX(), relBlockPos.getY(), relBlockPos.getZ()),
						(neighbourChunk, relBlockPos, newLightValue) -> neighbourChunk.setDhBlockLight(relBlockPos.getX(), relBlockPos.getY(), relBlockPos.getZ(), newLightValue),
						true);
			}
			
			// sky light
			if (updateSkyLight)
			{
				centerChunk.clearDhSkyLighting();
				
				this.propagateChunkLightPosList(skyLightWorldPosQueue, adjacentChunkHolder,
						(neighbourChunk, relBlockPos) -> neighbourChunk.getDhSkyLight(relBlockPos.getX(), relBlockPos.getY(), relBlockPos.getZ()),
						(neighbourChunk, relBlockPos, newLightValue) -> neighbourChunk.setDhSkyLight(relBlockPos.getX(), relBlockPos.getY(), relBlockPos.getZ(), newLightValue),
						false);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected lighting issue for center chunk: "+centerChunkPos, e);
		}
		finally
		{
			StableLightPosStack.returnStableLightPosArray(blockLightWorldPosQueue);
			StableLightPosStack.returnStableLightPosArray(skyLightWorldPosQueue);
		}
		
		
		if (updateBlockLight)
		{
			centerChunk.setIsDhBlockLightCorrect(true);
		}
		if (updateSkyLight)
		{
			centerChunk.setIsDhSkyLightCorrect(true);
		}
	}
	
	/** Applies each {@link LightPos} from the queue to the given set of {@link IChunkWrapper}'s. */
	private void propagateChunkLightPosList(
			StableLightPosStack lightPosQueue, AdjacentChunkHolder adjacentChunkHolder,
			IGetLightFunc getLightFunc, ISetLightFunc setLightFunc,
			boolean propagatingBlockLights)
	{
		// these objects are saved so they can be mutated throughout the method,
		// this reduces the number of allocations necessary, reducing GC pressure
		final LightPos lightPos = new LightPos(0, 0, 0, 0);
		final DhBlockPosMutable neighbourBlockPos = PRIMARY_BLOCK_POS_REF.get();
		final DhBlockPosMutable relNeighbourBlockPos = SECONDARY_BLOCK_POS_REF.get();
		
		IMutableBlockPosWrapper mcBlockPos = null;
		IBlockStateWrapper previousBlockState = null;
		
		// update each light position
		while (!lightPosQueue.isEmpty())
		{
			// since we don't care about the order the positions are processed,
			// we can grab the last position instead of the first for a slight performance increase (this way the array doesn't need to be shifted over every loop)
			lightPosQueue.popMutate(lightPos);
			
			int lightValue = lightPos.lightValue;
			
			
			// propagate the lighting in each cardinal direction, IE: -x, +x, -y, +y, -z, +z
			for (EDhDirection direction : EDhDirection.CARDINAL_DIRECTIONS) // since this is an array instead of an ArrayList this advanced for-loop shouldn't cause any GC issues
			{
				lightPos.mutateOffset(direction, neighbourBlockPos);
				neighbourBlockPos.mutateToChunkRelativePos(relNeighbourBlockPos);
				
				
				// only continue if the light position is inside one of our chunks
				IChunkWrapper neighbourChunk = adjacentChunkHolder.getByBlockPos(neighbourBlockPos.getX(), neighbourBlockPos.getZ());
				if (neighbourChunk == null)
				{
					// the light pos is outside our generator's range, ignore it
					continue;
				}
				
				if (relNeighbourBlockPos.getY() < neighbourChunk.getMinNonEmptyHeight() || relNeighbourBlockPos.getY() > neighbourChunk.getExclusiveMaxBuildHeight())
				{
					// the light pos is outside the chunk's min/max height,
					// this can happen if given a chunk that hasn't finished generating
					continue;
				}
				
				
				int currentBlockLight = getLightFunc.getLight(neighbourChunk, relNeighbourBlockPos);
				if (currentBlockLight >= (lightValue - 1))
				{
					// short circuit for when the light value at this position
					// is already greater-than what we could set it
					continue;
				}
				
				
				if (mcBlockPos == null)
				{
					// it doesn't matter what chunk we get the position object from
					// TODO move this getter logic out of ChunkWrapper
					mcBlockPos = neighbourChunk.getMutableBlockPosWrapper();
				}
				
				
				IBlockStateWrapper neighbourBlockState = previousBlockState = neighbourChunk.getBlockState(relNeighbourBlockPos, mcBlockPos, previousBlockState);
				// Math.max(1, ...) is used so that the propagated light level always drops by at least 1, preventing infinite cycles.
				int targetLevel = lightValue - Math.max(1, neighbourBlockState.getOpacity());
				if (targetLevel > currentBlockLight)
				{
					// this position is darker than the new light value, update/set it
					setLightFunc.setLight(neighbourChunk, relNeighbourBlockPos, targetLevel);
					
					// now that light has been propagated to this blockPos
					// we need to queue it up so its neighbours can be propagated as well
					lightPosQueue.push(neighbourBlockPos.getX(), neighbourBlockPos.getY(), neighbourBlockPos.getZ(), targetLevel);
				}
			}
		}
		
		
		// can be enable if troubleshooting lighting issues
		if (RENDER_BLOCK_LIGHT_WIREFRAME && propagatingBlockLights)
		{
			RenderDhLightValuesAsWireframe(adjacentChunkHolder, true);
		}
		else if (RENDER_SKY_LIGHT_WIREFRAME && !propagatingBlockLights)
		{
			RenderDhLightValuesAsWireframe(adjacentChunkHolder, false);
		}
		
		
		// propagation complete
	}
	
	
	
	//======================//
	// data source lighting //
	//======================//
	
	/** @author BuilderB0y */
	public void bakeDataSourceSkyLight(FullDataSourceV2 dataSource, int maxSkyLight)
	{
		// create a cache of all the IDs which are completely transparent.
		// FullDataPointIdMap is thread-safe with locks, and is also a map lookup,
		// and both of these things add a bit of overhead which is not necessary
		// in this context.
		// note: since IDs map to both biomes and blocks, there can be more than
		// one ID which corresponds to air.
		BitSet airIDs = new BitSet(dataSource.mapping.size());
		for (int id = 0, size = dataSource.mapping.size(); id < size; id++)
		{
			if (dataSource.mapping.getBlockStateWrapper(id).getOpacity() == 0)
			{
				airIDs.set(id, true);
			}
		}
		
		
		for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
		{
			for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
			{
				LongArrayList dataPoints = dataSource.get(x, z);
				if (dataPoints != null && !dataPoints.isEmpty())
				{
					// iterate through the data points in this column top-down
					// until we reach light level 0 in some way. at this point,
					// no more propagation needs to be performed for this column.
					int size = dataPoints.size();
					for (int index = 0; index < size; index++)
					{
						long point = dataPoints.getLong(index);
						// if the data point in the column is transparent,
						// then fill it with light and then propagate 
						// that light both horizontally and downwards.
						if (airIDs.get(FullDataPointUtil.getId(point)))
						{
							int skylight;
							if (index == 0)
							{
								// top-most data point in the column.
								skylight = maxSkyLight;
							}
							else
							{
								// handle down propagation here. sort of.
								// down propagation is also handled partially elsewhere.
								// basically if the data point above is transparent,
								// we copy its light level.
								// otherwise, if the data point above is opaque,
								// then no light can propagate downwards from it.
								// therefore, this data point should be light level 0*
								// and no more propagation needs to be performed for this column.
								//
								// *unless light propagates into it horizontally,
								// but that is handled separately.
								long above = dataPoints.getLong(index - 1);
								if (airIDs.get(FullDataPointUtil.getId(above)))
								{
									skylight = FullDataPointUtil.getSkyLight(above);
								}
								else
								{
									continue;
								}
							}
							
							// update the data point to contain the correct starting skylight level.
							point = FullDataPointUtil.setSkyLight(point, skylight);
							dataPoints.set(index, point);
							// now for the propagation.
							recursivelyLightAdjacentDataPoints(dataSource, airIDs, x, z, point);
						}
					}
				}
			}
		}
		
		
		// at this point, all transparent data points have been lit,
		// but opaque ones still have light level 0.
		// in this loop we make opaque data points copy the light level
		// above them if, and only if, the data point above is translucent.
		// with one exception: if the data point above is only partially translucent,
		// we use a slightly different way of computing how much light it absorbed.
		// this is how we handle water and ocean floors.
		// note that this alternate logic assumes the 
		// data point above is being lit from the top.
		// this is a fine assumption for water and oceans.
		for (LongArrayList list : dataSource.dataPoints)
		{
			if (list != null)
			{
				for (int index = 0, size = list.size(); index < size; index++)
				{
					long dataPoint = list.getLong(index);
					if (index == 0)
					{
						// top data point, assume "above" has the max sky light.
						dataPoint = FullDataPointUtil.setSkyLight(dataPoint, maxSkyLight);
						list.set(index, dataPoint);
					}
					else
					{
						// there is another data point above this one.
						// check to see how opaque this data point is first.
						// we will check the above one after that.
						if (!airIDs.get(FullDataPointUtil.getId(dataPoint)))
						{
							// this data point is not transparent.
							// it should be lit from above.
							long above = list.getLong(index - 1);
							int aboveLight = FullDataPointUtil.getSkyLight(above);
							if (airIDs.get(FullDataPointUtil.getId(above)))
							{
								// the above data point is transparent,
								// and does not absorb any light.
								// its light level can be copied as-is.
								dataPoint = FullDataPointUtil.setSkyLight(dataPoint, aboveLight);
								list.set(index, dataPoint);
							}
							else
							{
								// determine how much light should be absorbed by this column
								int absorption = dataSource.mapping.getBlockStateWrapper(FullDataPointUtil.getId(above)).getOpacity() * FullDataPointUtil.getHeight(above);
								if (absorption < aboveLight)
								{
									// the above data point is partially translucent,
									// and absorbs some light. however, it did not absorb
									// enough light to bring the light level down to 0.
									// so, the remaining light can still be copied.
									dataPoint = FullDataPointUtil.setSkyLight(dataPoint, aboveLight - absorption);
									list.set(index, dataPoint);
								}
							}
						}
					}
				}
			}
		}
	}
	
	/** @author BuilderB0y */
	public void recursivelyLightAdjacentDataPoints(
			FullDataSourceV2 chunk,
			BitSet airIDs,
			int relativeX,
			int relativeZ,
			long dataPoint
		)
	{
		int lightLevel = FullDataPointUtil.getSkyLight(dataPoint);
		// early exit condition:
		// in this case, propagating light is guaranteed to be 0 at adjacent positions,
		// and therefore we do not need to waste time propagating it.
		if (lightLevel <= 1)
		{
			return;
		}
		
		
		
		int minY = FullDataPointUtil.getBottomY(dataPoint);
		int maxY = FullDataPointUtil.getHeight(dataPoint) + minY;
		// try to propagate in all 4 directions.
		for (int offsetIndex = 0; offsetIndex < ADJACENT_DIRECTION_OFFSETS.length; )
		{
			int adjacentX = relativeX + ADJACENT_DIRECTION_OFFSETS[offsetIndex++];
			int adjacentZ = relativeZ + ADJACENT_DIRECTION_OFFSETS[offsetIndex++];
			
			// check if the adjacent position is within the bounds of this data source...
			if (adjacentX >= 0 && adjacentX < FullDataSourceV2.WIDTH && adjacentZ >= 0 && adjacentZ < FullDataSourceV2.WIDTH)
			{
				LongArrayList adjacentDataPoints = chunk.get(adjacentX, adjacentZ);
				// ...and also check to make sure we have some data points
				// (potentially transparent ones) to propagate through in the adjacent column.
				if (adjacentDataPoints != null)
				{
					// try to find adjacent data points we can propagate into.
					// we go top-down for this, which will be important for some
					// later conditions.
					int size = adjacentDataPoints.size();
					for (int adjacentIndex = 0; adjacentIndex < size; adjacentIndex++)
					{
						long adjacentDataPoint = adjacentDataPoints.getLong(adjacentIndex);
						int adjacentMinY = FullDataPointUtil.getBottomY(adjacentDataPoint);
						int adjacentMaxY = FullDataPointUtil.getHeight(adjacentDataPoint) + adjacentMinY;
						if (adjacentMinY >= maxY)
						{
							// if the adjacent data point is completely above this one,
							// then there is no overlap between this one and the adjacent one,
							// and therefore light cannot propagate here.
							// try to propagate to the next data point down from the adjacent one.
							continue;
						}
						else if (adjacentMaxY <= minY)
						{
							// if the adjacent data point is completely below this one,
							// then it also has no overlap and can't propagate,
							// but since we're going top-down, neither can any subsequent adjacent data points.
							break;
						}
						else if (!airIDs.get(FullDataPointUtil.getId(adjacentDataPoint)))
						{
							// assume for now that we cannot propagate into non-transparent data points.
							continue; // TODO how does this work with water? Do we care?
						}
						else
						{
							// now we can try to propagate.
							int adjacentLightLevel = FullDataPointUtil.getSkyLight(adjacentDataPoint);
							// if the resulting light level after propagation would INCREASE
							// the light level of the adjacent data point, then propagate to it.
							// otherwise, don't do that.
							if (lightLevel - 1 > adjacentLightLevel)
							{
								adjacentDataPoint = FullDataPointUtil.setSkyLight(adjacentDataPoint, lightLevel - 1);
								adjacentDataPoints.set(adjacentIndex, adjacentDataPoint);
								// if propagation succeeded, recursively propagate again starting at the adjacent data point.
								recursivelyLightAdjacentDataPoints(chunk, airIDs, adjacentX, adjacentZ, adjacentDataPoint);
							}
						}
					}
				}
			}
		}
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	/** Draw a wireframe representing each block's light value */
	private static void RenderDhLightValuesAsWireframe(AdjacentChunkHolder adjacentChunkHolder, boolean renderBlockLights)
	{
		for (IChunkWrapper chunk : adjacentChunkHolder.chunkArray)
		{
			if (chunk == null)
			{
				continue;
			}
			
			
			
			int chunkMinX = chunk.getMinBlockX();
			int chunkMinZ = chunk.getMinBlockZ();
			
			int minY = chunk.getMinNonEmptyHeight();
			int maxY = chunk.getMaxNonEmptyHeight();
			
			// check each position's light
			for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
			{
				for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
				{
					for (int y = minY; y < maxY; y++)
					{
						int lightValue = renderBlockLights? chunk.getDhBlockLight(x, y, z) : chunk.getDhSkyLight(x, y, z);
						if (lightValue != LodUtil.MIN_MC_LIGHT)
						{
							// hotter colors for more intense light
							Color color;
							if (lightValue >= 14)
							{
								color = Color.WHITE;
							}
							else if (lightValue >= 10)
							{
								color = Color.PINK;
							}
							else if (lightValue >= 6)
							{
								color = Color.YELLOW;
							}
							else if (lightValue >= 4)
							{
								color = Color.ORANGE;
							}
							else
							{
								color = Color.RED;
							}
							
							
							// a color can be set to null if you only want to troubleshoot up to a certain light level
							if (color != null)
							{
								DebugRenderer.makeParticle(
									new DebugRenderer.BoxParticle(
										new DebugRenderer.Box(DhSectionPos.encode((byte) 0, chunkMinX + x, chunkMinZ + z), y, y + 1, 0.2f, color),
										10.0, 0f
									)
								);
							}
						}
					}
				}
			}
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	interface IGetLightFunc { int getLight(IChunkWrapper chunk, DhBlockPos pos); }
	
	@FunctionalInterface
	interface ISetLightFunc { void setLight(IChunkWrapper chunk, DhBlockPos pos, int lightValue); }
	
	private static class LightPos extends DhBlockPosMutable
	{
		public int lightValue;
		
		public LightPos(int x, int y, int z, int lightValue)
		{
			super(x, y, z);
			this.lightValue = lightValue;
		}
		
		
		@Override
		public String toString() { return this.lightValue+" - ["+ this.x +", "+ this.y +", "+ this.z +"]"; }
		
		
	}
	
	/** 
	 * Holds all potential {@link LightPos} objects a lighting task may need.
	 * This is done so existing {@link LightPos} objects can be repurposed instead of destroyed,
	 * reducing garbage collector load.
	 */
	private static class StableLightPosStack
	{
		/** necessary to prevent multiple threads from modifying the cache at once */
		private static final ReentrantLock cacheLock = new ReentrantLock();
		private static final Queue<StableLightPosStack> lightArrayCache = new ArrayDeque<>();
		
		/** the index of the last item in the array, -1 if empty */
		private int index = -1;

		/** x, y, z, and lightValue. */
		public static final int INTS_PER_LIGHT_POS = 4;
		
		/**
		 * When tested with a normal 1.20 world James saw a maximum of 36,709 block and 2,355 sky lights,
		 * so 40,000 should be a good starting point that can contain most lighting tasks.
		 */
		private final IntArrayList lightPositions = new IntArrayList(40_000 * INTS_PER_LIGHT_POS);
		
		
		
		//================//
		// cache handling //
		//================//
		
		private static StableLightPosStack borrowStableLightPosArray()
		{
			try
			{
				// prevent multiple threads modifying the cache at once
				cacheLock.lock();
				
				return lightArrayCache.isEmpty() ? new StableLightPosStack() : lightArrayCache.remove();
			}
			finally
			{
				cacheLock.unlock();
			}
		}
		
		private static void returnStableLightPosArray(StableLightPosStack stableArray)
		{
			try
			{
				// prevent multiple threads modifying the cache at once
				cacheLock.lock();
				
				if (stableArray != null)
				{
					lightArrayCache.add(stableArray);
				}
			}
			finally
			{
				cacheLock.unlock();
			}
		}
		
		
		
		//===============//
		// stack methods //
		//===============//
		
		public boolean isEmpty() { return this.index == -1; }
		public int size() { return this.index+1; }
		
		public void push(int blockX, int blockY, int blockZ, int lightValue)
		{
			this.index++;
			int subIndex = this.index * INTS_PER_LIGHT_POS;
			if (subIndex < this.lightPositions.size())
			{
				this.lightPositions.set(subIndex, blockX);
				this.lightPositions.set(subIndex + 1, blockY);
				this.lightPositions.set(subIndex + 2, blockZ);
				this.lightPositions.set(subIndex + 3, lightValue);
			}
			else
			{
				// add a new pos
				this.lightPositions.add(blockX);
				this.lightPositions.add(blockY);
				this.lightPositions.add(blockZ);
				this.lightPositions.add(lightValue);
			}
		}
		
		/** mutates the given {@link LightPos} to match the next {@link LightPos} in the queue. */
		public void popMutate(LightPos pos)
		{
			int subIndex = this.index * INTS_PER_LIGHT_POS;
			
			pos.setX(this.lightPositions.getInt(subIndex));
			pos.setY(this.lightPositions.getInt(subIndex + 1));
			pos.setZ(this.lightPositions.getInt(subIndex + 2));
			pos.lightValue = this.lightPositions.getInt(subIndex + 3);
			
			this.index--;
		}
		
		@Override
		public String toString() { return this.index + "/" + (this.lightPositions.size() / INTS_PER_LIGHT_POS); }
		
	}
	
}
