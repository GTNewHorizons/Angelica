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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.file.IDataSource;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV1DTO;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;

/**
 * Formerly "CompleteFullDataSource". <br>
 * Should be fully populated, containing 1 data point for each column. <br><br>
 * 
 * Replaced by {@link FullDataSourceV2}.
 * 
 * @see FullDataPointUtil
 * @see FullDataSourceV2
 */
public class FullDataSourceV1 implements IDataSource<IDhLevel>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	/** measured in dataPoints */
	public static final int WIDTH = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	
	public static final byte DATA_FORMAT_VERSION = 3;
	/** never used but should stay here. */
	public static final String DATA_TYPE_NAME = "CompleteFullDataSource";
	
	/**
	 * This is the byte put between different sections in the binary save file.
	 * The presence and absence of this byte indicates if the file is correctly formatted.
	 */
	private static final int DATA_GUARD_BYTE = 0xFFFFFFFF;
	/** indicates the binary save file represents an empty data source */
	private static final int NO_DATA_FLAG_BYTE = 0x00000001;
	
	
	public final FullDataPointIdMap mapping;
	public EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	
	
	/** A flattened 2D array (for the X and Z directions) containing an array for the Y direction. */
	private final long[][] dataArrays;
	
	private long pos;
	
	private boolean isEmpty = true;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static FullDataSourceV1 createEmpty(long pos) { return new FullDataSourceV1(pos); }
	private FullDataSourceV1(long pos)
	{
		this.dataArrays = new long[WIDTH * WIDTH][0];
		this.mapping = new FullDataPointIdMap(pos);
		this.pos = pos;
	}
	
	
	
	
	//======//
	// data //
	//======//
	
	@Deprecated
	@Override
	public boolean update(FullDataSourceV2 dataSource, IDhLevel level) { throw new UnsupportedOperationException("Deprecated"); }
	
	
	
	//=====================//
	// setters and getters //
	//=====================//
	
	@Override
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	@Override
	public Long getPos() { return this.pos; }
	
	public void resizeDataStructuresForRepopulation(long pos)
	{
		// no data structures need to be changed, only the source's position 
		this.pos = pos;
	}
	
	@Override
	public byte getDataDetailLevel() { return (byte) (DhSectionPos.getDetailLevel(this.pos) - SECTION_SIZE_OFFSET); }
	
	public boolean isEmpty() { return this.isEmpty; }
	
	
	public long[] get(int index) { return this.get(index / WIDTH, index % WIDTH); }
	public long[] get(int relativeX, int relativeZ)
	{
		int dataArrayIndex = (relativeX * WIDTH) + relativeZ;
		if (dataArrayIndex >= this.dataArrays.length)
		{
			LodUtil.assertNotReach(
					"FullDataArrayAccessor.get() called with a relative position that is outside the data source. \n" +
							"given relative pos X: [" + relativeX + "] Z: [" + relativeZ + "]\n" +
							"dataArrays.length: [" + this.dataArrays.length + "] dataArrayIndex: [" + dataArrayIndex + "].");
		}
		
		return this.dataArrays[dataArrayIndex];
	}
	
	
	
	//=================//
	// stream handling // 
	//=================//
	
	/**
	 * Clears and then overwrites any data in this object with the data from the given file and stream.
	 * This is expected to be used with an existing {@link FullDataSourceV1} and can be used in place of a constructor to reuse an existing {@link FullDataSourceV1} object.
	 */
	public void repopulateFromStream(FullDataSourceV1DTO dto, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException, DataCorruptedException
	{
		// clear/overwrite the old data
		this.resizeDataStructuresForRepopulation(dto.pos);
		this.mapping.clear(dto.pos);
		
		// set the new data
		this.populateFromStream(dto, inputStream, level);
	}
	
	/**
	 * Overwrites any data in this object with the data from the given file and stream.
	 * This is expected to be used with an empty {@link FullDataSourceV1} and functions similar to a constructor.
	 */
	public void populateFromStream(FullDataSourceV1DTO dto, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException, DataCorruptedException
	{
		FullDataSourceSummaryData summaryData = this.readSourceSummaryInfo(dto, inputStream, level);
		this.setSourceSummaryData(summaryData);
		
		
		long[][] dataPoints = this.readDataPoints(summaryData.dataWidth, inputStream);
		if (dataPoints == null)
		{
			return;
		}
		this.setDataPoints(dataPoints);
		
		
		FullDataPointIdMap mapping = this.readIdMappings(inputStream, level.getLevelWrapper());
		this.setIdMapping(mapping);
		
	}
	
	
	// low level stream methods //
	
	/** unused, just here for reference as to how the data was written */
	@Deprecated
	public void writeSourceSummaryInfo(IDhLevel level, DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(this.getDataDetailLevel());
		outputStream.writeInt(WIDTH);
		outputStream.writeInt(level.getMinY());
		outputStream.writeByte(this.worldGenStep.value);
		
	}
	public FullDataSourceSummaryData readSourceSummaryInfo(FullDataSourceV1DTO dto, DhDataInputStream inputStream, IDhLevel level) throws IOException
	{
		int dataDetail = inputStream.readInt();
		if (dataDetail != dto.dataDetailLevel)
		{
			throw new IOException(LodUtil.formatLog("Data level mismatch. Expected: ["+dto.dataDetailLevel+"], found ["+dataDetail+"]."));
		}
		
		int width = inputStream.readInt();
		if (width != WIDTH)
		{
			throw new IOException(LodUtil.formatLog("Section width mismatch: " + width + " != " + WIDTH + " (Currently only 1 section width is supported)"));
		}
		
		int minY = inputStream.readInt();
		if (minY != level.getMinY())
		{
			LOGGER.warn("Data minY mismatch: " + minY + " != " + level.getMinY() + ". Will ignore data's y level");
		}
		
		byte worldGenByte = inputStream.readByte();
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromValue(worldGenByte);
		if (worldGenStep == null)
		{
			worldGenStep = EDhApiWorldGenerationStep.SURFACE;
			LOGGER.warn("Missing WorldGenStep, defaulting to: " + worldGenStep.name());
		}
		
		
		return new FullDataSourceSummaryData(width, worldGenStep);
	}
	public void setSourceSummaryData(FullDataSourceSummaryData summaryData) { this.worldGenStep = summaryData.worldGenStep; }
	
	/** unused, just here for reference as to how the data was written */
	@Deprecated
	public boolean writeDataPoints(DhDataOutputStream outputStream) throws IOException
	{
		if (this.isEmpty())
		{
			outputStream.writeInt(NO_DATA_FLAG_BYTE);
			return false;
		}
		outputStream.writeInt(DATA_GUARD_BYTE);
		
		
		
		// Data array length
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				outputStream.writeInt(this.get(x, z).length);
			}
		}
		
		
		
		// Data array content (only on non-empty columns)
		outputStream.writeInt(DATA_GUARD_BYTE);
		for (int x = 0; x < WIDTH; x++)
		{
			for (int z = 0; z < WIDTH; z++)
			{
				long[] dataColumn = this.get(x, z);
				if (dataColumn != null)
				{
					for (long dataPoint : dataColumn)
					{
						outputStream.writeLong(dataPoint);
					}
				}
			}
		}
		
		
		return true;
	}
	public long[][] readDataPoints(int width, DhDataInputStream dataInputStream) throws IOException
	{
		// Data array length
		int dataPresentFlag = dataInputStream.readInt();
		if (dataPresentFlag == NO_DATA_FLAG_BYTE)
		{
			// Section is empty
			return null;
		}
		else if (dataPresentFlag != DATA_GUARD_BYTE)
		{
			throw new IOException("Invalid file format. Data Points guard byte expected: (no data) [" + NO_DATA_FLAG_BYTE + "] or (data present) [" + DATA_GUARD_BYTE + "], but found [" + dataPresentFlag + "].");
		}
		
		
		
		long[][] dataPointArrays;
		if (WIDTH == width) // attempt to use the existing dataArrays if possible
		{
			dataPointArrays = this.dataArrays;
		}
		else
		{
			dataPointArrays = new long[width * width][];
		}
		
		for (int x = 0; x < width; x++)
		{
			for (int z = 0; z < width; z++)
			{
				int requestedArrayLength = dataInputStream.readInt();
				int arrayIndex = x * width + z;
				
				// attempt to use the existing dataArrays if possible
				if (dataPointArrays[arrayIndex] == null || dataPointArrays[arrayIndex].length != requestedArrayLength)
				{
					dataPointArrays[arrayIndex] = new long[requestedArrayLength];
				}
				else
				{
					// clear the existing array to prevent any data leakage
					Arrays.fill(dataPointArrays[arrayIndex], 0);
				}
			}
		}
		
		
		
		// check if the array start flag is present
		int arrayStartFlag = dataInputStream.readInt();
		if (arrayStartFlag != DATA_GUARD_BYTE)
		{
			throw new IOException("invalid data length end guard");
		}
		
		for (int xz = 0; xz < dataPointArrays.length; xz++) // x and z are combined
		{
			if (dataPointArrays[xz].length != 0)
			{
				for (int y = 0; y < dataPointArrays[xz].length; y++)
				{
					dataPointArrays[xz][y] = dataInputStream.readLong();
				}
			}
		}
		
		
		
		return dataPointArrays;
	}
	public void setDataPoints(long[][] dataPoints)
	{
		LodUtil.assertTrue(this.dataArrays.length == dataPoints.length, "Data point array length mismatch.");
		
		this.isEmpty = false;
		System.arraycopy(dataPoints, 0, this.dataArrays, 0, dataPoints.length);
	}
	
	
	/** unused, just here for reference as to how the data was written */
	@Deprecated
	public void writeIdMappings(DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(DATA_GUARD_BYTE);
		this.mapping.serialize(outputStream);
	}
	public FullDataPointIdMap readIdMappings(DhDataInputStream inputStream, ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException
	{
		int guardByte = inputStream.readInt();
		if (guardByte != DATA_GUARD_BYTE)
		{
			throw new IOException("Invalid data content end guard for ID mapping");
		}
		
		return FullDataPointIdMap.deserialize(inputStream, this.pos, levelWrapper);
	}
	public void setIdMapping(FullDataPointIdMap mappings) { this.mapping.mergeAndReturnRemappedEntityIds(mappings); }
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public void close()
	{ /* not currently needed */ }
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * This holds information that is relevant to the entire source and isn't stored in the data points. <br>
	 * Example: minimum height, detail level, source type, etc.
	 */
	private static class FullDataSourceSummaryData
	{
		public final int dataWidth;
		public EDhApiWorldGenerationStep worldGenStep;
		
		
		public FullDataSourceSummaryData(int dataWidth, EDhApiWorldGenerationStep worldGenStep)
		{
			this.dataWidth = dataWidth;
			this.worldGenStep = worldGenStep;
		}
		
	}
	
}
