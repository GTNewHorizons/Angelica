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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ColumnBox
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	/** 
	 * if the skylight has this value that means 
	 * no data is expected 
	 */
	private static final byte SKYLIGHT_EMPTY = -1;
	/** 
	 * if the skylight has this value that means
	 * that block position is covered/occuled by an adjacent block/column.
	 */
	private static final byte SKYLIGHT_COVERED = -2;
	
	private static final ThreadLocal<byte[]> THREAD_LOCAL_SKY_LIGHT_ARRAY = ThreadLocal.withInitial(() ->
	{
		byte[] array = new byte[RenderDataPointUtil.MAX_WORLD_Y_SIZE];
		Arrays.fill(array, SKYLIGHT_EMPTY);
		return array;
	});
	
	
	
	//=========//
	// builder //
	//=========//
	
	public static void addBoxQuadsToBuilder(
			LodQuadBuilder builder, IDhClientLevel clientLevel,
			short xSize, short ySize, short zSize,
			short minX, short minY, short minZ,
			int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
			long topData, long bottomData, ColumnArrayView[] adjData, boolean[] isAdjDataSameDetailLevel)
	{
		//================//
		// variable setup //
		//================//
		
		short maxX = (short) (minX + xSize);
		short maxY = (short) (minY + ySize);
		short maxZ = (short) (minZ + zSize);
		byte skyLightTop = skyLight;
		byte skyLightBot = RenderDataPointUtil.doesDataPointExist(bottomData) ? RenderDataPointUtil.getLightSky(bottomData) : 0;
		
		boolean isTransparent = ColorUtil.getAlpha(color) < 255 && LodRenderer.transparencyEnabled;
		boolean overVoid = !RenderDataPointUtil.doesDataPointExist(bottomData);
		boolean isTopTransparent = RenderDataPointUtil.getAlpha(topData) < 255 && LodRenderer.transparencyEnabled;
		boolean isBottomTransparent = RenderDataPointUtil.getAlpha(bottomData) < 255 && LodRenderer.transparencyEnabled;
		
		// defaulting to a value far below what we can normally render means we
		// don't need to have an additional "is cave culling enabled" check
		int caveCullingMaxY = Integer.MIN_VALUE;
		if (Config.Client.Advanced.Graphics.Culling.enableCaveCulling.get())
		{
			caveCullingMaxY = Config.Client.Advanced.Graphics.Culling.caveCullingHeight.get() - clientLevel.getMinY();
		}
		
		
		
		// if there isn't any data below this LOD, make this LOD's color opaque to prevent seeing void through transparent blocks
		// Note: this LOD should still be considered transparent for this method's checks, otherwise rendering bugs may occur
		if (!RenderDataPointUtil.doesDataPointExist(bottomData))
		{
			color = ColorUtil.setAlpha(color, 255);
		}
		
		
		// fake ocean transparency
		if (LodRenderer.transparencyEnabled && LodRenderer.fakeOceanFloor)
		{
			if (!isTransparent && isTopTransparent && RenderDataPointUtil.doesDataPointExist(topData))
			{
				skyLightTop = (byte) MathUtil.clamp(0, 15 - (RenderDataPointUtil.getYMax(topData) - minY), 15);
				ySize = (short) (RenderDataPointUtil.getYMax(topData) - minY - 1);
			}
			else if (isTransparent && !isBottomTransparent && RenderDataPointUtil.doesDataPointExist(bottomData))
			{
				minY = (short) (minY + ySize - 1);
				ySize = 1;
			}
			
			maxY = (short) (minY + ySize);
		}
		
		
		
		//==========================//
		// add top and bottom faces //
		//==========================//
		
		boolean skipTop = RenderDataPointUtil.doesDataPointExist(topData) && (RenderDataPointUtil.getYMin(topData) == maxY) && !isTopTransparent;
		if (!skipTop)
		{
			builder.addQuadUp(minX, maxY, minZ, xSize, zSize, ColorUtil.applyShade(color, MC.getShade(EDhDirection.UP)), irisBlockMaterialId, skyLightTop, blockLight);
		}
		
		boolean skipBottom = RenderDataPointUtil.doesDataPointExist(bottomData) && (RenderDataPointUtil.getYMax(bottomData) == minY) && !isBottomTransparent;
		if (!skipBottom)
		{
			builder.addQuadDown(minX, minY, minZ, xSize, zSize, ColorUtil.applyShade(color, MC.getShade(EDhDirection.DOWN)), irisBlockMaterialId, skyLightBot, blockLight);
		}
		
		
		
		//========================================//
		// add North, south, east, and west faces //
		//========================================//
		
		// NORTH face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.NORTH.ordinal() - 2]; // TODO can we use something other than ordinal-2?
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.NORTH.ordinal() - 2];
			// if the adjacent column is null that generally means the adjacent area hasn't been generated yet
			if (adjCol == null)
			{
				// Add an adjacent face if this is opaque face or transparent over the void.
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(EDhDirection.NORTH, minX, minY, minZ, xSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(builder, adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.NORTH, minX, minY, minZ, xSize, ySize,
						color, irisBlockMaterialId, blockLight);
			}
		}
		
		// SOUTH face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.SOUTH.ordinal() - 2];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.SOUTH.ordinal() - 2];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(EDhDirection.SOUTH, minX, minY, maxZ, xSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(builder, adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.SOUTH, minX, minY, maxZ, xSize, ySize,
						color, irisBlockMaterialId, blockLight);
			}
		}
		
		// WEST face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.WEST.ordinal() - 2];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.WEST.ordinal() - 2];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(EDhDirection.WEST, minX, minY, minZ, zSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(builder, adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.WEST, minX, minY, minZ, zSize, ySize,
						color, irisBlockMaterialId, blockLight);
			}
		}
		
		// EAST face
		{
			ColumnArrayView adjCol = adjData[EDhDirection.EAST.ordinal() - 2];
			boolean adjSameDetailLevel = isAdjDataSameDetailLevel[EDhDirection.EAST.ordinal() - 2];
			if (adjCol == null)
			{
				if (!isTransparent || overVoid)
				{
					builder.addQuadAdj(EDhDirection.EAST, maxX, minY, minZ, zSize, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
				}
			}
			else
			{
				makeAdjVerticalQuad(builder, adjCol, adjSameDetailLevel, caveCullingMaxY, EDhDirection.EAST, maxX, minY, minZ, zSize, ySize,
						color, irisBlockMaterialId, blockLight);
			}
		}
	}
	
	private static void makeAdjVerticalQuad(
			LodQuadBuilder builder, @NotNull ColumnArrayView adjColumnView, boolean adjacentIsSameDetailLevel, int caveCullingMaxY, EDhDirection direction, 
			short x, short yMin, short z, short horizontalWidth, short ySize,
			int color, byte irisBlockMaterialId, byte blockLight)
	{
		//==================//
		// create face with //
		// no adjacent data //
		//==================//
		
		color = ColorUtil.applyShade(color, MC.getShade(direction));
		
		// if there isn't any data adjacent to this LOD,
		// just add the full vertical quad
		if (adjColumnView.size == 0 || RenderDataPointUtil.isVoid(adjColumnView.get(0)))
		{
			
			builder.addQuadAdj(direction, x, yMin, z, horizontalWidth, ySize, color, irisBlockMaterialId, LodUtil.MAX_MC_LIGHT, blockLight);
			return;
		}
		
		
		
		//===========================//
		// Determine face visibility //
		// based on it's neighbors   //
		//===========================//
		
		short yMax = (short) (yMin + ySize); // min is inclusive, max is exclusive
		byte[] skyLightAtInputPos = THREAD_LOCAL_SKY_LIGHT_ARRAY.get();
		
		try
		{
			// set the initial sky-lights for this face,
			// if nothing overlaps or overhangs the face should have max sky light
			Arrays.fill(skyLightAtInputPos, yMin, yMax, LodUtil.MAX_MC_LIGHT);
			
			// iterate top down
			int adjCount = adjColumnView.size();
			for (int adjIndex = 0; adjIndex < adjCount; adjIndex++)
			{
				long adjPoint = adjColumnView.get(adjIndex);
				short adjMinY = RenderDataPointUtil.getYMin(adjPoint);
				short adjMaxY = RenderDataPointUtil.getYMax(adjPoint);
				
				// skip empty adjacent datapoints
				if (!RenderDataPointUtil.doesDataPointExist(adjPoint)
						|| RenderDataPointUtil.isVoid(adjPoint))
				{
					continue;
				}
				
				// skip this adjacent datapoint if it's above the input datapoint (since it can't affect the input data point)
				if (yMax <= adjMinY)
				{
					continue;
				}
				
				
				long adjAbovePoint = (adjIndex != 0) ? adjColumnView.get(adjIndex - 1) : RenderDataPointUtil.EMPTY_DATA;
				long adjBelowPoint = (adjIndex + 1 < adjCount) ? adjColumnView.get(adjIndex + 1) : RenderDataPointUtil.EMPTY_DATA;
				
				// if the adjacent data point is over the void
				// don't consider it as transparent
				boolean adjOverVoid = !RenderDataPointUtil.doesDataPointExist(adjBelowPoint);
				boolean adjTransparent = !adjOverVoid && RenderDataPointUtil.getAlpha(adjPoint) < 255 && LodRenderer.transparencyEnabled;
				
				
				
				//=================================//
				// set sky light based on adjacent //
				//=================================//
				
				// set light based on overlapping adjacent
				if (!adjTransparent)
				{
					// adj opaque
					// mark positions adjacent is covering
					byte adjSkyLight = RenderDataPointUtil.getLightSky(adjPoint);
					for (int i = adjMinY; i < adjMaxY; i++)
					{
						byte skyLightAtPos = skyLightAtInputPos[i];
						
						// if the adjacent is a different detail level, we want to render adjacent opaque
						// faces to try and reduce the chance of holes on detail level borders
						boolean adjacentCoversThis = 
								// if the adjacent is the same detail level, no special handling is necessary
								!adjacentIsSameDetailLevel
								// if the adjacent face is underground we probably don't need it
								&& RenderDataPointUtil.getYMax(adjPoint) >= caveCullingMaxY
								// check if this face is on a border
								&& 
								(
									(x == 0 && direction == EDhDirection.WEST)
									|| (z == 0 && direction == EDhDirection.NORTH)
									// TODO why does 256 represent a border? aren't LODs only 64 datapoints wide?
									|| (x == 256 && direction == EDhDirection.EAST)
									|| (z == 256 && direction == EDhDirection.SOUTH)
								);
						
						byte newSkyLightAtPos = adjacentCoversThis ? adjSkyLight : SKYLIGHT_COVERED;
						skyLightAtInputPos[i] = (byte) Math.min(newSkyLightAtPos, skyLightAtPos);
					}
				}
				else
				{
					// adjacent is transparent,
					// use datapoint below adjacent for lighting
					byte belowSkyLight = RenderDataPointUtil.getLightSky(adjBelowPoint);
					for (int i = adjMinY; i < adjMaxY; i++)
					{
						byte skyLightAtPos = skyLightAtInputPos[i];
						skyLightAtInputPos[i] = (byte) Math.min(belowSkyLight, skyLightAtPos);
					}
				}
				
				
				// fill in sky light up to the next DP,
				// this is done to handle overhangs
				byte adjSkyLight = RenderDataPointUtil.getLightSky(adjPoint);
				int adjAboveMinY = RenderDataPointUtil.getYMin(adjAbovePoint);
				for (int i = adjMaxY; i < adjAboveMinY; i++)
				{
					byte skyLightAtPos = skyLightAtInputPos[i];
					skyLightAtInputPos[i] = (byte) Math.min(adjSkyLight, skyLightAtPos);
				}
			}
			
			
			
			//=======================//
			// create vertical faces //
			//=======================//
			
			boolean inputTransparent = ColorUtil.getAlpha(color) < 255 && LodRenderer.transparencyEnabled;
			byte lastSkyLight = skyLightAtInputPos[yMin];
			int quadBottomY = yMin;
			int quadTopY = -1;
			
			// walk up the sky lights and create a new face
			// whenever the light changes to different valid value
			for (int i = yMin; i < yMax; i++)
			{
				byte skyLight = skyLightAtInputPos[i];
				if (skyLight != lastSkyLight)
				{
					// the sky light changed, create the in-progress face
					tryAddVerticalFaceWithSkyLightToBuilder(
							builder, direction,
							x, z, horizontalWidth,
							color, irisBlockMaterialId, blockLight,
							lastSkyLight, inputTransparent, quadTopY, quadBottomY
					);
					
					lastSkyLight = skyLight;
					quadBottomY = i;
				}
				
				quadTopY = (i + 1);
			}
			
			// add the in-progress face if present
			if (quadTopY != -1)
			{
				tryAddVerticalFaceWithSkyLightToBuilder(
						builder, direction,
						x, z, horizontalWidth,
						color, irisBlockMaterialId, blockLight,
						lastSkyLight, inputTransparent, quadTopY, quadBottomY
				);
			}
		}
		finally
		{
			// clean up the array before the next thread uses it
			// (may be unnecessary since we only work between the yMin-yMax anyway, but is helpful for debugging)
			Arrays.fill(skyLightAtInputPos, yMin, yMax, SKYLIGHT_EMPTY);
		}
	}
	private static void tryAddVerticalFaceWithSkyLightToBuilder(
			LodQuadBuilder builder, EDhDirection direction,
			short x, short z, short horizontalWidth,
			int color, byte irisBlockMaterialId, byte blockLight,
			byte lastSkyLight, boolean inputTransparent, int quadTopY, int quadBottomY
			)
	{
		// invalid positions will have a negative skylight
		if (lastSkyLight >= 0)
		{
			// Don't add transparent vertical faces
			// unless the adjacent position is empty.
			// This is done to prevent walls between water blocks in the ocean.
			if (!inputTransparent
				|| (lastSkyLight == LodUtil.MAX_MC_LIGHT))
			{
				// don't add negative/empty height faces
				short height = (short) (quadTopY - quadBottomY);
				if (height > 0)
				{
					builder.addQuadAdj(direction, x, (short) quadBottomY, z, horizontalWidth, height, color, irisBlockMaterialId, lastSkyLight, blockLight);
				}
			}
		}
	}
	
	
	
}
