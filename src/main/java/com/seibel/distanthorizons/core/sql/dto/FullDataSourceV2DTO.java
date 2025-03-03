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

package com.seibel.distanthorizons.core.sql.dto;

import com.google.common.base.MoreObjects;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListParent;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.network.INetworkObject;
import com.seibel.distanthorizons.core.util.BoolUtil;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.ListUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/** handles storing {@link FullDataSourceV2}'s in the database. */
public class FullDataSourceV2DTO 
		extends PhantomArrayListParent
		implements IBaseDTO<Long>, INetworkObject, AutoCloseable
{
	public static final boolean VALIDATE_INPUT_DATAPOINTS = true;
	
	
	public long pos;
	
	public int levelMinY;
	
	/** only for the data array */
	public int dataChecksum;
	
	public ByteArrayList compressedDataByteArray;
	
	/** @see EDhApiWorldGenerationStep */
	public ByteArrayList compressedColumnGenStepByteArray;
	/** @see EDhApiWorldCompressionMode */
	public ByteArrayList compressedWorldCompressionModeByteArray;
	
	public ByteArrayList compressedMappingByteArray;
	
	public byte dataFormatVersion;
	public byte compressionModeValue;
	
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToParent;
	/** Will be null if we don't want to update this value in the DB */
	@Nullable
	public Boolean applyToChildren;
	
	public long lastModifiedUnixDateTime;
	public long createdUnixDateTime;
	
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("V2DTO");
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static FullDataSourceV2DTO CreateFromDataSource(FullDataSourceV2 dataSource, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
		
		// populate arrays
		writeDataSourceDataArrayToBlob(dataSource.dataPoints, dto.compressedDataByteArray, compressionModeEnum);
		writeGenerationStepsToBlob(dataSource.columnGenerationSteps, dto.compressedColumnGenStepByteArray, compressionModeEnum);
		writeWorldCompressionModeToBlob(dataSource.columnWorldCompressionMode, dto.compressedWorldCompressionModeByteArray, compressionModeEnum);
		writeDataMappingToBlob(dataSource.mapping, dto.compressedMappingByteArray, compressionModeEnum);
		
		// populate individual variables
		{
			dto.pos = dataSource.getPos();
			// the mapping hash isn't included since it takes significantly longer to calculate and 
			// as of the time of this comment (2025-1-22) the checksum isn't used for anything so changing it shouldn't cause any issues
			dto.dataChecksum = dataSource.hashCode();
			dto.dataFormatVersion = FullDataSourceV2.DATA_FORMAT_VERSION;
			dto.compressionModeValue = compressionModeEnum.value;
			dto.lastModifiedUnixDateTime = dataSource.lastModifiedUnixDateTime;
			dto.createdUnixDateTime = dataSource.createdUnixDateTime;
			dto.applyToParent = dataSource.applyToParent;
			dto.applyToChildren = dataSource.applyToChildren;
			dto.levelMinY = dataSource.levelMinY;
		}
		
		return dto;
	}
	
	/** Should only be used for subsequent decoding */
	public static FullDataSourceV2DTO CreateEmptyDataSourceForDecoding() { return new FullDataSourceV2DTO(); }
	private FullDataSourceV2DTO() 
	{
		super(ARRAY_LIST_POOL, 4, 0, 0);
		
		// Expected sizes here are 0 since we don't know how big these arrays need to be,
		// they depend on compression settings and world complexity.
		this.compressedDataByteArray = this.pooledArraysCheckout.getByteArray(0, 0);
		this.compressedColumnGenStepByteArray = this.pooledArraysCheckout.getByteArray(1, 0);
		this.compressedWorldCompressionModeByteArray = this.pooledArraysCheckout.getByteArray(2, 0);
		this.compressedMappingByteArray = this.pooledArraysCheckout.getByteArray(3, 0);
	}
	
	
	
	//========================//
	// data source population //
	//========================//
	
	public FullDataSourceV2 createDataSource(@NotNull ILevelWrapper levelWrapper) throws IOException, InterruptedException, DataCorruptedException
	{
		FullDataSourceV2 dataSource = FullDataSourceV2.createEmpty(this.pos);
		this.internalPopulateDataSource(dataSource, levelWrapper, false);
		return dataSource;
	}
	
	/** 
	 * May be missing one or more data fields. <br>
	 * Designed to be used without access to Minecraft or any supporting objects. 
	 */
	public FullDataSourceV2 createUnitTestDataSource() throws IOException, InterruptedException, DataCorruptedException 
	{ return this.internalPopulateDataSource(FullDataSourceV2.createEmpty(this.pos), null, true); }
	
	private FullDataSourceV2 internalPopulateDataSource(FullDataSourceV2 dataSource, ILevelWrapper levelWrapper, boolean unitTest) throws IOException, InterruptedException, DataCorruptedException
	{
		if (FullDataSourceV2.DATA_FORMAT_VERSION != this.dataFormatVersion)
		{
			throw new IllegalStateException("There should only be one data format ["+FullDataSourceV2.DATA_FORMAT_VERSION+"].");
		}
		
		
		EDhApiDataCompressionMode compressionModeEnum;
		try
		{
			compressionModeEnum = this.getCompressionMode();
		}
		catch (IllegalArgumentException e)
		{
			// may happen if ZStd was used (which was added and removed during the nightly builds)
			// or if the compressor value is changed to an invalid option
			throw new DataCorruptedException(e);
		}
		
		
		readBlobToGenerationSteps(this.compressedColumnGenStepByteArray, dataSource.columnGenerationSteps, compressionModeEnum);
		readBlobToWorldCompressionMode(this.compressedWorldCompressionModeByteArray, dataSource.columnWorldCompressionMode, compressionModeEnum);
		readBlobToDataSourceDataArray(this.compressedDataByteArray, dataSource.dataPoints, compressionModeEnum);
		
		dataSource.mapping.clear(dataSource.getPos());
		// should only be null when used in a unit test
		if (!unitTest)
		{
			if (levelWrapper == null)
			{
				throw new NullPointerException("No level wrapper present, unable to deserialize data map. This should only be used for unit tests.");
			}
			
			FullDataPointIdMap newMap = readBlobToDataMapping(this.compressedMappingByteArray, dataSource.getPos(), levelWrapper,  compressionModeEnum);
			dataSource.mapping.addAll(newMap);
			if (dataSource.mapping.size() != newMap.size())
			{
				// if the mappings are out of sync then the LODs will render incorrectly due to IDs being wrong
				LodUtil.assertNotReach("ID maps out of sync for pos: "+this.pos);
			}
		}
		
		dataSource.lastModifiedUnixDateTime = this.lastModifiedUnixDateTime;
		dataSource.createdUnixDateTime = this.createdUnixDateTime;
		
		dataSource.levelMinY = this.levelMinY;
		
		dataSource.isEmpty = false;
		
		if (this.applyToParent != null)
		{
			dataSource.applyToParent = this.applyToParent;
		}
		if (this.applyToChildren != null)
		{
			dataSource.applyToChildren = this.applyToChildren;
		}
		
		return dataSource;
	}
	
	
	
	//=================//
	// (de)serializing //
	//=================//
	
	private static void writeDataSourceDataArrayToBlob(LongArrayList[] inputDataArray, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		// write the outputs to a stream to prep for writing to the database
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		
		// normally a DhStream should be the topmost stream to prevent closing the stream accidentally, 
		// but since this stream will be closed immediately after writing anyway, it won't be an issue
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		
		// write the data
		int dataArrayLength = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;
		for (int xz = 0; xz < dataArrayLength; xz++)
		{
			LongArrayList dataColumn = inputDataArray[xz];
			
			// write column length
			short columnLength = (dataColumn != null) ? (short) dataColumn.size() : 0;
			// a short is used instead of an int because at most we store 4096 vertical slices and a 
			// short fits that with less wasted spaces vs an int (short has max value of 32,767 vs int's max of 2 billion)
			compressedOut.writeShort(columnLength);
			
			// write column data (will be skipped if no data was present)
			for (int y = 0; y < columnLength; y++)
			{
				compressedOut.writeLong(dataColumn.getLong(y));
			}
		}
		
		
		// generate the checksum
		compressedOut.flush();
		byteArrayOutputStream.close();
		outputByteArray.addElements(0, byteArrayOutputStream.toByteArray());
	}
	private static void readBlobToDataSourceDataArray(ByteArrayList inputCompressedDataByteArray, LongArrayList[] outputDataLongArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputCompressedDataByteArray.elements());
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		
		// read the data
		int dataArrayLength = FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH;
		for (int xz = 0; xz < dataArrayLength; xz++)
		{
			// read the column length
			short dataColumnLength = compressedIn.readShort(); // separate variables are used for debugging and in case validation wants to be added later 
			if (dataColumnLength < 0)
			{
				throw new DataCorruptedException("Read DataSource Blob data at index ["+xz+"], column length ["+dataColumnLength+"] should be greater than zero.");
			}
			
			LongArrayList dataColumn = outputDataLongArray[xz];
			ListUtil.clearAndSetSize(dataColumn, dataColumnLength);
			
			// read column data (will be skipped if no data was present)
			for (int y = 0; y < dataColumnLength; y++)
			{
				long dataPoint = compressedIn.readLong();
				if (VALIDATE_INPUT_DATAPOINTS)
				{
					FullDataPointUtil.validateDatapoint(dataPoint);
				}
				dataColumn.set(y, dataPoint);
			}
		}
	}
	
	
	private static void writeGenerationStepsToBlob(ByteArrayList inputColumnGenStepByteArray, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		for (int i = 0; i < inputColumnGenStepByteArray.size(); i++)
		{
			compressedOut.writeByte(inputColumnGenStepByteArray.getByte(i));
		}
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		outputByteArray.addElements(0, byteArrayOutputStream.toByteArray());
	}
	private static void readBlobToGenerationSteps(ByteArrayList inputCompressedDataByteArray, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputCompressedDataByteArray.elements());
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		try
		{
			compressedIn.readFully(outputByteArray.elements(), 0, FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH);
		}
		catch (EOFException e)
		{
			throw new DataCorruptedException(e);
		}
	}
	
	
	private static void writeWorldCompressionModeToBlob(ByteArrayList inputWorldCompressionModeByteArray, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		for (int i = 0; i < inputWorldCompressionModeByteArray.size(); i++)
		{
			compressedOut.write(inputWorldCompressionModeByteArray.getByte(i));
		}
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		outputByteArray.addElements(0, byteArrayOutputStream.toByteArray());
	}
	private static void readBlobToWorldCompressionMode(ByteArrayList inputCompressedDataByteArray, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputCompressedDataByteArray.elements());
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		try
		{
			compressedIn.readFully(outputByteArray.elements(), 0, FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH);
		}
		catch (EOFException e)
		{
			throw new DataCorruptedException(e);
		}
	}
	
	
	private static void writeDataMappingToBlob(FullDataPointIdMap mapping, ByteArrayList outputByteArray, EDhApiDataCompressionMode compressionModeEnum) throws IOException
	{
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		DhDataOutputStream compressedOut = new DhDataOutputStream(byteArrayOutputStream, compressionModeEnum);
		
		mapping.serialize(compressedOut);
		
		compressedOut.flush();
		byteArrayOutputStream.close();
		outputByteArray.addElements(0, byteArrayOutputStream.toByteArray());
	}
	private static FullDataPointIdMap readBlobToDataMapping(ByteArrayList compressedMappingByteArray, long pos, @NotNull ILevelWrapper levelWrapper, EDhApiDataCompressionMode compressionModeEnum) throws IOException, InterruptedException, DataCorruptedException
	{
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedMappingByteArray.elements());
		DhDataInputStream compressedIn = new DhDataInputStream(byteArrayInputStream, compressionModeEnum);
		
		FullDataPointIdMap mapping = FullDataPointIdMap.deserialize(compressedIn, pos, levelWrapper);
		return mapping;
	}
	
	
	
	//============//
	// networking //
	//============//
	
	@Override
	public void encode(ByteBuf out)
	{
		out.writeLong(this.pos);
		out.writeInt(this.dataChecksum);
		
		out.writeInt(this.compressedDataByteArray.size());
		out.writeBytes(this.compressedDataByteArray.elements(), 0, this.compressedDataByteArray.size());
		
		out.writeInt(this.compressedColumnGenStepByteArray.size());
		out.writeBytes(this.compressedColumnGenStepByteArray.elements(), 0, this.compressedColumnGenStepByteArray.size());
		out.writeInt(this.compressedWorldCompressionModeByteArray.size());
		out.writeBytes(this.compressedWorldCompressionModeByteArray.elements(), 0, this.compressedWorldCompressionModeByteArray.size());
		
		out.writeInt(this.compressedMappingByteArray.size());
		out.writeBytes(this.compressedMappingByteArray.elements(), 0, this.compressedMappingByteArray.size());
		
		out.writeByte(this.dataFormatVersion);
		out.writeByte(this.compressionModeValue);
		
		out.writeBoolean(BoolUtil.falseIfNull(this.applyToParent));
		out.writeBoolean(BoolUtil.falseIfNull(this.applyToChildren));
		
		out.writeLong(this.lastModifiedUnixDateTime);
		out.writeLong(this.createdUnixDateTime);
	}
	
	@Override
	public void decode(ByteBuf in)
	{
		this.pos = in.readLong();
		this.dataChecksum = in.readInt();
		
		this.compressedDataByteArray.size(in.readInt());
		in.readBytes(this.compressedDataByteArray.elements(), 0, this.compressedDataByteArray.size());
		
		this.compressedColumnGenStepByteArray.size(in.readInt());
		in.readBytes(this.compressedColumnGenStepByteArray.elements(), 0, this.compressedColumnGenStepByteArray.size());
		this.compressedWorldCompressionModeByteArray.size(in.readInt());
		in.readBytes(this.compressedWorldCompressionModeByteArray.elements(), 0, this.compressedWorldCompressionModeByteArray.size());
		
		this.compressedMappingByteArray.size(in.readInt());
		in.readBytes(this.compressedMappingByteArray.elements(), 0, this.compressedMappingByteArray.size());
		
		this.dataFormatVersion = in.readByte();
		this.compressionModeValue = in.readByte();
		
		this.applyToParent = in.readBoolean();
		this.applyToChildren = in.readBoolean();
		
		this.lastModifiedUnixDateTime = in.readLong();
		this.createdUnixDateTime = in.readLong();
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	public EDhApiDataCompressionMode getCompressionMode() throws IllegalArgumentException { return EDhApiDataCompressionMode.getFromValue(this.compressionModeValue); }
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("levelMinY", this.levelMinY)
				.add("pos", this.pos)
				.add("dataChecksum", this.dataChecksum)
				.add("compressedDataByteArray length", this.compressedDataByteArray.size())
				.add("compressedColumnGenStepByteArray length", this.compressedColumnGenStepByteArray.size())
				.add("compressedWorldCompressionModeByteArray length", this.compressedWorldCompressionModeByteArray.size())
				.add("compressedMappingByteArray length", this.compressedMappingByteArray.size())
				.add("dataFormatVersion", this.dataFormatVersion)
				.add("compressionModeValue", this.compressionModeValue)
				.add("applyToParent", this.applyToParent)
				.add("applyToChildren", this.applyToChildren)
				.add("lastModifiedUnixDateTime", this.lastModifiedUnixDateTime)
				.add("createdUnixDateTime", this.createdUnixDateTime)
				.toString();
	}
	
	
	
}
