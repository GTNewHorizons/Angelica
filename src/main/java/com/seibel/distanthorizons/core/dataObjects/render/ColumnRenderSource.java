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

package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.file.IDataSource;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListParent;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.ListUtil;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnQuadView;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores the render data used to generate OpenGL buffers.
 *
 * @see RenderDataPointUtil
 */
public class ColumnRenderSource
		extends PhantomArrayListParent
		implements IDataSource<IDhClientLevel>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final boolean DO_SAFETY_CHECKS = ModInfo.IS_DEV_BUILD;
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	/** width of this data in columns */
	public static final int SECTION_SIZE = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET); // 64
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Render Source");
	
	
	
	/** will be zero if an empty data source was created */
	public int verticalDataCount;
	public long pos;
	public int yOffset;
	
	public final LongArrayList renderDataContainer;
	
	public final DebugSourceFlag[] debugSourceFlags;
	
	private boolean isEmpty = true;
	
	public AtomicLong localVersion = new AtomicLong(0); // used to track changes to the data source, so that buffers can be updated when necessary
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static ColumnRenderSource createEmpty(long pos, int maxVerticalSize, int yOffset)
	{ return new ColumnRenderSource(pos, maxVerticalSize, yOffset); }
	/**
	 * Creates an empty ColumnRenderSource.
	 *
	 * @param pos the relative position of the container
	 * @param maxVerticalSize the maximum vertical size of the container
	 */
	private ColumnRenderSource(long pos, int maxVerticalSize, int yOffset)
	{
		super(ARRAY_LIST_POOL, 0, 0, 1);
		
		this.pos = pos;
		this.yOffset = yOffset;
		
		this.verticalDataCount = maxVerticalSize;
		
		this.renderDataContainer = this.pooledArraysCheckout.getLongArray(0, SECTION_SIZE * SECTION_SIZE * this.verticalDataCount);
		
		this.debugSourceFlags = new DebugSourceFlag[SECTION_SIZE * SECTION_SIZE];
	}
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public long getDataPoint(int posX, int posZ, int verticalIndex) { return this.renderDataContainer.getLong(posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex); }
	
	public ColumnArrayView getVerticalDataPointView(int posX, int posZ)
	{
		int offset = posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount;
		
		// don't allow returning views that are outside this render source's bounds
		if (offset >= this.renderDataContainer.size())
		{
			return null;
		}
		else if (posX < 0 || posX >= SECTION_SIZE
				|| posZ < 0 || posZ >= SECTION_SIZE)
		{
			return null;
		}
		
		return new ColumnArrayView(this.renderDataContainer, this.verticalDataCount,
				offset, this.verticalDataCount);
	}
	
	public ColumnQuadView getFullQuadView() { return this.getQuadViewOverRange(0, 0, SECTION_SIZE, SECTION_SIZE); }
	public ColumnQuadView getQuadViewOverRange(int quadX, int quadZ, int quadXSize, int quadZSize) { return new ColumnQuadView(this.renderDataContainer, SECTION_SIZE, this.verticalDataCount, quadX, quadZ, quadXSize, quadZSize); }
	
	
	
	//=============//
	// data update //
	//=============//
	
	@Override
	public boolean update(FullDataSourceV2 inputFullDataSource, IDhClientLevel level)
	{
		final String errorMessagePrefix = "Unable to complete update for RenderSource pos: [" + this.pos + "] and pos: [" + inputFullDataSource.getPos() + "]. Error:";
		
		boolean dataChanged = false;
		if (DhSectionPos.getDetailLevel(inputFullDataSource.getPos()) == DhSectionPos.getDetailLevel(this.pos))
		{
			try
			{
				if (Thread.interrupted())
				{
					LOGGER.warn(errorMessagePrefix + "write interrupted.");
					return false;
				}
				
				
				
				DhBlockPos2D centerBlockPos = DhSectionPos.getCenterBlockPos(inputFullDataSource.getPos());
				int halfBlockWidth = DhSectionPos.getBlockWidth(inputFullDataSource.getPos()) / 2;
				DhBlockPos2D minBlockPos = new DhBlockPos2D(centerBlockPos.x - halfBlockWidth, centerBlockPos.z - halfBlockWidth);
				
				for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
				{
					for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
					{
						ColumnArrayView columnArrayView = this.getVerticalDataPointView(x, z);
						int columnHash = columnArrayView.getDataHash();
						
						LongArrayList dataColumn = inputFullDataSource.get(x, z);
						EDhApiWorldGenerationStep worldGenStep = inputFullDataSource.getWorldGenStepAtRelativePos(x, z);
						if (dataColumn != null && worldGenStep != EDhApiWorldGenerationStep.EMPTY)
						{
							FullDataToRenderDataTransformer.updateOrReplaceRenderDataViewColumnWithFullDataColumn(
									level, inputFullDataSource.mapping,
									minBlockPos.x + x,
									minBlockPos.z + z,
									columnArrayView, dataColumn);
							dataChanged |= columnHash != columnArrayView.getDataHash();
							
							this.fillDebugFlag(x, z, 1, 1, ColumnRenderSource.DebugSourceFlag.DIRECT);
						}
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.error(errorMessagePrefix + e.getMessage(), e);
			}
		}
		
		if (dataChanged)
		{
			this.localVersion.incrementAndGet();
			this.markNotEmpty();
		}
		
		return dataChanged;
	}
	
	
	
	//=====================//
	// data helper methods //
	//=====================//
	
	public Long getPos() { return this.pos; }
	@Override
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	public byte getDataDetailLevel() { return (byte) (DhSectionPos.getDetailLevel(this.pos) - SECTION_SIZE_OFFSET); }
	
	public boolean isEmpty() { return this.isEmpty; }
	public void markNotEmpty() { this.isEmpty = false; }
	
	/** can be used when debugging */
	public boolean hasNonVoidDataPoints()
	{
		if (this.isEmpty)
		{
			return false;
		}
		
		
		for (int x = 0; x < SECTION_SIZE; x++)
		{
			for (int z = 0; z < SECTION_SIZE; z++)
			{
				ColumnArrayView columnArrayView = this.getVerticalDataPointView(x,z);
				for (int i = 0; i < columnArrayView.size; i++)
				{
					long dataPoint = columnArrayView.get(i);
					if (!RenderDataPointUtil.isVoid(dataPoint))
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	/** Sets the debug flag for the given area */
	public void fillDebugFlag(int xStart, int zStart, int xWidth, int zWidth, DebugSourceFlag flag)
	{
		for (int x = xStart; x < xStart + xWidth; x++)
		{
			for (int z = zStart; z < zStart + zWidth; z++)
			{
				this.debugSourceFlags[x * SECTION_SIZE + z] = flag;
			}
		}
	}
	
	public DebugSourceFlag debugGetFlag(int ox, int oz) { return this.debugSourceFlags[ox * SECTION_SIZE + oz]; }
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString()
	{
		String LINE_DELIMITER = "\n";
		String DATA_DELIMITER = " ";
		String SUBDATA_DELIMITER = ",";
		StringBuilder stringBuilder = new StringBuilder();
		
		stringBuilder.append(DhSectionPos.toString(this.pos));
		stringBuilder.append(LINE_DELIMITER);
		
		int size = 1;
		for (int z = 0; z < size; z++)
		{
			for (int x = 0; x < size; x++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					//Converting the dataToHex
					stringBuilder.append(Long.toHexString(this.getDataPoint(x, z, y)));
					if (y != this.verticalDataCount - 1)
						stringBuilder.append(SUBDATA_DELIMITER);
				}
				
				if (x != size - 1)
					stringBuilder.append(DATA_DELIMITER);
			}
			
			if (z != size - 1)
				stringBuilder.append(LINE_DELIMITER);
		}
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// helper enums //
	//==============//
	
	public enum DebugSourceFlag
	{
		FULL(ColorUtil.BLUE),
		DIRECT(ColorUtil.WHITE),
		FILE(ColorUtil.BROWN);
		
		public final int color;
		
		DebugSourceFlag(int color) { this.color = color; }
	}
	
}
