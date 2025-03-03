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

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.dto.ChunkHashDTO;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class ChunkHashRepo extends AbstractDhRepo<DhChunkPos, ChunkHashDTO>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkHashRepo(String databaseType, File databaseFile) throws SQLException
	{
		super(databaseType, databaseFile, ChunkHashDTO.class);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public String getTableName() { return "ChunkHash"; }
	
	@Override
	protected String CreateParameterizedWhereString() { return "ChunkPosX = ? AND ChunkPosZ = ?"; }
	
	@Override
	protected int setPreparedStatementWhereClause(PreparedStatement statement, int index, DhChunkPos pos) throws SQLException
	{
		statement.setInt(index++, pos.getX());
		statement.setInt(index++, pos.getZ());
		return index;
	}
	
	
	
	//=======================//
	// repo required methods //
	//=======================//
	
	@Override
	@Nullable
	public ChunkHashDTO convertResultSetToDto(ResultSet resultSet) throws ClassCastException, SQLException
	{
		int posX = resultSet.getInt("ChunkPosX");
		int posZ = resultSet.getInt("ChunkPosZ");
		
		int chunkHash = resultSet.getInt("ChunkHash");
		
		
		ChunkHashDTO dto = new ChunkHashDTO(new DhChunkPos(posX, posZ), chunkHash);
		return dto;
	}
	
	@Override
	public PreparedStatement createInsertStatement(ChunkHashDTO dto) throws SQLException
	{
		String sql =
			"INSERT INTO "+this.getTableName() + " (\n" +
			"   ChunkPosX, ChunkPosZ, \n" +
			"   ChunkHash, \n" +
			"   LastModifiedUnixDateTime, CreatedUnixDateTime) \n" +
			"VALUES( \n" +
			"    ?, ?, \n" +
			"    ?, \n" +
			"    ?, ? \n" +
			");";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.pos.getX());
		statement.setObject(i++, dto.pos.getZ());
		
		statement.setObject(i++, dto.chunkHash);
		
		statement.setObject(i++, System.currentTimeMillis()); // last modified unix time
		statement.setObject(i++, System.currentTimeMillis()); // created unix time
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(ChunkHashDTO dto) throws SQLException
	{
		String sql =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"    ChunkHash = ? \n" +
			"   ,LastModifiedUnixDateTime = ? \n" +
			"WHERE ChunkPosX = ? AND ChunkPosZ = ?";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.chunkHash);
		statement.setObject(i++, System.currentTimeMillis()); // last modified unix time
		
		statement.setObject(i++, dto.pos.getX());
		statement.setObject(i++, dto.pos.getZ());
		
		return statement;
	}
	
	
	
}
