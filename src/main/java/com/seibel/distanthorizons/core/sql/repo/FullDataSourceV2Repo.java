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

package com.seibel.distanthorizons.core.sql.repo;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.DbConnectionClosedException;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.BoolUtil;
import com.seibel.distanthorizons.core.util.ListUtil;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class FullDataSourceV2Repo extends AbstractDhRepo<Long, FullDataSourceV2DTO>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV2Repo(String databaseType, File databaseFile) throws SQLException
	{
		super(databaseType, databaseFile, FullDataSourceV2DTO.class);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public String getTableName() { return "FullData"; }
	
	@Override
	protected String CreateParameterizedWhereString() { return "DetailLevel = ? AND PosX = ? AND PosZ = ?"; }
	
	@Override
	protected int setPreparedStatementWhereClause(PreparedStatement statement, int index, Long pos) throws SQLException
	{
		int detailLevel = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
		
		statement.setInt(index++, detailLevel);
		statement.setInt(index++, DhSectionPos.getX(pos));
		statement.setInt(index++, DhSectionPos.getZ(pos));
		
		return index;
	}
	
	
	
	@Override @Nullable
	public FullDataSourceV2DTO convertResultSetToDto(ResultSet resultSet) throws ClassCastException, IOException, SQLException
	{
		//======================//
		// get statement values //
		//======================//
		
		byte detailLevel = resultSet.getByte("DetailLevel");
		byte sectionDetailLevel = (byte) (detailLevel + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		int posX = resultSet.getInt("PosX");
		int posZ = resultSet.getInt("PosZ");
		long pos = DhSectionPos.encode(sectionDetailLevel, posX, posZ);
		
		int minY = resultSet.getInt("MinY");
		int dataChecksum = resultSet.getInt("DataChecksum");
		
		
		byte dataFormatVersion = resultSet.getByte("DataFormatVersion");
		byte compressionModeValue = resultSet.getByte("CompressionMode");
		
		// while these values can be null in the DB, null would just equate to false
		boolean applyToParent = (resultSet.getInt("ApplyToParent")) == 1;
		boolean applyToChildren = (resultSet.getInt("ApplyToChildren")) == 1;
		
		long lastModifiedUnixDateTime = resultSet.getLong("LastModifiedUnixDateTime");
		long createdUnixDateTime = resultSet.getLong("CreatedUnixDateTime");
		
		
		
		//===================//
		// set DTO variables //
		//===================//
		
		FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
		
		// set pooled arrays
		dto.compressedDataByteArray = putAllBytes(resultSet.getBinaryStream("Data"), dto.compressedDataByteArray);
		dto.compressedColumnGenStepByteArray = putAllBytes(resultSet.getBinaryStream("ColumnGenerationStep"), dto.compressedColumnGenStepByteArray);
		dto.compressedWorldCompressionModeByteArray = putAllBytes(resultSet.getBinaryStream("ColumnWorldCompressionMode"), dto.compressedWorldCompressionModeByteArray);
		dto.compressedMappingByteArray = putAllBytes(resultSet.getBinaryStream("Mapping"), dto.compressedMappingByteArray);
		
		// set individual variables
		{
			dto.pos = pos;
			dto.dataChecksum = dataChecksum;
			dto.dataFormatVersion = dataFormatVersion;
			dto.compressionModeValue = compressionModeValue;
			dto.lastModifiedUnixDateTime = lastModifiedUnixDateTime;
			dto.createdUnixDateTime = createdUnixDateTime;
			dto.applyToParent = applyToParent;
			dto.applyToChildren = applyToChildren;
			dto.levelMinY = minY;
		}
		return dto;
	}
	
	private final String insertSqlTemplate =
		"INSERT INTO "+this.getTableName() + " (\n" +
		"   DetailLevel, PosX, PosZ, \n" +
		"   MinY, DataChecksum, \n" +
		"   Data, ColumnGenerationStep, ColumnWorldCompressionMode, Mapping, \n" +
		"   DataFormatVersion, CompressionMode, ApplyToParent, ApplyToChildren, \n" +
		"   LastModifiedUnixDateTime, CreatedUnixDateTime) \n" +
		"VALUES( \n" +
		"    ?, ?, ?, \n" +
		"    ?, ?, \n" +
		"    ?, ?, ?, ?, \n" +
		"    ?, ?, ?, ?, \n" +
		"    ?, ? \n" +
		");";
	@Override
	public PreparedStatement createInsertStatement(FullDataSourceV2DTO dto) throws SQLException
	{
		PreparedStatement statement = this.createPreparedStatement(this.insertSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setInt(i++, DhSectionPos.getDetailLevel(dto.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		statement.setInt(i++, DhSectionPos.getX(dto.pos));
		statement.setInt(i++, DhSectionPos.getZ(dto.pos));
		
		statement.setInt(i++, dto.levelMinY);
		statement.setInt(i++, dto.dataChecksum);
		
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedDataByteArray.elements()), dto.compressedDataByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedColumnGenStepByteArray.elements()), dto.compressedColumnGenStepByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedWorldCompressionModeByteArray.elements()), dto.compressedWorldCompressionModeByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedMappingByteArray.elements()), dto.compressedMappingByteArray.size());
		
		statement.setByte(i++, dto.dataFormatVersion);
		statement.setByte(i++, dto.compressionModeValue);
		// if nothing is present assume we don't need/want to propagate updates
		statement.setBoolean(i++, BoolUtil.falseIfNull(dto.applyToParent));
		statement.setBoolean(i++, BoolUtil.falseIfNull(dto.applyToChildren));
		
		statement.setLong(i++, System.currentTimeMillis()); // last modified unix time
		statement.setLong(i++, System.currentTimeMillis()); // created unix time
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(FullDataSourceV2DTO dto) throws SQLException
	{
		// Dynamic string so we can update one, both, or neither
		// of the applyTo... flags.
		// This is necessary to prevent concurrent modifications when
		// update propagation is run.
		String updateSqlTemplate = (
				"UPDATE "+this.getTableName()+" \n" +
				"SET \n" +
				"    MinY = ? \n" +
				"   ,DataChecksum = ? \n" +
				
				"   ,Data = ? \n" +
				"   ,ColumnGenerationStep = ? \n" +
				"   ,ColumnWorldCompressionMode = ? \n" +
				"   ,Mapping = ? \n" +
				
				"   ,DataFormatVersion = ? \n" +
				"   ,CompressionMode = ? \n" +
					// only update these values if they're present
					(dto.applyToParent != null ? "   ,ApplyToParent = ? \n" : "" ) +
					(dto.applyToChildren != null ? "   ,ApplyToChildren = ? \n" : "" ) +
				
				"   ,LastModifiedUnixDateTime = ? \n" +
				"   ,CreatedUnixDateTime = ? \n" +
				
				"WHERE DetailLevel = ? AND PosX = ? AND PosZ = ?"
			// intern should help reduce memory overhead due to this string being dynamic
			).intern();
		
		
		PreparedStatement statement = this.createPreparedStatement(updateSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setInt(i++, dto.levelMinY);
		statement.setInt(i++, dto.dataChecksum);
		
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedDataByteArray.elements()), dto.compressedDataByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedColumnGenStepByteArray.elements()), dto.compressedColumnGenStepByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedWorldCompressionModeByteArray.elements()), dto.compressedWorldCompressionModeByteArray.size());
		statement.setBinaryStream(i++, new ByteArrayInputStream(dto.compressedMappingByteArray.elements()), dto.compressedMappingByteArray.size());
		
		statement.setByte(i++, dto.dataFormatVersion);
		statement.setByte(i++, dto.compressionModeValue);
		if (dto.applyToParent != null)
		{
			statement.setBoolean(i++, dto.applyToParent);
		}
		if (dto.applyToChildren != null)
		{
			statement.setBoolean(i++, dto.applyToChildren);
		}
		
		statement.setLong(i++, System.currentTimeMillis()); // last modified unix time
		statement.setLong(i++, dto.createdUnixDateTime);
		
		statement.setInt(i++, DhSectionPos.getDetailLevel(dto.pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		statement.setInt(i++, DhSectionPos.getX(dto.pos));
		statement.setInt(i++, DhSectionPos.getZ(dto.pos));
		
		return statement;
	}
	
	
	
	//=========//
	// updates //
	//=========//
	
	/** should be be very similar to {@link FullDataSourceV2Repo#setApplyToChildrenSql} */
	private final String setApplyToParentSql = 
			"UPDATE "+this.getTableName()+" \n" +
			"SET ApplyToParent = ? \n" +
			"WHERE DetailLevel = ? AND PosX = ? AND PosZ = ?";
	public void setApplyToParent(long pos, boolean applyToParent)
	{ this.setApplyToFlag(pos, applyToParent, true); }
	
	/** should be be very similar to {@link FullDataSourceV2Repo#setApplyToParentSql} */
	private final String setApplyToChildrenSql =
			"UPDATE "+this.getTableName()+" \n" +
					"SET ApplyToChildren = ? \n" +
					"WHERE DetailLevel = ? AND PosX = ? AND PosZ = ?";
	public void setApplyToChild(long pos, boolean applyToChild)
	{ this.setApplyToFlag(pos, applyToChild, false); }
	
	private void setApplyToFlag(long pos, boolean applyFlag, boolean applyToParent)
	{
		String sql = applyToParent ? this.setApplyToParentSql : this.setApplyToChildrenSql;
		PreparedStatement statement = this.createPreparedStatement(sql);
		if (statement == null)
		{
			return;
		}
		
		
		try
		{
			int i = 1;
			statement.setBoolean(i++, applyFlag);
			
			int detailLevel = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
			statement.setInt(i++, detailLevel);
			statement.setInt(i++, DhSectionPos.getX(pos));
			statement.setInt(i++, DhSectionPos.getZ(pos));
			
			try (ResultSet result = this.query(statement))
			{
				
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	/** should be be very similar to {@link FullDataSourceV2Repo#getChildPositionsToUpdateSql} */
	private final String getParentPositionsToUpdateSql =
			"SELECT DetailLevel, PosX, PosZ, " +
					"   abs((PosX << (6 + DetailLevel)) - ?) + abs((PosZ << (6 + DetailLevel)) - ?) AS Distance " +
					"FROM " + this.getTableName() + " " +
					"WHERE ApplyToParent = 1 " +
					"ORDER BY DetailLevel ASC, Distance ASC " +
					"LIMIT ?; ";
	public LongArrayList getPositionsToUpdate(int targetBlockPosX, int targetBlockPosZ, int returnCount)
	{ return this.getPositionsToUpdate(targetBlockPosX, targetBlockPosZ, returnCount, true); }
	
	/** should be be very similar to {@link FullDataSourceV2Repo#getParentPositionsToUpdateSql} */
	private final String getChildPositionsToUpdateSql =
			"SELECT DetailLevel, PosX, PosZ, " +
					"   abs((PosX << (6 + DetailLevel)) - ?) + abs((PosZ << (6 + DetailLevel)) - ?) AS Distance " +
					"FROM " + this.getTableName() + " " +
					"WHERE ApplyToChildren = 1 " +
					"ORDER BY DetailLevel ASC, Distance ASC " +
					"LIMIT ?; ";
	public LongArrayList getChildPositionsToUpdate(int targetBlockPosX, int targetBlockPosZ, int returnCount)
	{ return this.getPositionsToUpdate(targetBlockPosX, targetBlockPosZ, returnCount, false); }
	
	private LongArrayList getPositionsToUpdate(int targetBlockPosX, int targetBlockPosZ, int returnCount, boolean getParentUpdates)
	{
		LongArrayList list = new LongArrayList();
		
		String sql = getParentUpdates ? this.getParentPositionsToUpdateSql : this.getChildPositionsToUpdateSql;
		PreparedStatement statement = this.createPreparedStatement(sql);
		if (statement == null)
		{
			return list;
		}
		
		try
		{
			int i = 1;
			statement.setInt(i++, targetBlockPosX);
			statement.setInt(i++, targetBlockPosZ);
			
			statement.setInt(i++, returnCount);
			
			try (ResultSet result = this.query(statement))
			{
				while (result != null && result.next())
				{
					byte detailLevel = result.getByte("DetailLevel");
					byte sectionDetailLevel = (byte) (detailLevel + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
					int posX = result.getInt("PosX");
					int posZ = result.getInt("PosZ");
					
					long pos = DhSectionPos.encode(sectionDetailLevel, posX, posZ);
					list.add(pos);
				}
			}
			
			return list;
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	private final String getColumnGenerationStepSql =
			"select ColumnGenerationStep, CompressionMode " +
			"from "+this.getTableName()+" " +
			"WHERE DetailLevel = ? AND PosX = ? AND PosZ = ?";
	/** @return null if nothing exists for this position */
	public void getColumnGenerationStepForPos(long pos, ByteArrayList outputByteArray)
	{
		PreparedStatement statement = this.createPreparedStatement(this.getColumnGenerationStepSql);
		if (statement == null)
		{
			return;
		}
		
		
		try
		{
			int detailLevel = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
			
			
			
			int i = 1;
			statement.setInt(i++, detailLevel);
			statement.setInt(i++, DhSectionPos.getX(pos));
			statement.setInt(i++, DhSectionPos.getZ(pos));
			
			
			try (ResultSet result = this.query(statement))
			{
				if (result == null || !result.next())
				{
					return;
				}
				
				
				byte compressionModeEnumValue = result.getByte("CompressionMode");
				EDhApiDataCompressionMode compressionModeEnum = EDhApiDataCompressionMode.getFromValue(compressionModeEnumValue);
				
				try
				{
					// decompress the data
					DhDataInputStream compressedIn = new DhDataInputStream(result.getBinaryStream("ColumnGenerationStep"), compressionModeEnum);
					putAllBytes(compressedIn, outputByteArray);
				}
				catch (IOException e)
				{
					LOGGER.warn("Decompression issue when getting column gen steps for pos: [" + DhSectionPos.toString(pos) + "], deleting corrupted data.", e);
					
					this.deleteWithKey(pos);
					ListUtil.clearAndSetSize(outputByteArray, FullDataSourceV2.WIDTH * FullDataSourceV2.WIDTH);
				}
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//=============//
	// multiplayer //
	//=============//
	
	private final String getTimestampForPosSql =
			"SELECT LastModifiedUnixDateTime " +
			"FROM " + this.getTableName() + " " +
			"WHERE DetailLevel = ? " +
			"AND PosX = ? " +
			"AND PosZ = ?;";
	@Nullable
	public Long getTimestampForPos(long pos)
	{
		try
		{
			PreparedStatement preparedStatement = this.createPreparedStatement(this.getTimestampForPosSql);
			if (preparedStatement == null)
			{
				return null;
			}
			
			
			int i = 1;
			preparedStatement.setInt(i++, DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
			preparedStatement.setInt(i++, DhSectionPos.getX(pos));
			preparedStatement.setInt(i++, DhSectionPos.getZ(pos));
			
			try (ResultSet result = this.query(preparedStatement))
			{
				if (result == null || !result.next())
				{
					return null;
				}
				
				return result.getLong("LastModifiedUnixDateTime");
			}
		}
		catch (DbConnectionClosedException e)
		{
			return null;
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private final String getTimestampForRangeSql =
			"SELECT PosX, PosZ, LastModifiedUnixDateTime " +
			"FROM " + this.getTableName() + " " +
			"WHERE DetailLevel = ? " +
			"AND PosX BETWEEN ? AND ? " +
			"AND PosZ BETWEEN ? AND ?;";
	public Map<Long, Long> getTimestampsForRange(byte detailLevel, int startPosX, int startPosZ, int endPosX, int endPosZ)
	{
		try
		{
			PreparedStatement preparedStatement = this.createPreparedStatement(this.getTimestampForRangeSql);
			if (preparedStatement == null)
			{
				return new HashMap<>();
			}
			
			
			int i = 1;
			preparedStatement.setInt(i++, detailLevel - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
			preparedStatement.setInt(i++, startPosX);
			preparedStatement.setInt(i++, endPosX - 1);
			preparedStatement.setInt(i++, startPosZ);
			preparedStatement.setInt(i++, endPosZ - 1);
			
			
			try (ResultSet result = this.query(preparedStatement))
			{
				HashMap<Long, Long> returnMap = new HashMap<>();
				while (result != null && result.next())
				{
					long key = DhSectionPos.encode(detailLevel, result.getInt("PosX"), result.getInt("PosZ"));
					long value = result.getLong("LastModifiedUnixDateTime");
					
					returnMap.put(key, value);
				}
				
				return returnMap;
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//===================//
	// compression tests //
	//===================//
	
	private final String getAllPositionsSql = 
			"select DetailLevel, PosX, PosZ " +
			"from "+this.getTableName()+"; ";
	/** @return every position in this database */
	public LongArrayList getAllPositions()
	{
		LongArrayList list = new LongArrayList();
		
		PreparedStatement statement = this.createPreparedStatement(this.getAllPositionsSql);
		if (statement == null)
		{
			return list;
		}
		
		
		try(ResultSet result = this.query(statement))
		{
			while (result != null && result.next())
			{
				byte detailLevel = result.getByte("DetailLevel");
				byte sectionDetailLevel = (byte) (detailLevel + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
				int posX = result.getInt("PosX");
				int posZ = result.getInt("PosZ");
				
				long pos = DhSectionPos.encode(sectionDetailLevel, posX, posZ);
				list.add(pos);
			}
			
			return list;
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	private final String getDataSizeInBytesSql =
			"select LENGTH(Data) as dataSize " +
			"from "+this.getTableName()+" " +
			"WHERE DetailLevel = ? AND PosX = ? AND PosZ = ?";
	/** 
	 * @return the size of the full data at the given position 
	 *          (doesn't include the size of the mapping or any other column)
	 */
	public long getDataSizeInBytes(long pos)
	{
		int detailLevel = DhSectionPos.getDetailLevel(pos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
		
		PreparedStatement statement = this.createPreparedStatement(this.getDataSizeInBytesSql);
		if (statement == null)
		{
			return 0L;
		}
		
		try
		{
			int i = 1;
			statement.setInt(i++, detailLevel);
			statement.setInt(i++, DhSectionPos.getX(pos));
			statement.setInt(i++, DhSectionPos.getZ(pos));
			
			
			try (ResultSet result = this.query(statement)) // TODO check other query's
			{
				if (result == null || !result.next())
				{
					return 0L;
				}
				
				return result.getLong("dataSize");
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	private final String getTotalDataSizeInBytesSql =
			"select SUM(LENGTH(Data)) as dataSize " +
			"from "+this.getTableName()+"; ";
	/** @return the total size in bytes of the full data for this entire database */
	public long getTotalDataSizeInBytes()
	{
		PreparedStatement statement = this.createPreparedStatement(this.getTotalDataSizeInBytesSql);
		
		try(ResultSet result = this.query(statement))
		{
			if (result == null || !result.next())
			{
				return 0;
			}
			
			return result.getLong("dataSize");
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	
	//===============//
	// helper method //
	//===============//
	
	private static ByteArrayList putAllBytes(InputStream inputStream, @Nullable ByteArrayList existingArrayList) throws IOException
	{
		if (existingArrayList == null)
		{
			// inputStream.available() can throw a null pointer due to a bug with LZMA stream so we have to estimate the array size
			existingArrayList = new ByteArrayList(64);
		}
		else
		{
			existingArrayList.clear();
		}
		
		try
		{
			int nextByte = inputStream.read();
			while (nextByte != -1)
			{
				existingArrayList.add((byte) nextByte);
				nextByte = inputStream.read();
			}
		}
		catch (EOFException ignore) { /* shouldn't happen, but just in case */ }
		
		return existingArrayList;
	}
	
	
	
}
