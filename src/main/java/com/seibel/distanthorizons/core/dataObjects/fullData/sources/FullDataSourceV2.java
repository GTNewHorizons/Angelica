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

package com.seibel.distanthorizons.core.dataObjects.fullData.sources;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.objects.data.DhApiTerrainDataPoint;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.file.AbstractDataSourceHandler;
import com.seibel.distanthorizons.core.file.IDataSource;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListParent;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This data source contains every datapoint over its given {@link DhSectionPos}. <br><br>
 * 
 * @see FullDataPointUtil
 * @see FullDataSourceV1
 */
public class FullDataSourceV2 
		extends PhantomArrayListParent
		implements IDataSource<IDhLevel>, IDhApiFullDataSource
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	/** useful for debugging, but can slow down update operations quite a bit due to being called so often. */
	private static final boolean RUN_UPDATE_DEV_VALIDATION = false;
	/** 
	 * If the data column order isn't correct
	 * block lighting may appear broken 
	 * and/or certain detail level LODs may not appear at all. 
	 */
	private static final boolean RUN_DATA_ORDER_VALIDATION = ModInfo.IS_DEV_BUILD;
	
	/** measured in data columns */
	public static final int WIDTH = 64;
	/** how many chunks wide this datasource is at detail level 0. */
	public static final int NUMB_OF_CHUNKS_WIDE = WIDTH / LodUtil.CHUNK_WIDTH;
	
	public static final byte DATA_FORMAT_VERSION = 1;
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("FullDataV2");
	
	
	
	private int cachedHashCode = 0;
	
	private final long pos;
	@Override
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	
	public final FullDataPointIdMap mapping;
	
	
	public long lastModifiedUnixDateTime;
	public long createdUnixDateTime;
	
	public int levelMinY;
	
	/** 
	 * stores how far each column has been generated should start with {@link EDhApiWorldGenerationStep#EMPTY}
	 *
	 * @see EDhApiWorldGenerationStep
	 */
	public final ByteArrayList columnGenerationSteps;
	/** 
	 * stores what world compression was used for each column.
	 *
	 * @see EDhApiWorldCompressionMode
	 */
	public final ByteArrayList columnWorldCompressionMode;
	
	/** 
	 * stored x/z, y <br>
	 * The y data should be sorted from top to bottom <br>
	 * TODO that ordering feels weird, it'd be nice to reverse that order, unfortunately
	 *      there's something in the render data logic that expects this order so we can't change it right now
	 */
	public final LongArrayList[] dataPoints;
	
	public boolean isEmpty;
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToParent = null;
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToChildren = null;
	
	/** should only be used by methods exposed via the DH API */
	private boolean runApiChunkValidation = false;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static FullDataSourceV2 createFromChunk(IChunkWrapper chunkWrapper) { return LodDataBuilder.createFromChunk(chunkWrapper); }
	
	public static FullDataSourceV2 createFromLegacyDataSourceV1(FullDataSourceV1 legacyData)
	{
		if (FullDataSourceV1.WIDTH != WIDTH)
		{
			throw new UnsupportedOperationException(
					"Unable to convert ["+FullDataSourceV1.class.getSimpleName()+"] into ["+FullDataSourceV2.class.getSimpleName()+"]. " +
					"Data sources have different data point widths and no converter is present. " +
					"input width ["+ FullDataSourceV1.WIDTH+"], recipient width ["+WIDTH+"].");
		}
		
		
		// Note: this logic only works if the data point data is the same between both versions
		byte[] columnGenerationSteps = new byte[WIDTH * WIDTH];
		byte[] columnWorldCompressionMode = new byte[WIDTH * WIDTH];
		LongArrayList[] dataPoints = new LongArrayList[WIDTH * WIDTH];
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				long[] legacyDataColumn = legacyData.get(x, z);
				if (legacyDataColumn != null && legacyDataColumn.length != 0)
				{
					int index = relativePosToIndex(x, z);
					LongArrayList newDataColumn = new LongArrayList(legacyDataColumn);
					
					
					// convert the data point format
					boolean columnHasNonAirBlock = false;
					for (int i = 0; i < legacyDataColumn.length; i++)
					{
						long dataPoint = legacyDataColumn[i];
						
						boolean isAir = legacyData.mapping.getBlockStateWrapper(FullDataPointUtil.getId(dataPoint)).isAir();
						byte blockLight = (byte) FullDataPointUtil.getBlockLight(dataPoint);
						
						if (isAir)
						{
							// air shouldn't have any light, otherwise down sampling will look weird
							blockLight = 0;
						}
						
						dataPoint = FullDataPointUtil.setBlockLight(dataPoint, blockLight);
						newDataColumn.set(i, dataPoint);
						
						
						// check if this datapoint is air
						if (!columnHasNonAirBlock && !isAir)
						{
							columnHasNonAirBlock = true;
						}
					}
					
					
					// save the converted data point
					ensureDataColumnOrder(newDataColumn);
					dataPoints[index] = newDataColumn;
					
					// the old data sources didn't have a generation step written down
					// if the column has any data points, assume it's fully generated, otherwise assume it's empty
					columnGenerationSteps[index] = (columnHasNonAirBlock ? EDhApiWorldGenerationStep.LIGHT.value : EDhApiWorldGenerationStep.EMPTY.value);
					columnWorldCompressionMode[index] = EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS.value;
				}
			}
		}
		
		FullDataSourceV2 fullDataSource = FullDataSourceV2.createWithData(legacyData.getPos(), legacyData.mapping, dataPoints, columnGenerationSteps, columnWorldCompressionMode);
		return fullDataSource;
	}
	
	public static FullDataSourceV2 createEmpty(long pos)
	{
		return new FullDataSourceV2(
				pos, new FullDataPointIdMap(pos),
				// data points, genSteps, and columnCompression are all null since
				// nothing has been generated yet.
				// Using the default value of all 0's is adequate
				null,
				null, null,
				true);
	}
	
	public static FullDataSourceV2 createWithData(long pos, FullDataPointIdMap mapping, LongArrayList[] data, byte[] columnGenerationStep, byte[] columnWorldCompressionMode)
	{ return new FullDataSourceV2(pos, mapping, data, columnGenerationStep, columnWorldCompressionMode, false); }
	
	private FullDataSourceV2(
			long pos,
			FullDataPointIdMap mapping, @Nullable LongArrayList[] data,
			@Nullable byte[] columnGenerationSteps, @Nullable byte[] columnWorldCompressionMode,
			boolean empty)
	{
		super(ARRAY_LIST_POOL, 2, 0, WIDTH * WIDTH);
		
		LodUtil.assertTrue(data == null || data.length == WIDTH * WIDTH);
		
		
		
		this.pos = pos;
		this.mapping = mapping;
		this.isEmpty = empty;
		
		
		// pooled data arrays
		this.dataPoints = new LongArrayList[WIDTH * WIDTH];
		for (int i = 0; i < WIDTH * WIDTH; i++)
		{
			// size defaulting to 0 since we don't know how many datapoints
			// will be in this column yet
			this.dataPoints[i] = this.pooledArraysCheckout.getLongArray(i, 0);
		}
		
		// use incoming data if present
		if (data != null)
		{
			for (int i = 0; i < WIDTH * WIDTH; i++)
			{
				this.dataPoints[i].addAll(data[i]);
			}
		}
		
		// pooled generation step array
		this.columnGenerationSteps = this.pooledArraysCheckout.getByteArray(0, 0); // initial size is 0 so we can simply add the existing array if present
		if (columnGenerationSteps != null)
		{
			this.columnGenerationSteps.addElements(0, columnGenerationSteps);
		}
		else
		{
			ListUtil.clearAndSetSize(this.columnGenerationSteps, WIDTH * WIDTH);
		}
		
		// pooled column compression array
		this.columnWorldCompressionMode = this.pooledArraysCheckout.getByteArray(1, 0);
		if (columnWorldCompressionMode != null)
		{
			this.columnWorldCompressionMode.addElements(0, columnWorldCompressionMode);
		}
		else
		{
			ListUtil.clearAndSetSize(this.columnWorldCompressionMode, WIDTH * WIDTH);
		}
	}
	
	
	
	//======//
	// data //
	//======//
	
	public LongArrayList get(int relX, int relZ) throws IndexOutOfBoundsException { return this.dataPoints[relativePosToIndex(relX, relZ)]; }
	
	@Override
	public boolean update(@NotNull FullDataSourceV2 inputDataSource, @Nullable IDhLevel level) { return this.update(inputDataSource); }
	public boolean update(@NotNull FullDataSourceV2 inputDataSource)
	{
		// don't try updating if the input is empty
		if (inputDataSource.mapping.isEmpty())
		{
			return false;
		}
		
		
		byte thisDetailLevel = DhSectionPos.getDetailLevel(this.pos);
		byte inputDetailLevel = DhSectionPos.getDetailLevel(inputDataSource.pos);
		
		
		// determine the mapping changes necessary for the input to map onto this datasource
		int[] remappedIds = this.mapping.mergeAndReturnRemappedEntityIds(inputDataSource.mapping);
		
		boolean dataChanged;
		if (inputDetailLevel == thisDetailLevel)
		{
			dataChanged = this.updateFromSameDetailLevel(inputDataSource, remappedIds);
			
			// same detail level, propagate parent/children update flags from input
			if (this.applyToParent != null || inputDataSource.applyToParent != null)
			{
				this.applyToParent =
						// copy over application flag if either are set to continue propagating
						(BoolUtil.falseIfNull(this.applyToParent) || BoolUtil.falseIfNull(inputDataSource.applyToParent))
						// don't propagate past the top of the tree
						&& (DhSectionPos.getDetailLevel(this.pos) < AbstractDataSourceHandler.TOP_SECTION_DETAIL_LEVEL);
			}
			
			// null check to prevent setting a flag we don't want to save in the DB
			if (this.applyToChildren != null || inputDataSource.applyToChildren != null)
			{
				this.applyToChildren =
						(BoolUtil.falseIfNull(this.applyToChildren) || BoolUtil.falseIfNull(inputDataSource.applyToChildren))
						// don't propagate past the bottom of the tree
						&& (DhSectionPos.getDetailLevel(this.pos) > AbstractDataSourceHandler.MIN_SECTION_DETAIL_LEVEL);
			}
		}
		else if (inputDetailLevel + 1 == thisDetailLevel)
		{
			dataChanged = this.updateFromOneBelowDetailLevel(inputDataSource, remappedIds);
			
			// propagating up, parent will need changes
			this.applyToParent =
					dataChanged
					&& (BoolUtil.falseIfNull(this.applyToParent) || BoolUtil.falseIfNull(inputDataSource.applyToParent))
					&& (DhSectionPos.getDetailLevel(this.pos) < AbstractDataSourceHandler.TOP_SECTION_DETAIL_LEVEL);
			
		}
		else if (inputDetailLevel - 1 == thisDetailLevel)
		{
			dataChanged = this.downsampleFromOneAboveDetailLevel(inputDataSource, remappedIds);
			
			// propagating down, children will need changes
			
			this.applyToChildren =
					dataChanged
					&& (BoolUtil.falseIfNull(this.applyToChildren) || BoolUtil.falseIfNull(inputDataSource.applyToChildren))
					&& (DhSectionPos.getDetailLevel(this.pos) > AbstractDataSourceHandler.MIN_SECTION_DETAIL_LEVEL);
		}
		else
		{
			// other detail levels aren't supported since it would be more difficult to maintain
			// and would lead to edge cases that don't necessarily need to be supported 
			// (IE what do you do when the input is smaller than a single datapoint in the receiving data source?)
			// instead it's better to just percolate the updates up
			throw new UnsupportedOperationException("Unsupported data source update. Expected input detail level of ["+(thisDetailLevel-1)+"], ["+thisDetailLevel+"], or ["+(thisDetailLevel+1)+"], received detail level ["+inputDetailLevel+"].");
		}
		
		if (dataChanged)
		{
			// update the hash code
			this.generateHashCode();
		}
		
		return dataChanged;
	}
	
	public boolean updateFromSameDetailLevel(FullDataSourceV2 inputDataSource, int[] remappedIds)
	{
		// both data sources should have the same detail level
		if (DhSectionPos.getDetailLevel(inputDataSource.pos) != DhSectionPos.getDetailLevel(this.pos))
		{
			throw new IllegalArgumentException("Both data sources must have the same detail level. Expected ["+ DhSectionPos.getDetailLevel(this.pos)+"], received ["+ DhSectionPos.getDetailLevel(inputDataSource.pos)+"].");
		}
		
		// copy over everything from the input data source into this one
		// provided there is data to copy and the world generation step is the same or more complete
		boolean dataChanged = false;
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				int index = relativePosToIndex(x, z);
				
				LongArrayList inputDataArray = inputDataSource.dataPoints[index];
				if (inputDataArray != null)
				{
					byte thisGenState = this.columnGenerationSteps.getByte(index);
					byte inputGenState = inputDataSource.columnGenerationSteps.getByte(index);
				
					
					// determine if this column should be updated
					boolean genStateAllowsUpdating = false;
					// if the input is downsampled, we only want to replace empty or downsampled values
					if (inputGenState == EDhApiWorldGenerationStep.DOWN_SAMPLED.value
						&&
						(
							thisGenState == EDhApiWorldGenerationStep.EMPTY.value
							|| thisGenState == EDhApiWorldGenerationStep.DOWN_SAMPLED.value
						))
					{
						genStateAllowsUpdating = true;
					}
					// if the input is any other non-empty value,
					// replace anything that is less-complete
					else if (inputGenState != EDhApiWorldGenerationStep.EMPTY.value
							&& thisGenState <= inputGenState)
					{
						// don't apply less-complete generation data
						genStateAllowsUpdating = true;
					}
					
					
					if (genStateAllowsUpdating)
					{
						// check if the data changed
						if (this.dataPoints[index] == null)
						{
							// no data was present previously
							this.dataPoints[index] = new LongArrayList(inputDataArray);
							dataChanged = true;
						}
						else if (this.dataPoints[index].size() != inputDataArray.size())
						{
							// data is present, but the size is different
							dataChanged = true;
						}
						
						int oldDataHash = 0;
						if (!dataChanged)
						{
							// some old data existed with the same length,
							// we'll have to compare the caches
							oldDataHash = this.dataPoints[index].hashCode();
						}
						
						
						// copy over the new data
						this.dataPoints[index].clear();
						this.dataPoints[index].addAll(inputDataArray);
						this.remapDataColumn(index, remappedIds);
						
						if (RUN_DATA_ORDER_VALIDATION)
						{
							throwIfDataColumnInWrongOrder(inputDataSource.pos, this.dataPoints[index]);
						}
						
						
						
						if (!dataChanged)
						{
							// check if the identical length data column hashes are the same
							// hashes need to be compared after the ID's have been remapped otherwise the ID's won't match even if the data is the same
							if (oldDataHash != this.dataPoints[index].hashCode())
							{
								// the hashes are different, something was changed
								dataChanged = true;
							}
						}
						
						
						this.columnGenerationSteps.set(index, inputGenState);
						// always overwrite the compression mode since we're replacing this column
						this.columnWorldCompressionMode.set(index, inputDataSource.columnWorldCompressionMode.getByte(index));
						this.isEmpty = false;
					}
				}
			}
		}
		
		return dataChanged;
	}
	public boolean updateFromOneBelowDetailLevel(FullDataSourceV2 inputDataSource, int[] remappedIds)
	{
		if (DhSectionPos.getDetailLevel(inputDataSource.pos) + 1 != DhSectionPos.getDetailLevel(this.pos))
		{
			throw new IllegalArgumentException("Input data source must be exactly 1 detail level below this data source. Expected [" + (DhSectionPos.getDetailLevel(this.pos) - 1) + "], received [" + DhSectionPos.getDetailLevel(inputDataSource.pos) + "].");
		}
		
		// input is one detail level lower (higher detail)
		// so 2x2 input data points will be converted into 1 recipient data point
		
		
		// determine where in the input data source should be written to
		// since the input is one detail level below it will be one of this position's 4 children
		int minChildXPos = DhSectionPos.getX(DhSectionPos.getChildByIndex(this.pos, 0));
		int recipientOffsetX = (DhSectionPos.getX(inputDataSource.pos) == minChildXPos) ? 0 : (WIDTH / 2);
		int minChildZPos = DhSectionPos.getZ(DhSectionPos.getChildByIndex(this.pos, 0));
		int recipientOffsetZ = (DhSectionPos.getZ(inputDataSource.pos) == minChildZPos) ? 0 : (WIDTH / 2);
		
		
		
		// merge the input's data points
		// into this data source's
		boolean dataChanged = false;
		for (int x = 0; x < WIDTH; x += 2)
		{
			for (int z = 0; z < WIDTH; z += 2)
			{
				int recipientX = (x / 2) + recipientOffsetX;
				int recipientZ = (z / 2) + recipientOffsetZ;
				int recipientIndex = relativePosToIndex(recipientX, recipientZ);
				
				
				// world gen //
				byte inputGenStep = determineMinWorldGenStepForTwoByTwoColumn(inputDataSource.columnGenerationSteps, x, z);
				this.columnGenerationSteps.set(recipientIndex, inputGenStep);
				
				
				// world compression //
				byte worldCompressionMode = determineHighestWorldCompressionForTwoByTwoColumn(inputDataSource.columnWorldCompressionMode, x, z);
				this.columnWorldCompressionMode.set(recipientIndex, worldCompressionMode);
				
				
				
				// data points //
				LongArrayList mergedInputDataArray = mergeInputTwoByTwoDataColumn(inputDataSource, x, z);
				
				// check if the data changed
				if (this.dataPoints[recipientIndex] == null)
				{
					// no data was present previously
					dataChanged = true;
				}
				else if (this.dataPoints[recipientIndex].size() != mergedInputDataArray.size())
				{
					// data is present, but the size is different
					dataChanged = true;
				}
				
				int oldDataHash = 0;
				if (!dataChanged)
				{
					// some old data existed with the same length,
					// we'll have to compare the caches
					oldDataHash = this.dataPoints[recipientIndex].hashCode();
				}
				
				
				this.dataPoints[recipientIndex] = mergedInputDataArray;
				this.remapDataColumn(recipientIndex, remappedIds);
				
				if (RUN_DATA_ORDER_VALIDATION)
				{
					throwIfDataColumnInWrongOrder(inputDataSource.pos, this.dataPoints[recipientIndex]);
				}
				
				
				
				if (!dataChanged)
				{
					// check if the identical length data column hashes are the same
					// hashes need to be compared after the ID's have been remapped otherwise the ID's won't match even if the data is the same
					if (oldDataHash != this.dataPoints[recipientIndex].hashCode())
					{
						// the hashes are different, something was changed
						dataChanged = true;
					}
				}
				
				this.isEmpty = false;
			}
		}
		
		return dataChanged;
	}
	/** 
	 * The minimum value is used because we don't want to accidentally record that
	 * something was generated when it wasn't.
	 */
	private static byte determineMinWorldGenStepForTwoByTwoColumn(ByteArrayList columnGenerationSteps, int relX, int relZ)
	{
		// TODO merge similar logic with determineHighestWorldCompressionForTwoByTwoColumn
		byte minWorldGenStepValue = Byte.MAX_VALUE;
		for (int x = 0; x < 2; x++)
		{
			for (int z = 0; z < 2; z++)
			{
				int index = relativePosToIndex(x + relX, z + relZ);
				byte worldGenStepValue = columnGenerationSteps.getByte(index);
				minWorldGenStepValue = (byte) Math.min(minWorldGenStepValue, worldGenStepValue);
			}
		}
		return minWorldGenStepValue;
	}
	/** 
	 * The minimum value is used because we don't want to accidentally record that
	 * something was generated when it wasn't.
	 */
	private static byte determineHighestWorldCompressionForTwoByTwoColumn(ByteArrayList columnCompressionMode, int relX, int relZ)
	{
		// TODO merge similar logic with determineMinWorldGenStepForTwoByTwoColumn
		byte minWorldGenStepValue = Byte.MIN_VALUE;
		for (int x = 0; x < 2; x++)
		{
			for (int z = 0; z < 2; z++)
			{
				int index = relativePosToIndex(x + relX, z + relZ);
				byte worldGenStepValue = columnCompressionMode.getByte(index);
				minWorldGenStepValue = (byte) Math.max(minWorldGenStepValue, worldGenStepValue);
			}
		}
		return minWorldGenStepValue;
	}
	private static LongArrayList mergeInputTwoByTwoDataColumn(FullDataSourceV2 inputDataSource, int x, int z)
	{
		LongArrayList newColumnList = new LongArrayList();
		
		// special numbers:
		// -2 = the column's height hasn't been determined yet
		// -1 = we've reached the end of the column
		int[] currentDatapointIndex = new int[] { -2, -2, -2, -2 };
		
		int lastId = 0;
		byte lastBlockLight = 0;
		byte lastSkyLight = 0;
		int height = 0;
		int minY = 0;
		
		
		// these arrays will be reused quite often, so re-using them helps reduce some GC pressure
		long[] datapointsForYSlice = new long[4];
		int[] mergeIds = new int[4];
		int[] mergeBlockLights = new int[4];
		int[] mergeSkyLights = new int[4];
		
		
		for (int blockY = 0; blockY < RenderDataPointUtil.MAX_WORLD_Y_SIZE; blockY++, height++)
		{
			// if each column has reached the end of their data, nothing more needs to be done
			if (currentDatapointIndex[0] == -1
				&& currentDatapointIndex[1] == -1
				&& currentDatapointIndex[2] == -1
				&& currentDatapointIndex[3] == -1
				)
			{
				break;
			}
			
			
			// scary double loop but, 
			// this will only ever loop 4 times, 
			// once for each of the 4 input columns
			Arrays.fill(datapointsForYSlice, 0L);
			int colIndex = 0;
			for (int inputX = x; inputX < x + 2; inputX++)
			{
				for (int inputZ = z; inputZ < z + 2; inputZ++, colIndex++)
				{
					// TODO throw an assertion if the column isn't in top-down order or just fix it...
					LongArrayList inputDataArray = inputDataSource.dataPoints[relativePosToIndex(inputX, inputZ)];
					if (inputDataArray == null || inputDataArray.size() == 0)
					{
						currentDatapointIndex[colIndex] = -1;
						continue;
					}
					
					// determine the last index (the lowest data point) for each column
					if (currentDatapointIndex[colIndex] == -2)
					{
						currentDatapointIndex[colIndex] = inputDataArray.size() - 1;
						
						if (RUN_DATA_ORDER_VALIDATION)
						{
							throwIfDataColumnInWrongOrder(inputDataSource.pos, inputDataArray);
						}
					}
					
					
					int dataPointIndex = currentDatapointIndex[colIndex];
					if (dataPointIndex == -1)
					{
						// went over the end 
						continue;
					}
					long datapoint = inputDataArray.getLong(dataPointIndex);
					
					int datapointMinY = FullDataPointUtil.getBottomY(datapoint);
					int numbOfBlocksTall = FullDataPointUtil.getHeight(datapoint);
					int datapointMaxY = (datapointMinY + numbOfBlocksTall);
					
					
					// check if y position is inside this datapoint
					if (blockY < datapointMinY)
					{
						// this y-slice is below this datapoint, nothing can be added
						continue;
					}
					else if (blockY >= datapointMaxY)
					{
						// this y-slice is above the current datapoint,
						// try the next data point
						
						int newDatapointIndex = currentDatapointIndex[colIndex] - 1;
						if (newDatapointIndex < 0)
						{
							// went to far, no additional data present
							newDatapointIndex = -1;
						}
						currentDatapointIndex[colIndex] = newDatapointIndex;
						
						
						// try again with the next data point
						inputZ--;
						colIndex--;
						continue;
					}
					
					
					
					datapointsForYSlice[colIndex] = datapoint;
				}
			}
			
			
			
			Arrays.fill(mergeIds, 0);
			Arrays.fill(mergeBlockLights, 0);
			Arrays.fill(mergeSkyLights, 0);
			for (int i = 0; i < 4; i++)
			{
				mergeIds[i] = FullDataPointUtil.getId(datapointsForYSlice[i]);
				mergeBlockLights[i] = FullDataPointUtil.getBlockLight(datapointsForYSlice[i]);
				mergeSkyLights[i] = FullDataPointUtil.getSkyLight(datapointsForYSlice[i]);
			}
			
			
			// determine the most common values for this slice
			int id = determineMostValueInColumnSlice(mergeIds, inputDataSource.mapping);
			byte blockLight = (byte) determineAverageValueInColumnSlice(mergeBlockLights);
			byte skyLight = (byte) determineAverageValueInColumnSlice(mergeSkyLights);
			
			// if this slice is different then the last one, create a new one
			if (id != lastId
				// block and sky light might not be necessary
				|| blockLight != lastBlockLight
				|| skyLight != lastSkyLight)
			{
				if (height != 0)
				{
					try
					{
						long datapoint = FullDataPointUtil.encode(lastId, height, minY, lastBlockLight, lastSkyLight);
						newColumnList.add(datapoint);
					}
					catch (DataCorruptedException e)
					{
						// shouldn't happen, (especially if validation is disabled) but just in case
						LOGGER.warn("Skipping corrupt datapoint for pos "+inputDataSource.pos+" at relative position ["+x+","+z+"] with data: ID["+lastId+"], Height["+height+"], minY["+minY+"], lastBlockLight["+lastBlockLight+"], lastSkyLight["+lastSkyLight+"].");
					}
				}
				
				lastId = id;
				lastBlockLight = blockLight;
				lastSkyLight = skyLight;
				height = 0;
				minY = blockY;
			}
		}
		
		// add the last slice if present
		if (height != 0)
		{
			try
			{
				newColumnList.add(FullDataPointUtil.encode(lastId, height, minY, lastBlockLight, lastSkyLight));
			}
			catch (DataCorruptedException e)
			{
				// shouldn't happen, (especially if validation is disabled) but just in case
				LOGGER.warn("Skipping corrupt datapoint for pos "+inputDataSource.pos+" at relative position ["+x+","+z+"] with data: ID["+lastId+"], Height["+height+"], minY["+minY+"], lastBlockLight["+lastBlockLight+"], lastSkyLight["+lastSkyLight+"].");
			}
		}
		
		
		// flip the array if necessary
		// TODO why is this sometimes necessary? What did I (James) screw up that causes the mergedInputDataArray
		//  to sometimes be in a different order? Is it potentially related to what detail level is coming in?
		ensureDataColumnOrder(newColumnList);
		
		return newColumnList;
	}
	/**
	 * Only update the ID once it's been added to this data source.
	 * Updating the incoming data source will cause issues if it is applied 
	 * to anything else due to multiple remapping.
	 */
	private void remapDataColumn(int dataPointIndex, int[] remappedIds)
	{
		LongArrayList dataColumn = this.dataPoints[dataPointIndex];
		for (int i = 0; i < dataColumn.size(); i++)
		{
			dataColumn.set(i, FullDataPointUtil.remap(remappedIds, dataColumn.getLong(i)));
		}
	}
	private static boolean areDataColumnsDifferent(long[] oldDataArray, long[] newDataArray)
	{
		if (oldDataArray == null || oldDataArray.length != newDataArray.length)
		{
			// new data was added/removed
			return true;
		}
		else
		{
			// check if the new column data is different
			int oldArrayHash = Arrays.hashCode(oldDataArray);
			int newArrayHash = Arrays.hashCode(newDataArray);
			return (newArrayHash != oldArrayHash);
		}
	}
	/** @param mapping can be included to ignore air ID's, otherwise all 4 values are treated equally */
	private static int determineMostValueInColumnSlice(int[] sliceArray, @Nullable FullDataPointIdMap mapping)
	{
		if (RUN_UPDATE_DEV_VALIDATION)
		{
			LodUtil.assertTrue(sliceArray.length == 4, "Column Slice should only contain 4 values.");
		}
		
		int value0 = sliceArray[0];
		int count0 = 0;
		int value1 = sliceArray[1];
		int count1 = 0;
		int value2 = sliceArray[2];
		int count2 = 0;
		int value3 = sliceArray[3];
		int count3 = 0; 
		
		// count the occurrences of each value
		for (int i = 0; i < 4; i++)
		{
			int value = sliceArray[i];
			if (mapping != null && mapping.getBlockStateWrapper(value).isAir())
			{
				// always overwrite air to prevent holes in hollow structures
				continue;
			}
			
			if (value == value0)
			{
				count0++;
			}
			else if (value == value1)
			{
				count1++;
			}
			else if (value == value2)
			{
				count2++;
			}
			else
			{
				count3++;
			}
		}
		
		// return the most common occurance
		int maxCount = Math.max(count0, Math.max(count1, Math.max(count2, count3)));
		if (maxCount == count0)
		// if the max count is 1 then we'll just go with the first column
		{
			return value0;
		}
		else if (maxCount == count1)
		{
			return value1;
		}
		else if (maxCount == count2)
		{
			return value2;
		}
		else
		{
			return value3;
		}
	}
	private static int determineAverageValueInColumnSlice(int[] sliceArray)
	{
		if (RUN_UPDATE_DEV_VALIDATION)
		{
			LodUtil.assertTrue(sliceArray.length == 4, "Column Slice should only contain 4 values.");
		}
		
		
		int value = 0;
		for (int i = 0; i < 4; i++)
		{
			value += sliceArray[i];
		}
		
		value /= 4;
		return value;
	}
	
	/** 
	 * Only downsamples into a given column if this data source doesn't
	 * already contain data in that column.
	 * This is done to prevent accidentally downsampling onto already present higher-detail data.
	 */
	public boolean downsampleFromOneAboveDetailLevel(FullDataSourceV2 inputDataSource, int[] remappedIds)
	{
		if (DhSectionPos.getDetailLevel(inputDataSource.pos) - 1 != DhSectionPos.getDetailLevel(this.pos))
		{
			throw new IllegalArgumentException("Input data source must be exactly 1 detail level above this data source. Expected [" + (DhSectionPos.getDetailLevel(this.pos) - 1) + "], received [" + DhSectionPos.getDetailLevel(inputDataSource.pos) + "].");
		}
		
		// input is one detail level higher (lower detail)
		// so 1x1 input data points will be converted into 2x2 recipient data point
		
		
		// determine where in this data source should be read from
		// since the input is one detail level above this will be one of input position's 4 children
		int minParentXPos = DhSectionPos.getX(DhSectionPos.getChildByIndex(inputDataSource.pos, 0));
		int inputOffsetX = (DhSectionPos.getX(this.pos) == minParentXPos) ? 0 : (WIDTH / 2);
		int minParentZPos = DhSectionPos.getZ(DhSectionPos.getChildByIndex(inputDataSource.pos, 0));
		int inputOffsetZ = (DhSectionPos.getZ(this.pos) == minParentZPos) ? 0 : (WIDTH / 2);
		
		
		
		// merge the input's data points
		// into this data source's
		boolean dataChanged = false;
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				// recipient index is 1-to-1
				int recipientIndex = relativePosToIndex(x, z);
				
				int inputX = (x / 2) + inputOffsetX;
				int inputZ = (z / 2) + inputOffsetZ;
				int inputIndex = relativePosToIndex(inputX, inputZ);
				
				
				// world gen //
				
				// a separate generation step needs to be used so can replace
				// this data with higher-quality data when it is available
				byte inputGenStep = EDhApiWorldGenerationStep.DOWN_SAMPLED.value;
				this.columnGenerationSteps.set(recipientIndex, inputGenStep);
				
				
				// world compression //
				byte worldCompressionMode = inputDataSource.columnWorldCompressionMode.getByte(recipientIndex);
				this.columnWorldCompressionMode.set(recipientIndex, worldCompressionMode);
				
				
				
				// data points //
				
				// check if this column should be downsampled
				boolean downSampleColumn;
				if (this.dataPoints[recipientIndex] == null)
				{
					downSampleColumn = true;
				}
				else
				{
					downSampleColumn = true; // assume empty until we find non-empty data
					for (long dataPoint : this.dataPoints[recipientIndex])
					{
						if (dataPoint != FullDataPointUtil.EMPTY_DATA_POINT)
						{
							downSampleColumn = false;
							break;
						}
					}
				}
				
				if (downSampleColumn)
				{
					LongArrayList inputDataArray = inputDataSource.dataPoints[inputIndex];
					this.dataPoints[recipientIndex] = inputDataArray;
					this.remapDataColumn(recipientIndex, remappedIds);
					
					if (RUN_DATA_ORDER_VALIDATION)
					{
						throwIfDataColumnInWrongOrder(inputDataSource.pos, this.dataPoints[recipientIndex]);
					}
					
					dataChanged = true;
				}
				
				this.isEmpty = false;
			}
		}
		
		return dataChanged;
	}
	
	
	//================//
	// helper methods //
	//================//
	
	/** 
	 * Usually this should just be used internally, but there may be instances
	 * where the raw data arrays are available without the data source object.
	 */
	public static int relativePosToIndex(int relX, int relZ) throws IndexOutOfBoundsException
	{ 
		if (relX < 0 || relZ < 0 ||
			relX > WIDTH || relZ > WIDTH)
		{
			throw new IndexOutOfBoundsException("Relative data source positions must be between [0] and ["+WIDTH+"] (inclusive) the relative pos: ["+relX+","+relZ+"] is outside of those boundaries.");
		}
		
		return (relX * WIDTH) + relZ; 
	}
	
	/** 
	 * Throws an exception if the given
	 * full data column array is in the wrong order
	 * IE if the first data point is the lowest and the last data point is the highest.
	 * Data columns should be in reverse order, IE the first data point should be the highest data point.
	 * 
	 * @see FullDataSourceV2#dataPoints
	 */
	public static void throwIfDataColumnInWrongOrder(long pos, LongArrayList dataArray) throws IllegalStateException
	{
		if (dataArray.size() < 2)
		{
			return;
		}
		
		long firstDataPoint = dataArray.getLong(0);
		int firstBottomY = FullDataPointUtil.getBottomY(firstDataPoint);
		
		long lastDataPoint = dataArray.getLong(dataArray.size() - 1);
		int lastBottomY = FullDataPointUtil.getBottomY(lastDataPoint);
		
		if (firstBottomY < lastBottomY)
		{
			throw new IllegalStateException("Incorrect data point order at pos: ["+ DhSectionPos.toString(pos)+"], first datapoint bottom Y ["+firstBottomY+"], last datapoint bottom Y ["+lastBottomY+"].");
		}
	}
	
	/**
	 * Ensures the given data column is in the correct Y order, specifically
	 * top-to-bottom.
	 */
	private static void ensureDataColumnOrder(LongArrayList dataColumn)
	{
		if (dataColumn.size() < 2)
		{
			return;
		}
		
		long firstDataPoint = dataColumn.getLong(0);
		int firstBottomY = FullDataPointUtil.getBottomY(firstDataPoint);
		
		long lastDataPoint = dataColumn.getLong(dataColumn.size() - 1);
		int lastBottomY = FullDataPointUtil.getBottomY(lastDataPoint);
		
		if (firstBottomY < lastBottomY)
		{
			// reverse the array so index 0 is the highest,
			// this is necessary for later logic
			// source: https://stackoverflow.com/questions/2137755/how-do-i-reverse-an-int-array-in-java
			for(int i = 0; i < dataColumn.size() / 2; i++)
			{
				long temp = dataColumn.getLong(i);
				dataColumn.set(i, dataColumn.getLong(dataColumn.size() - i - 1));
				dataColumn.set(dataColumn.size() - i - 1, temp);
			}
		}
	}
	
	
	
	//=====================//
	// setters and getters //
	//=====================//
	
	@Override
	public Long getPos() { return this.pos; }
	
	@Override
	public byte getDataDetailLevel() { return (byte) (DhSectionPos.getDetailLevel(this.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL); }
	
	public EDhApiWorldGenerationStep getWorldGenStepAtRelativePos(int relX, int relZ) 
	{
		int index = relativePosToIndex(relX, relZ);
		return EDhApiWorldGenerationStep.fromValue(this.columnGenerationSteps.getByte(index)); 
	}
	
	public void setSingleColumn(LongArrayList longArray, int relX, int relZ, EDhApiWorldGenerationStep worldGenStep, EDhApiWorldCompressionMode worldCompressionMode)
	{
		int index = relativePosToIndex(relX, relZ);
		this.dataPoints[index] = longArray;
		this.columnGenerationSteps.set(index, worldGenStep.value);
		this.columnWorldCompressionMode.set(index, worldCompressionMode.value);
		
		
		if (RUN_UPDATE_DEV_VALIDATION)
		{
			// validate the incoming ID's
			int maxValidId = this.mapping.getMaxValidId();
			for (int i = 0; i < longArray.size(); i++)
			{
				long dataPoint = longArray.getLong(i);
				int id = FullDataPointUtil.getId(dataPoint);
				if (id > maxValidId)
				{
					LodUtil.assertNotReach("Column set with higher than possible ID. ID [" + id + "], max valid ID [" + maxValidId + "].");
				}
			}
		}
	}
	
	
	
	//=============//
	// API methods //
	//=============//
	
	public void setRunApiChunkValidation(boolean runValidation) { this.runApiChunkValidation = runValidation; }
	
	@Override
	public int getWidthInDataColumns() { return WIDTH; }
	
	@Override
	public List<DhApiTerrainDataPoint> setApiDataPointColumn(int relX, int relZ, List<DhApiTerrainDataPoint> columnDataPoints) 
				throws IndexOutOfBoundsException, IllegalArgumentException
	{
		try
		{
			LodDataBuilder.correctDataColumnOrder(columnDataPoints);
			if (this.runApiChunkValidation)
			{
				LodDataBuilder.validateOrThrowApiDataColumn(columnDataPoints);
			}
			
			LongArrayList packedDataPoints = LodDataBuilder.convertApiDataPointListToPackedLongArray(columnDataPoints, this, 0);
			
			// TODO there should be an "unknown" compression and generation step, or be defined via the datapoints
			this.setSingleColumn(packedDataPoints, relX, relZ, EDhApiWorldGenerationStep.SURFACE, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
			
			return columnDataPoints;
		}
		catch (DataCorruptedException e)
		{
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}
	
	@Override 
	public List<DhApiTerrainDataPoint> getApiDataPointColumn(int relX, int relZ) throws IndexOutOfBoundsException
	{
		LongArrayList dataColumn = this.get(relX, relZ);
		
		ArrayList<DhApiTerrainDataPoint> apiList = new ArrayList<>();
		for (int i = 0; i < dataColumn.size(); i++)
		{
			long datapoint = dataColumn.getLong(i);
			
			DhApiTerrainDataPoint apiDataPoint = DhApiTerrainDataPointUtil.createApiDatapoint(this.levelMinY, this.mapping, DhSectionPos.getDetailLevel(this.pos), datapoint);
			apiList.add(apiDataPoint);
		}
		
		return apiList;
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return DhSectionPos.toString(this.pos); }
	
	/** Only includes the base data in this object, not the mapping */
	@Override 
	public int hashCode()
	{
		if (this.cachedHashCode == 0)
		{
			this.generateHashCode();
		}
		return this.cachedHashCode;
	}
	private void generateHashCode()
	{
		int result = DhSectionPos.hashCode(this.pos);
		result = 31 * result + Arrays.deepHashCode(this.dataPoints);
		result = 17 * result + this.columnGenerationSteps.hashCode();
		result = 43 * result + this.columnWorldCompressionMode.hashCode();
		
		this.cachedHashCode = result;
	}
	
	@Override 
	public boolean equals(Object obj)
	{
		if (!(obj instanceof FullDataSourceV2))
		{
			return false;
		}
		FullDataSourceV2 other = (FullDataSourceV2) obj;
		
		if (other.pos != this.pos)
		{
			return false;
		}
		else
		{
			// the positions are the same, use the hash as a quick method
			// to determine if the data inside is the same.
			// Note: this isn't perfect, but should work well enough for our use case.
			return other.hashCode() == this.hashCode();
		}
	}
	
	
	
}
