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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.ChunkHashDTO;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV1DTO;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FullDataSourceV1Repo extends AbstractDhRepo<Long, FullDataSourceV1DTO>
{
	public static final String TABLE_NAME = "Legacy_FullData_V1";
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataSourceV1Repo(String databaseType, File databaseFile) throws SQLException
	{
		super(databaseType, databaseFile, FullDataSourceV1DTO.class);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public String getTableName() { return TABLE_NAME; }
	
	@Override
	protected String CreateParameterizedWhereString() { return "DhSectionPos = ?"; }
	
	@Override
	protected int setPreparedStatementWhereClause(PreparedStatement statement, int index, Long pos) throws SQLException
	{
		statement.setString(index++, serializeSectionPos(pos));
		return index;
	}
	
	
	
	//=======================//
	// repo required methods //
	//=======================//
	
	@Override
	@Nullable
	public FullDataSourceV1DTO convertResultSetToDto(ResultSet resultSet) throws ClassCastException, SQLException
	{
		String posString = resultSet.getString("DhSectionPos");
		Long pos = deserializeSectionPos(posString);
		
		// meta data
		int checksum = resultSet.getInt("Checksum");
		byte dataDetailLevel = resultSet.getByte("DataDetailLevel");
		String worldGenStepString = resultSet.getString("WorldGenStep");
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromName(worldGenStepString);
		
		String dataType = resultSet.getString("DataType");
		byte binaryDataFormatVersion = resultSet.getByte("BinaryDataFormatVersion");
		
		// binary data
		byte[] dataByteArray = resultSet.getBytes("Data");
		
		FullDataSourceV1DTO dto = new FullDataSourceV1DTO(
				pos,
				checksum, dataDetailLevel, worldGenStep,
				dataType, binaryDataFormatVersion, 
				dataByteArray);
		return dto;
	}
	
	private final String insertSqlTemplate =
			"INSERT INTO "+this.getTableName() + "\n" +
					"  (DhSectionPos, \n" +
					"Checksum, DataVersion, DataDetailLevel, WorldGenStep, DataType, BinaryDataFormatVersion, \n" +
					"Data) \n" +
					"   VALUES( \n" +
					"    ? \n" +
					"   ,? ,? ,? ,? ,? ,? \n" +
					"   ,? \n" +
					// created/lastModified are automatically set by Sqlite
					");";
	@Override
	public PreparedStatement createInsertStatement(FullDataSourceV1DTO dto) throws SQLException
	{
		PreparedStatement statement = this.createPreparedStatement(this.insertSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setString(i++, serializeSectionPos(dto.pos));
		
		statement.setInt(i++, dto.checksum);
		statement.setInt(i++, 0 /*dto.dataVersion*/);
		statement.setByte(i++, dto.dataDetailLevel);
		statement.setObject(i++, dto.worldGenStep);
		statement.setString(i++, dto.dataType);
		statement.setByte(i++, dto.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		return statement;
	}
	
	private final String updateSqlTemplate =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"    Checksum = ? \n" +
			"   ,DataVersion = ? \n" +
			"   ,DataDetailLevel = ? \n" +
			"   ,WorldGenStep = ? \n" +
			"   ,DataType = ? \n" +
			"   ,BinaryDataFormatVersion = ? \n" +
			
			"   ,Data = ? \n" +
			
			"   ,LastModifiedDateTime = CURRENT_TIMESTAMP \n" +
			"WHERE DhSectionPos = ?";
	@Override
	public PreparedStatement createUpdateStatement(FullDataSourceV1DTO dto) throws SQLException
	{
		PreparedStatement statement = this.createPreparedStatement(this.updateSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setInt(i++, dto.checksum);
		statement.setInt(i++, 0 /*dto.dataVersion*/);
		statement.setByte(i++, dto.dataDetailLevel);
		statement.setObject(i++, dto.worldGenStep);
		statement.setString(i++, dto.dataType);
		statement.setByte(i++, dto.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		statement.setString(i++, serializeSectionPos(dto.pos));
		
		return statement;
	}
	
	
	
	//===========//
	// migration //
	//===========//
	
	/** Returns how many positions need to be migrated over to the new version */
	public long getMigrationCount()
	{
		Map<String, Object> resultMap = this.queryDictionaryFirst(
				"select COUNT(*) as itemCount from "+this.getTableName()+" where MigrationFailed <> 1");
		
		if (resultMap == null)
		{
			return 0;
		}
		else
		{
			Number resultNumber = (Number) resultMap.get("itemCount");
			long count = resultNumber.longValue();
			return count;
		}
	}
	
	private final String getMigrationPositionsSqlTemplate =
			"SELECT DhSectionPos " +
			"FROM "+this.getTableName()+" " +
			"WHERE MigrationFailed <> 1 " +
			"LIMIT ?;";
	/** Returns the new "returnCount" positions that need to be migrated */
	public LongArrayList getPositionsToMigrate(int returnCount)
	{
		LongArrayList posList = new LongArrayList();
		
		try(PreparedStatement statement = this.createPreparedStatement(this.getMigrationPositionsSqlTemplate))
		{
			if (statement == null)
			{
				return posList;
			}
			
			
			int i = 1;
			statement.setInt(i++, returnCount);
			
			try (ResultSet result = this.query(statement))
			{
				while (result != null && result.next())
				{
					String posString = result.getString("DhSectionPos");
					// returned in the format [sectionDetailLevel,x,z] IE [6,0,0]
					Long sectionPos = deserializeSectionPos(posString);
					if (sectionPos != null)
					{
						posList.add(sectionPos.longValue());
					}
				}
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		
		return posList;
	}
	
	public void markMigrationFailed(long pos)
	{
		String sql =
				"UPDATE "+this.getTableName()+" \n" +
						"SET MigrationFailed = 1 \n" +
						"WHERE DhSectionPos = '"+serializeSectionPos(pos)+"'";
		
		this.queryDictionaryFirst(sql);
	}
	
	
	
	//======================//
	// migration - deletion //
	//======================//
	
	/** returns the number of data sources that should be deleted */
	public long getUnusedDataSourceCount()
	{
		Map<String, Object> resultMap = this.queryDictionaryFirst(
				"select Count(*) as unusedCount from "+this.getTableName()+" where DataDetailLevel <> 0 or DataType <> 'CompleteFullDataSource'");
		
		if (resultMap != null)
		{
			// Number cast is necessary because the returned number can be an int or long
			Number resultNumber = (Number) resultMap.get("unusedCount");
			long count = resultNumber.longValue();
			return count;
		}
		else
		{
			return 0;
		}
	}
	
	private final String getUnusedPositionSqlTemplate =
			"SELECT DhSectionPos " +
			"FROM "+this.getTableName()+" " +
			"WHERE DataDetailLevel <> 0 OR DataType <> 'CompleteFullDataSource' " +
			"LIMIT ?";
	/** Returns single quote surrounded {@link DhSectionPos} serailzed values */
	public ArrayList<String> getUnusedDataSourcePositionStringList(int limit)
	{
		ArrayList<String> deletePosList = new ArrayList<>();
		
		try(PreparedStatement statement = this.createPreparedStatement(this.getUnusedPositionSqlTemplate))
		{
			if (statement == null)
			{
				return deletePosList;
			}
			
			int i = 1;
			statement.setInt(i++, limit);
			
			try (ResultSet result = this.query(statement))
			{
				while (result != null && result.next())
				{
					String posString = result.getString("DhSectionPos");
					deletePosList.add("'"+posString+"'");
				}
			}
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
		
		return deletePosList;
	}
	
	/** Expects positions to already be surrounded in single quotes */
	public void deleteUnusedLegacyData(ArrayList<String> deletePosList)
	{
		String sectionPosCsv = StringUtil.join(",", deletePosList);
		this.queryDictionaryFirst("delete from " + this.getTableName() + " where DhSectionPos in (" + sectionPosCsv + ")");
	}
	
	
	
	//=====================//
	// section pos helpers //
	//=====================//
	
	private static String serializeSectionPos(long pos) { return "[" + DhSectionPos.getDetailLevel(pos) + ',' + DhSectionPos.getX(pos) + ',' + DhSectionPos.getZ(pos) + ']'; }
	
	@Nullable
	private static Long deserializeSectionPos(String value)
	{
		if (value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']')
		{
			return null;
		}
		
		String[] split = value.substring(1, value.length() - 1).split(",");
		if (split.length != 3)
		{
			return null;
		}
		
		return DhSectionPos.encode(Byte.parseByte(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
	}
	
	
	
}
