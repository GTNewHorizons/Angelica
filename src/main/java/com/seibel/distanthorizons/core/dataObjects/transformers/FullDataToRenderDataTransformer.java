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

import com.seibel.distanthorizons.api.enums.config.EDhApiBlocksToAvoid;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * Handles converting {@link FullDataSourceV2}'s to {@link ColumnRenderSource}.
 */
public class FullDataToRenderDataTransformer
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final LongOpenHashSet brokenPos = new LongOpenHashSet();
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Data Transformer");
	
	
	
	//==============================//
	// public transformer interface //
	//==============================//
	
	@Nullable
	public static ColumnRenderSource transformFullDataToRenderSource(@Nullable FullDataSourceV2 fullDataSource, @Nullable IDhClientLevel level)
	{
		if (fullDataSource == null)
		{
			return null;
		}
		else if (level == null)
		{
			// if the client is no longer loaded in the world, render sources cannot be created 
			return null;
		}
		
		
		try
		{
			return transformCompleteFullDataToColumnData(level, fullDataSource);
		}
		catch (InterruptedException e)
		{
			return null;
		}
	}
	
	
	
	//==============//
	// transformers //
	//==============//
	
	/**
	 * Creates a LodNode for a chunk in the given world.
	 *
	 * @throws IllegalArgumentException thrown if either the chunk or world is null.
	 * @throws InterruptedException Can be caused by interrupting the thread upstream.
	 * Generally thrown if the method is running after the client leaves the current world.
	 */
	private static ColumnRenderSource transformCompleteFullDataToColumnData(IDhClientLevel level, FullDataSourceV2 fullDataSource) throws InterruptedException
	{
 		final long pos = fullDataSource.getPos();
		final byte dataDetail = fullDataSource.getDataDetailLevel();
		
		final int vertSize = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxVerticalData(fullDataSource.getDataDetailLevel());
		
		
		
		final ColumnRenderSource columnSource = ColumnRenderSource.createEmpty(pos, vertSize, level.getMinY());
		if (fullDataSource.isEmpty)
		{
			return columnSource;
		}
		
		columnSource.markNotEmpty();
		int baseX = DhSectionPos.getMinCornerBlockX(pos);
		int baseZ = DhSectionPos.getMinCornerBlockZ(pos);
		
		for (int x = 0; x < DhSectionPos.getWidthCountForLowerDetailedSection(pos, dataDetail); x++)
		{
			for (int z = 0; z < DhSectionPos.getWidthCountForLowerDetailedSection(pos, dataDetail); z++)
			{
				throwIfThreadInterrupted();
				
				ColumnArrayView columnArrayView = columnSource.getVerticalDataPointView(x, z);
				LongArrayList dataColumn = fullDataSource.get(x, z);
				
				updateOrReplaceRenderDataViewColumnWithFullDataColumn(
						level, fullDataSource.mapping, 
						// bitshift is to account for LODs with a detail level greater than 0 so the block pos is correct
						baseX + BitShiftUtil.pow(x,dataDetail), baseZ + BitShiftUtil.pow(z,dataDetail), 
						columnArrayView, dataColumn);
			}
		}
		
		columnSource.fillDebugFlag(0, 0, ColumnRenderSource.SECTION_SIZE, ColumnRenderSource.SECTION_SIZE, ColumnRenderSource.DebugSourceFlag.FULL);
			
		return columnSource;
	}
	
	/** Updates the given {@link ColumnArrayView} to match the incoming Full data {@link LongArrayList} */
	public static void updateOrReplaceRenderDataViewColumnWithFullDataColumn(
			IDhClientLevel level, 
			FullDataPointIdMap fullDataMapping, int blockX, int blockZ, 
			ColumnArrayView columnArrayView, 
			LongArrayList fullDataColumn)
	{
		// we can't do anything if the full data is missing or empty
		if (fullDataColumn == null || fullDataColumn.size() == 0)
		{
			return;
		}
		
		int fullDataLength = fullDataColumn.size();
		if (fullDataLength <= columnArrayView.verticalSize())
		{
			// Directly use the arrayView since it fits.
			setRenderColumnView(level, fullDataMapping, blockX, blockZ, columnArrayView, fullDataColumn);
		}
		else
		{
			PhantomArrayListCheckout checkout = ARRAY_LIST_POOL.checkoutArrays(0, 0, 1);
			LongArrayList dataArrayList = checkout.getLongArray(0, fullDataLength);
			
			try
			{
				// expand the ColumnArrayView to fit the new larger max vertical size
				ColumnArrayView newColumnArrayView = new ColumnArrayView(dataArrayList, fullDataLength, 0, fullDataLength);
				setRenderColumnView(level, fullDataMapping, blockX, blockZ, newColumnArrayView, fullDataColumn);
				columnArrayView.changeVerticalSizeFrom(newColumnArrayView);
			}
			finally
			{
				ARRAY_LIST_POOL.returnCheckout(checkout);
			}
		}
	}
	private static void setRenderColumnView(
			IDhClientLevel level, FullDataPointIdMap fullDataMapping,
			int blockX, int blockZ,
			ColumnArrayView renderColumnData, LongArrayList fullColumnData)
	{
		//===============//
		// config values //
		//===============//
		
		boolean ignoreNonCollidingBlocks = (Config.Client.Advanced.Graphics.Quality.blocksToIgnore.get() == EDhApiBlocksToAvoid.NON_COLLIDING);
		boolean colorBelowWithAvoidedBlocks = Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks.get();
		
		HashSet<IBlockStateWrapper> blockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredBlocks(level.getLevelWrapper());
		HashSet<IBlockStateWrapper> caveBlockStatesToIgnore = WRAPPER_FACTORY.getRendererIgnoredCaveBlocks(level.getLevelWrapper());
		
		int caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - level.getMinY();
		boolean caveCullingEnabled = 
			Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get()
			&& (
				// dimensions with a ceiling will be all caves so we don't want cave culling
				!level.getLevelWrapper().hasCeiling()
				// the end has a lot of overhangs with 0 lighting above the void, which look broken with
				// the current cave culling logic (this could probably be improved, but just skipping it works best for now)
				&& !level.getLevelWrapper().getDimensionType().isTheEnd()
			);
		
		boolean isColumnVoid = true;
		
		int colorToApplyToNextBlock = -1;
		int lastColor = 0;
		int lastBottom = -10_000;
		
		int skylightToApplyToNextBlock = -1;
		int blocklightToApplyToNextBlock = -1;
		int renderDataIndex = 0;
		
		
		
		//==================================//
		// convert full data to render data //
		//==================================//
		
		DhBlockPosMutable mutableBlockPos = new DhBlockPosMutable(blockX, 0, blockZ);
		
		// goes from the top down
		for (int fullDataIndex = 0; fullDataIndex < fullColumnData.size(); fullDataIndex++)
		{
			long fullData = fullColumnData.getLong(fullDataIndex);
			
			int bottomY = FullDataPointUtil.getBottomY(fullData);
			int blockHeight = FullDataPointUtil.getHeight(fullData);
			int topY = bottomY + blockHeight;
			int id = FullDataPointUtil.getId(fullData);
			int blockLight = FullDataPointUtil.getBlockLight(fullData);
			int skyLight = FullDataPointUtil.getSkyLight(fullData);
			
			mutableBlockPos.setY(bottomY + level.getMinY());
			
			IBiomeWrapper biome;
			IBlockStateWrapper block;
			try
			{
				biome = fullDataMapping.getBiomeWrapper(id);
				block = fullDataMapping.getBlockStateWrapper(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				if (!brokenPos.contains(fullDataMapping.getPos()))
				{
					brokenPos.add(fullDataMapping.getPos());
					String levelId = level.getLevelWrapper().getDhIdentifier();
					LOGGER.warn("Unable to get data point with id ["+id+"] " +
							"(Max possible ID: ["+fullDataMapping.getMaxValidId()+"]) " +
							"for pos ["+fullDataMapping.getPos()+"] in level ["+levelId+"]. " +
							"Error: ["+e.getMessage()+"]. " +
							"Further errors for this position won't be logged.");
				}
				
				// don't render broken data
				continue;
			}
			
			blockLight = Math.max(blockLight, block.getLightEmission());
			
			//====================//
			// ignored block and  //
			// cave culling check //
			//====================//
			
			boolean ignoreBlock = blockStatesToIgnore.contains(block);
			boolean caveBlock = caveBlockStatesToIgnore.contains(block);
			if (caveBlock)
			{
				if (caveCullingEnabled
					// assume this data point is underground if it has no sky-light
					&& skyLight == LodUtil.MIN_MC_LIGHT	
					// ignore caves above a certain height to prevent floating islands from having walls underneath them
					&& topY < caveCullingMaxY
					// cave culling shouldn't happen when at the top of the world
					&& renderDataIndex != 0 && fullDataIndex != 0
					// cave culling can't happen when at the bottom of the world
					&& (fullDataIndex+1) < fullColumnData.size())
				{
					// we need to get the next sky/block lights because
					// the air block here will always have a light of 0/0 due to only the top of the LOD's light being saved.
					long nextFullData = fullColumnData.getLong(fullDataIndex+1);
					int nextSkyLight = FullDataPointUtil.getSkyLight(nextFullData);
					
					if (nextSkyLight == LodUtil.MIN_MC_LIGHT
							&& ColorUtil.getAlpha(lastColor) == 255)
					{
						// replace the previous block with new bottom
						long columnData = renderColumnData.get(renderDataIndex - 1);
						columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
						renderColumnData.set(renderDataIndex - 1, columnData);
					}
					
					continue;
				}
				
				
				if (ignoreBlock)
				{
					// this is a merged block and a cave block, so it should never be rendered
					continue;
				}
			}
			else if (ignoreBlock)
			{
				// this is an ignored block, but shouldn't be merged like a cave block
				continue;
			}
			
			
			
			//=======================//
			// non-solid block check //
			//=======================//
			
			if (ignoreNonCollidingBlocks 
				&& !block.isSolid() && !block.isLiquid() && block.getOpacity() != LodUtil.BLOCK_FULLY_OPAQUE)
			{
				if (colorBelowWithAvoidedBlocks)
				{
					int tempColor = level.computeBaseColor(mutableBlockPos, biome, block);
					// don't transfer the color when alpha is 0
					// this prevents issues if grass is transparent
					if (ColorUtil.getAlpha(tempColor) != 0)
					{
						colorToApplyToNextBlock = ColorUtil.setAlpha(tempColor,255);
						skylightToApplyToNextBlock = skyLight;
						blocklightToApplyToNextBlock = blockLight;
					}
				}
				
				// skip this non-colliding block
				continue;
			}
			
			
			int color;
			if (colorToApplyToNextBlock == -1)
			{
				// use this block's color
				color = level.computeBaseColor(mutableBlockPos, biome, block);
			}
			else
			{
				// use the previous block's color
				color = colorToApplyToNextBlock;
				colorToApplyToNextBlock = -1;
				skyLight = skylightToApplyToNextBlock;
				blockLight = blocklightToApplyToNextBlock;
			}
			
			
			
			//=============================//
			// merge same-colored adjacent //
			//=============================//
			
			// check if they share a top-bottom face and if they have same color
			if (color == lastColor && bottomY + blockHeight == lastBottom  && renderDataIndex > 0)
			{
				//replace the previous block with new bottom
				long columnData = renderColumnData.get(renderDataIndex - 1);
				columnData = RenderDataPointUtil.setYMin(columnData, bottomY);
				renderColumnData.set(renderDataIndex - 1, columnData);
			}
			else
			{
				// add the block
				isColumnVoid = false;
				long columnData = RenderDataPointUtil.createDataPoint(bottomY + blockHeight, bottomY, color, skyLight, blockLight, block.getMaterialId());
				renderColumnData.set(renderDataIndex, columnData);
				renderDataIndex++;
			}
			lastBottom = bottomY;
			lastColor = color;
		}
		
		
		if (isColumnVoid)
		{
			renderColumnData.set(0, RenderDataPointUtil.EMPTY_DATA);
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * Called in loops that may run for an extended period of time. <br>
	 * This is necessary to allow canceling these transformers since running
	 * them after the client has left a given world will throw exceptions.
	 */
	private static void throwIfThreadInterrupted() throws InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException(FullDataToRenderDataTransformer.class.getSimpleName() + " task interrupted.");
		}
	}
	
}
