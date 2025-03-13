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

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV1;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Handles storing{@link FullDataSourceV1}'s in the database.
 */
public class FullDataSourceV1DTO implements IBaseDTO<Long>
{
	public long pos;
	public int checksum;
	public byte dataDetailLevel;
	public EDhApiWorldGenerationStep worldGenStep;
	
	// Loader stuff //
	/** indicates what data is held in this file, this is generally the data's name */
	public String dataType;
	public byte binaryDataFormatVersion;
	
	public final byte[] dataArray;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV1DTO(long pos, int checksum, byte dataDetailLevel, EDhApiWorldGenerationStep worldGenStep, String dataType, byte binaryDataFormatVersion, byte[] dataArray)
	{
		this.pos = pos;
		this.checksum = checksum;
		this.dataDetailLevel = dataDetailLevel;
		this.worldGenStep = worldGenStep;
		
		this.dataType = dataType;
		this.binaryDataFormatVersion = binaryDataFormatVersion;
		
		this.dataArray = dataArray;
	}
	
	
	/** @return a stream for the data contained in this DTO. */
	public DhDataInputStream getInputStream() throws IOException
	{
		InputStream inputStream = new ByteArrayInputStream(this.dataArray);
		DhDataInputStream compressedStream = new DhDataInputStream(inputStream, EDhApiDataCompressionMode.LZ4); // LZ4 was used by DH before 2.1.0 and as such must be used until the render data format is changed to record the compressor
		return compressedStream;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public Long getKey() { return this.pos; }
	@Override
	public String getKeyDisplayString() { return DhSectionPos.toString(this.pos); }
	
	@Override 
	public void close()
	{ /* no closing needed */ }
	
}
