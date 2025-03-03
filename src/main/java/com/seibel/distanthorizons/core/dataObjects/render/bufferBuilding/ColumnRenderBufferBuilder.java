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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Used to populate the buffers in a {@link ColumnRenderSource} object.
 *
 * @see ColumnRenderSource
 */
public class ColumnRenderBufferBuilder
{
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logRendererBufferEvent.get());
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	//==============//
	// vbo building //
	//==============//
	
	/** @link adjData should be null for adjacent sections that cross detail level boundaries */
	public static CompletableFuture<ColumnRenderBuffer> uploadBuffersAsync(
			IDhClientLevel clientLevel,
			long pos,
			LodQuadBuilder quadBuilder
		)
	{
		ColumnRenderBuffer buffer = new ColumnRenderBuffer(new DhBlockPos(DhSectionPos.getMinCornerBlockX(pos), clientLevel.getMinY(), DhSectionPos.getMinCornerBlockZ(pos)));
		CompletableFuture<ColumnRenderBuffer> uploadFuture = buffer.makeAndUploadBuffersAsync(quadBuilder, GLProxy.getInstance().getGpuUploadMethod());
		uploadFuture.whenComplete((uploadedBuffer, exception) -> 
		{
			// clean up if not uploaded
			if (uploadedBuffer != null && !uploadedBuffer.buffersUploaded)
			{
				uploadedBuffer.close();
			}
		});
		return uploadFuture;
	}
	public static void makeLodRenderData(
			LodQuadBuilder quadBuilder, ColumnRenderSource renderSource, IDhClientLevel clientLevel,
			ColumnRenderSource[] adjRegions, boolean[] isSameDetailLevel)
	{
		//=============//
		// debug check //
		//=============//
		
		// can be used to limit which section positions are build and thus, rendered
		// useful when debugging a specific section
		boolean columnBuilderDebugEnabled = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get();
		if (columnBuilderDebugEnabled)
		{
			if (DhSectionPos.getDetailLevel(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugDetailLevel.get()
				&& DhSectionPos.getX(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXPos.get()
				&& DhSectionPos.getZ(renderSource.pos) == Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZPos.get())
			{
				int breakpoint = 0;
			}
			else
			{
				return;
			}
		}
		
		
		
		//===================//
		// build each column //
		//===================//
		
		byte thisDetailLevel = renderSource.getDataDetailLevel();
		for (int relX = 0; relX < ColumnRenderSource.SECTION_SIZE; relX++)
		{
			for (int relZ = 0; relZ < ColumnRenderSource.SECTION_SIZE; relZ++)
			{
				// stop the builder if requested
				UncheckedInterruptedException.throwIfInterrupted();
				
				// ignore empty/null columns
				ColumnArrayView columnRenderData = renderSource.getVerticalDataPointView(relX, relZ);
				if (columnRenderData.size() == 0
						|| !RenderDataPointUtil.doesDataPointExist(columnRenderData.get(0))
						|| RenderDataPointUtil.isVoid(columnRenderData.get(0)))
				{
					continue;
				}
				
				
				
				//=============//
				// debug limit //
				//=============//
				
				// can be used to limit the buffer building to a specific relative position.
				// useful for debugging a single column
				if (columnBuilderDebugEnabled)
				{
					int wantedX = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugXRow.get();
					if (wantedX >= 0 && relX != wantedX)
					{
						continue;
					}
					int wantedZ = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugZRow.get();
					if (wantedZ >= 0 && relZ != wantedZ)
					{
						continue;
					}
				}
				
				
				
				//==================================//
				// get adjacent render data columns //
				//==================================//
				
				ColumnArrayView[] adjColumnViews = new ColumnArrayView[EDhDirection.ADJ_DIRECTIONS.length];
				for (EDhDirection lodDirection : EDhDirection.ADJ_DIRECTIONS)
				{
					try
					{
						int xAdj = relX + lodDirection.getNormal().x;
						int zAdj = relZ + lodDirection.getNormal().z;
						boolean isCrossRenderSourceBoundary =
								(xAdj < 0 || xAdj >= ColumnRenderSource.SECTION_SIZE) ||
								(zAdj < 0 || zAdj >= ColumnRenderSource.SECTION_SIZE);
						
						ColumnRenderSource adjRenderSource;
						byte adjDetailLevel;
						
						
						
						//=========================//
						// get the adjacent render //
						// source if present       //
						//=========================//
						
						if (!isCrossRenderSourceBoundary)
						{
							// the adjacent position is inside this same render source
							adjRenderSource = renderSource;
							adjDetailLevel = thisDetailLevel;
						}
						else
						{
							// the adjacent position is outside this render source
							
							// skip empty sections
							adjRenderSource = adjRegions[lodDirection.ordinal() - 2];
							if (adjRenderSource == null)
							{
								continue;
							}
							
							adjDetailLevel = adjRenderSource.getDataDetailLevel();
							if (adjDetailLevel == thisDetailLevel)
							{
								// if the adjacent position is outside this render source,
								// wrap the position around so it's inside the adjacent source
								
								if (xAdj < 0)
								{
									xAdj += ColumnRenderSource.SECTION_SIZE;
								}
								if (xAdj >= ColumnRenderSource.SECTION_SIZE)
								{
									xAdj -= ColumnRenderSource.SECTION_SIZE;
								}
								
								if (zAdj < 0)
								{
									zAdj += ColumnRenderSource.SECTION_SIZE;
								}
								if (zAdj >= ColumnRenderSource.SECTION_SIZE)
								{
									zAdj -= ColumnRenderSource.SECTION_SIZE;
								}
							}
						}
						
						
						
						//========================//
						// get the adjacent views //
						//========================//
						
						// the old logic handled additional cases, but they never appeared to fire,
						// so just these two cases should be fine
						boolean expectedDetailLevels = (adjDetailLevel == thisDetailLevel) || (adjDetailLevel > thisDetailLevel);
						if (!expectedDetailLevels)
						{
							LodUtil.assertNotReach("Mismatch between adjacent detail level ["+adjDetailLevel+"] and this render source's detail level ["+thisDetailLevel+"]. Detail levels should be adj >= this.");	
						}
						
						adjColumnViews[lodDirection.ordinal() - 2] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
					}
					catch (RuntimeException e)
					{
						EVENT_LOGGER.warn("Failed to get adj data for relative pos: [" + thisDetailLevel + ":" + relX + "," + relZ + "] at [" + lodDirection + "], Error: "+e.getMessage(), e);
					}
				} // for adjacent directions
				
				
				
				//==========================//
				// build this render column //
				//==========================//
				
				ColumnRenderSource.DebugSourceFlag debugSourceFlag = renderSource.debugGetFlag(relX, relZ);
				
				// We render every vertical lod present in this position
				// We only stop when we find a block that is void or non-existing block
				for (int i = 0; i < columnRenderData.size(); i++)
				{
					// can be uncommented to limit which vertical LOD is generated
					if (Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugEnable.get())
					{
						int wantedColumnIndex = Config.Client.Advanced.Debugging.ColumnBuilderDebugging.columnBuilderDebugColumnIndex.get();
						if (wantedColumnIndex >= 0 && i != wantedColumnIndex)
						{
							continue;
						}
					}
					
					long data = columnRenderData.get(i);
					// If the data is not render-able (Void or non-existing) we stop since there is
					// no data left in this position
					if (RenderDataPointUtil.isVoid(data) || !RenderDataPointUtil.doesDataPointExist(data))
					{
						break;
					}
					
					long topDataPoint = (i - 1) >= 0 ? columnRenderData.get(i - 1) : RenderDataPointUtil.EMPTY_DATA;
					long bottomDataPoint = (i + 1) < columnRenderData.size() ? columnRenderData.get(i + 1) : RenderDataPointUtil.EMPTY_DATA;
					
					addLodToBuffer(
							clientLevel,
							data, topDataPoint, bottomDataPoint, 
							adjColumnViews, isSameDetailLevel,
							thisDetailLevel, relX, relZ, 
							quadBuilder, debugSourceFlag);
				}
				
			}// for z
		}// for x
		
		quadBuilder.finalizeData();
	}
	private static void addLodToBuffer(
			IDhClientLevel clientLevel,
			long data, long topData, long bottomData, 
			ColumnArrayView[] adjColumnViews, boolean[] isSameDetailLevel,
			byte detailLevel, int renderSourceOffsetPosX, int renderSourceOffsetPosZ, 
			LodQuadBuilder quadBuilder, ColumnRenderSource.DebugSourceFlag debugSource)
	{
		long sectionPos = DhSectionPos.encode(detailLevel, renderSourceOffsetPosX, renderSourceOffsetPosZ);
		
		short width = (short) BitShiftUtil.powerOfTwo(detailLevel);
		short xMin = (short) DhSectionPos.getMinCornerBlockX(sectionPos);
		short yMin = RenderDataPointUtil.getYMin(data);
		short zMin = (short) DhSectionPos.getMinCornerBlockZ(sectionPos);
		short ySize = (short) (RenderDataPointUtil.getYMax(data) - yMin);
		
		if (ySize == 0)
		{
			return;
		}
		else if (ySize < 0)
		{
			throw new IllegalArgumentException("Negative y size for the data! Data: [" + RenderDataPointUtil.toString(data) + "].");
		}
		
		byte blockMaterialId = RenderDataPointUtil.getBlockMaterialId(data);
		
		
		
		int color;
		boolean fullBright = false;
		EDhApiDebugRendering debugging = Config.Client.Advanced.Debugging.debugRendering.get();
		switch (debugging)
		{
			case OFF:
			{
				float saturationMultiplier = Config.Client.Advanced.Graphics.Quality.saturationMultiplier.get().floatValue();
				float brightnessMultiplier = Config.Client.Advanced.Graphics.Quality.brightnessMultiplier.get().floatValue();
				if (saturationMultiplier == 1.0 && brightnessMultiplier == 1.0)
				{
					color = RenderDataPointUtil.getColor(data);
				}
				else
				{
					float[] ahsv = ColorUtil.argbToAhsv(RenderDataPointUtil.getColor(data));
					color = ColorUtil.ahsvToArgb(ahsv[0], ahsv[1], ahsv[2] * saturationMultiplier, ahsv[3] * brightnessMultiplier);
				}
				break;
			}
			case SHOW_DETAIL:
			{
				color = LodUtil.DEBUG_DETAIL_LEVEL_COLORS[detailLevel];
				fullBright = true;
				break;
			}
			case SHOW_BLOCK_MATERIAL:
			{
				
				switch (EDhApiBlockMaterial.getFromIndex(blockMaterialId))
				{
					case UNKNOWN:
					case AIR: // shouldn't normally be rendered, but just in case
						color = ColorUtil.HOT_PINK;
						break;
					
					case LEAVES:
						color = ColorUtil.DARK_GREEN;
						break;
					case STONE:
						color = ColorUtil.GRAY;
						break;
					case WOOD:
						color = ColorUtil.BROWN;
						break;
					case METAL:
						color = ColorUtil.DARK_GRAY;
						break;
					case DIRT:
						color = ColorUtil.LIGHT_BROWN;
						break;
					case LAVA:
						color = ColorUtil.ORANGE;
						break;
					case DEEPSLATE:
						color = ColorUtil.BLACK;
						break;
					case SNOW:
						color = ColorUtil.WHITE;
						break;
					case SAND:
						color = ColorUtil.TAN;
						break;
					case TERRACOTTA:
						color = ColorUtil.DARK_ORANGE;
						break;
					case NETHER_STONE:
						color = ColorUtil.DARK_RED;
						break;
					case WATER:
						color = ColorUtil.BLUE;
						break;
					case GRASS:
						color = ColorUtil.GREEN;
						break;
					case ILLUMINATED:
						color = ColorUtil.YELLOW;
						break;
					
					default:
						// undefined color
						color = ColorUtil.CYAN;
						break;
				}
				
				fullBright = true;
				break;
			}
			case SHOW_OVERLAPPING_QUADS:
			{
				color = ColorUtil.WHITE;
				fullBright = true;
				break;
			}
			case SHOW_RENDER_SOURCE_FLAG:
			{
				color = debugSource == null ? ColorUtil.RED : debugSource.color;
				fullBright = true;
				break;
			}
			default:
				throw new IllegalArgumentException("Unknown debug mode: " + debugging);
		}
		
		ColumnBox.addBoxQuadsToBuilder(
				quadBuilder, clientLevel,
				width, ySize, width,
				xMin, yMin, zMin,
				color,
				blockMaterialId,
				RenderDataPointUtil.getLightSky(data),
				fullBright ? 15 : RenderDataPointUtil.getLightBlock(data),
				topData, bottomData, adjColumnViews, isSameDetailLevel);
	}
	
}
