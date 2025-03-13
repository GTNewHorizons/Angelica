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
import com.seibel.distanthorizons.core.sql.DatabaseUpdater;
import com.seibel.distanthorizons.core.sql.DbConnectionClosedException;
import com.seibel.distanthorizons.core.sql.dto.IBaseDTO;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles interfacing with SQL databases.
 * 
 * @param <TDTO> DTO stands for "Data Transfer Object" 
 */
public abstract class AbstractDhRepo<TKey, TDTO extends IBaseDTO<TKey>> implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final String DEFAULT_DATABASE_TYPE = "jdbc:sqlite";
	/** a value of 0 means there's no timeout */
	public static final int TIMEOUT_SECONDS = 0;
	
	private static final ConcurrentHashMap<String, Connection> CONNECTIONS_BY_CONNECTION_STRING = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<AbstractDhRepo<?, ?>, String> ACTIVE_CONNECTION_STRINGS_BY_REPO = new ConcurrentHashMap<>();
	
	private static final ThreadPoolExecutor WAL_FLUSH_THREAD = ThreadUtil.makeSingleDaemonThreadPool("Abstract Repo WAL Flush");
	private static final AtomicBoolean FLUSH_THREAD_QUEUED = new AtomicBoolean(false);
	
	
	private final String connectionString;
	private final Connection connection;
	
	public final String databaseType;
	public final File databaseFile;
	
	public final Class<? extends TDTO> dtoClass;
	
	protected final KeyedLockContainer<TKey> saveLockContainer = new KeyedLockContainer<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** @throws SQLException if the repo is unable to access the database or has trouble updating said database. */
	public AbstractDhRepo(String databaseType, File databaseFile, Class<? extends TDTO> dtoClass) throws SQLException
	{
		this.databaseType = databaseType;
		this.databaseFile = databaseFile;
		this.dtoClass = dtoClass;
		
		
		try
		{
			// needed by Forge to load the Java database connection
			Class.forName("org.sqlite.JDBC");	
		}
		catch (ClassNotFoundException e)
		{
			throw new RuntimeException(e);
		}
		
		
		
		//==========================//
		// database file validation //
		//==========================//
		
		// check that the database file exists
		if (!databaseFile.exists())
		{
			// check that the parent folder exists
			File parentFolder = databaseFile.getParentFile();
			if (parentFolder != null && !parentFolder.exists())
			{
				if (!parentFolder.mkdirs())
				{
					throw new RuntimeException("Unable to create the necessary parent folders for the database file at location ["+databaseFile.getPath()+"].");
				}
			}
			
			if (!databaseFile.exists())
			{
				try
				{
					boolean fileCreated = databaseFile.createNewFile();
				}
				catch (IOException e)
				{
					throw new RuntimeException("Unable to create database file at location ["+databaseFile.getPath()+"] due to error: ["+e.getMessage()+"]", e);
				}
			}
		}
		
		if (!databaseFile.canRead())
		{
			throw new RuntimeException("Unable to read database file at location ["+databaseFile.getPath()+"], please make sure the folder and file has the correct permissions.");
		}
		if (!databaseFile.canWrite())
		{
			throw new RuntimeException("Unable to write database file at location ["+databaseFile.getPath()+"], please make sure the folder and file aren't set to read-only.");
		}
		
		
		
		//==================//
		// connection setup //
		//==================//
		
		// get or create the connection,
		// reusing existing connections reduces the chance of locking the database during trivial queries
		this.connectionString = this.databaseType+":"+this.databaseFile.getPath();
		
		
		this.connection = CONNECTIONS_BY_CONNECTION_STRING.computeIfAbsent(this.connectionString, (connectionString) ->
			{
				try
				{
					return DriverManager.getConnection(connectionString);
				}
				catch (SQLException e)
				{
					LOGGER.error("Unable to connect to database with the connection string: ["+connectionString+"]");
					return null;
				}
			});
		if (this.connection == null)
		{
			throw new SQLException("Unable to get repo with connection string ["+this.connectionString+"]");
		}
		
		ACTIVE_CONNECTION_STRINGS_BY_REPO.put(this, this.connectionString);
		
		DatabaseUpdater.runAutoUpdateScripts(this);
	}
	
	
	
	//===============//
	// high level DB //
	//===============//
	
	public TDTO getByKey(TKey primaryKey)
	{
		try(PreparedStatement statement = this.createSelectStatementByKey(primaryKey);
			ResultSet resultSet = this.query(statement))
		{
			if (resultSet != null && resultSet.next())
			{
				return this.convertResultSetToDto(resultSet);
			}
			else 
			{
				return null;
			}
		}
		catch (SQLException | IOException e)
		{
			if (e instanceof SQLException 
				&& DbConnectionClosedException.isClosedException((SQLException)e))
			{
				//LOGGER.warn("Attempted to get ["+this.dtoClass.getSimpleName()+"] with primary key ["+primaryKey+"] on closed repo ["+this.connectionString+"].");	
			}
			else
			{
				LOGGER.warn("Unexpected issue deserializing DTO ["+this.dtoClass.getSimpleName()+"] with primary key ["+primaryKey+"]. Error: ["+e.getMessage()+"].", e);	
			}
			return null;
		}
	}
	
	
	public void save(TDTO dto)
	{
		// a lock is necessary to prevent concurrent modification between
		// existsWithKey and insert/update,
		// otherwise another thread might cause the insert/update to fail.
		ReentrantLock saveLock = this.saveLockContainer.getLockForPos(dto.getKey());
		
		try
		{
			saveLock.lock();
			
			if (this.existsWithKey(dto.getKey()))
			{
				this.update(dto);
			}
			else
			{
				this.insert(dto);
			}
		}
		finally
		{
			saveLock.unlock();
			//this.tryTriggerWalFlush();
		}
	}
	private void insert(TDTO dto) 
	{
		try(PreparedStatement statement = this.createInsertStatement(dto);
			ResultSet result = this.query(statement))
		{
		}
		catch (DbConnectionClosedException ignored) 
		{
			//LOGGER.warn("Attempted to insert ["+this.dtoClass.getSimpleName()+"] with primary key ["+(dto != null ? dto.getKeyDisplayString() : "NULL")+"] on closed repo ["+this.connectionString+"].");
		}
		catch (SQLException e)
		{
			String message = "Unexpected DTO insert error: ["+e.getMessage()+"].";
			LOGGER.error(message);
			throw new RuntimeException(message, e);
		}
	}
	private void update(TDTO dto)
	{
		try(PreparedStatement statement = this.createUpdateStatement(dto);
			ResultSet result = this.query(statement))
		{
			
		}
		catch (DbConnectionClosedException e)
		{
			//LOGGER.warn("Attempted to update ["+this.dtoClass.getSimpleName()+"] with primary key ["+(dto != null ? dto.getKeyDisplayString() : "NULL")+"] on closed repo ["+this.connectionString+"].");
		}
		catch (SQLException e)
		{
			String message = "Unexpected DTO update error: ["+e.getMessage()+"].";
			LOGGER.error(message);
			throw new RuntimeException(message, e);
		}
	}
	
	
	public void delete(TDTO dto) { this.deleteWithKey(dto.getKey()); }
	public void deleteWithKey(TKey key) 
	{
		try (PreparedStatement statement = this.createDeleteStatementByKey(key);
			ResultSet result = this.query(statement))
		{
			
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
		//finally
		//{
		//	this.tryTriggerWalFlush();
		//}
	}
	
	/** With great power comes great responsibility... */
	public void deleteAll() 
	{ 
		String sql = "DELETE FROM " + this.getTableName();
		try (PreparedStatement statement = this.createPreparedStatement(sql);
			ResultSet result = this.query(statement))
		{
			
		}
		catch (SQLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	
	public boolean exists(TDTO dto) { return this.existsWithKey(dto.getKey()); }
	public boolean existsWithKey(TKey key) 
	{
		try
		{
			try (PreparedStatement statement = this.createExistsStatementByKey(key);
				ResultSet result = this.query(statement))
			{
				return result != null && result.getInt("existingCount") != 0;
			}
		}
		catch (SQLException e)
		{
			return false;
		}
	}
	
	
	
	//==============//
	// low level DB //
	//==============//
	
	/** 
	 * This can only run 1 command at a time. <br><br>
	 * 
	 * Note: {@link AbstractDhRepo#query(PreparedStatement)} with a {@link PreparedStatement}
	 * should be used if the query will be run often.
	 * This reduces GC pressure due to the {@link String} and {@link Map} allocation cost.
	 */
	@Nullable
	public Map<String, Object> queryDictionaryFirst(String sql) 
	{
		try
		{
			List<Map<String, Object>> objectList = this.queryDictionary(sql);
			return !objectList.isEmpty() ? objectList.get(0) : null;
		}
		catch (DbConnectionClosedException e)
		{
			return null;
		}
	}
	/**
	 * This can only run 1 command at a time. <br><br>
	 * 
	 * Note: {@link AbstractDhRepo#query(PreparedStatement)} with a {@link PreparedStatement}
	 * should be used if the query will be run often.
	 * This reduces GC pressure due to the {@link String} and {@link Map} allocation cost.
	 */
	private List<Map<String, Object>> queryDictionary(String sql) throws RuntimeException, DbConnectionClosedException
	{
		try (Statement statement = this.connection.createStatement())
		{
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			
			// Note: this can only handle 1 command at a time
			boolean resultSetPresent = statement.execute(sql);
			try (ResultSet resultSet = statement.getResultSet())
			{
				return this.convertResultSetToDictionaryList(resultSet, resultSetPresent);
			}
		}
		catch(SQLException e)
		{
			// SQL exceptions generally only happen when something is wrong with 
			// the database or the query and should cause the system to blow up to notify the developer
			
			if (DbConnectionClosedException.isClosedException(e))
			{
				throw new DbConnectionClosedException(e);
			}
			else
			{
				String message = "Unexpected Query error: [" + e.getMessage() + "], for script: [" + sql + "].";
				LOGGER.error(message, e);
				throw new RuntimeException(message, e);
			}
		}
	}
	
	
	
	/** 
	 * Warning: both the returned {@link ResultSet} and incoming {@link PreparedStatement} 
	 * must be wrapped in a try-resource block to prevent memory
	 * leaks and issues with the DB becoming locked.
	 */
	@Nullable
	public ResultSet query(@Nullable PreparedStatement statement) throws RuntimeException
	{
		// This is done so we don't have to add "if null" checks everywhere.
		// Normally this should only happen once the DB has been closed.
		if (statement == null)
		{
			return null;
		}
		
		
		try
		{
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			
			// Note: this can only handle 1 command at a time
			boolean resultSetPresent = statement.execute();
			if (resultSetPresent)
			{
				return statement.getResultSet();
			}
			else
			{
				return null;
			}
		}
		catch(SQLException e)
		{
			// SQL exceptions generally only happen when something is wrong with 
			// the database or the query and should cause the system to blow up to notify the developer
			
			if (DbConnectionClosedException.isClosedException(e))
			{
				return null;
			}
			else
			{
				String message = "Unexpected Query error: [" + e.getMessage() + "], for prepared statement: [" + statement + "].";
				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}
		}
	}
	
	
	
	/** 
	 * @return Null if the database was closed
	 * @throws RuntimeException if there was a problem with the given SQL string
	 */
	@Nullable
	public PreparedStatement createPreparedStatement(String sql) throws RuntimeException
	{
		try
		{
			PreparedStatement statement = this.connection.prepareStatement(sql);
			statement.setQueryTimeout(TIMEOUT_SECONDS);
			return statement;
		}
		catch(SQLException e)
		{
			if (DbConnectionClosedException.isClosedException(e))
			{
				return null;
			}
			else
			{
				// SQL exceptions generally only happen when something is wrong with 
				// the database or the query and should cause the system to blow up to notify the developer
				
				String message = "Unexpected error: [" + e.getMessage() + "], preparing statement: [" + sql + "].";
				LOGGER.error(message);
				throw new RuntimeException(message, e);
			}
		}
	}
	
	
	
	//=============//
	// connections //
	//=============//
	
	public Connection getConnection() { return this.connection; }
	
	public boolean isConnected() 
	{
		try
		{
			return this.connection != null && this.connection.isClosed();
		}
		catch (SQLException e)
		{
			return false;
		}
	}
	
	/** can be used to make sure everything is closed when the world closes */
	public static void closeAllConnections()
	{
		LOGGER.info("Closing all ["+ACTIVE_CONNECTION_STRINGS_BY_REPO.size()+"] database connections...");
		for (String connectionString : ACTIVE_CONNECTION_STRINGS_BY_REPO.values())
		{
			try
			{
				Connection connection = CONNECTIONS_BY_CONNECTION_STRING.remove(connectionString);
				if (connection != null)
				{
					if (!connection.isClosed())
					{
						LOGGER.info("Closing database connection: [" + connectionString + "]");
						connection.close();
					}
					else
					{
						LOGGER.warn("Attempting to close already closed database connection: [" + connectionString + "]");
					}
				}
			}
			catch(SQLException e)
			{
				// connection close failed.
				LOGGER.error("Unable to close the connection ["+connectionString+"], error: ["+e.getMessage()+"]");
			}
		}
	}
	
	@Override
	public void close()
	{
		try
		{
			// mark this repo as deactivated
			ACTIVE_CONNECTION_STRINGS_BY_REPO.remove(this);
			
			// check if any other repos are using this connection
			if (!ACTIVE_CONNECTION_STRINGS_BY_REPO.containsValue(this.connectionString)) // not a fast operation, but we shouldn't have more than 10 repos active at a time, so it shouldn't be a problem
			{
				if(this.connection != null)
				{
					CONNECTIONS_BY_CONNECTION_STRING.remove(this.connectionString);
					
					if (!this.connection.isClosed())
					{
						LOGGER.info("Closing database connection: [" + this.connectionString + "]...");
						this.connection.close();
						LOGGER.info("Finished closing database connection: [" + this.connectionString + "]");
					}
					else
					{
						LOGGER.warn("Attempting to close already closed database connection: [" + this.connectionString + "]");
					}
				}
				ACTIVE_CONNECTION_STRINGS_BY_REPO.remove(this);
			}
		}
		catch(SQLException e)
		{
			// connection close failed.
			LOGGER.error("Unable to close the connection ["+this.connectionString+"], error: ["+e.getMessage()+"]");
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private List<Map<String, Object>> convertResultSetToDictionaryList(ResultSet resultSet, boolean resultSetPresent) throws SQLException
	{
		if (resultSetPresent)
		{
			List<Map<String, Object>> resultList = convertResultSetToDictionaryList(resultSet);
			resultSet.close();
			return resultList;
		}
		else
		{
			if (resultSet != null)
			{
				resultSet.close();
			}
			
			return new ArrayList<>();
		}
	}
	private static List<Map<String, Object>> convertResultSetToDictionaryList(ResultSet resultSet) throws SQLException
	{
		List<Map<String, Object>> list = new ArrayList<>();
		
		ResultSetMetaData resultMetaData = resultSet.getMetaData();
		int resultColumnCount = resultMetaData.getColumnCount();
		
		while (resultSet.next())
		{
			HashMap<String, Object> object = new HashMap<>();
			for (int columnIndex = 1; columnIndex <= resultColumnCount; columnIndex++) // column indices start at 1
			{
				String columnName = resultMetaData.getColumnName(columnIndex);
				if (columnName == null || columnName.isEmpty())
				{
					throw new RuntimeException("SQL result set is missing a column name for column ["+resultMetaData.getTableName(columnIndex)+"."+columnIndex+"].");
				}
				
				
				// some values need explicit conversion
				// Example: Long values that are within the bounds of an int would automatically be incorrectly returned as "Integer" objects
				String columnType = resultMetaData.getColumnTypeName(columnIndex).toUpperCase();
				Object columnValue;
				switch (columnType)
				{
					case "BIGINT":
						columnValue = resultSet.getLong(columnIndex);
						break;
					case "SMALLINT":
						columnValue = resultSet.getShort(columnIndex);
						break;
					case "TINYINT":
						columnValue = resultSet.getByte(columnIndex);
						break;
					default:
						columnValue = resultSet.getObject(columnIndex);
						break;
				}
				
				
				object.put(columnName, columnValue);
			}
			
			list.add(object);
		}
		
		return list;
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	public abstract String getTableName();
	
	@Nullable
	public abstract TDTO convertResultSetToDto(ResultSet resultSet) throws ClassCastException, IOException, SQLException;
	
	
	
	/** 
	 * should NOT start with WHERE 
	 * Example: TODO 
	 */
	protected abstract String CreateParameterizedWhereString();
	
	protected void setPreparedStatementWhereClause(PreparedStatement statement, TKey key) throws SQLException { this.setPreparedStatementWhereClause(statement, 1, key); }
	protected abstract int setPreparedStatementWhereClause(PreparedStatement statement, int parameterIndex, TKey key) throws SQLException;
	
	
	private String selectSqlTemplate = null;
	public PreparedStatement createSelectStatementByKey(TKey key) throws SQLException
	{
		// create shared template string
		if (this.selectSqlTemplate == null)
		{
			this.selectSqlTemplate = "SELECT * FROM "+this.getTableName() + " WHERE " + this.CreateParameterizedWhereString();
		}
		
		PreparedStatement statement = this.createPreparedStatement(this.selectSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		this.setPreparedStatementWhereClause(statement, key);
		
		return statement;
	}
	
	private String existsSqlTemplate = null;
	public PreparedStatement createExistsStatementByKey(TKey key) throws SQLException
	{
		// create shared template string
		if (this.existsSqlTemplate == null)
		{
			this.existsSqlTemplate = "SELECT EXISTS(SELECT 1 FROM "+this.getTableName()+" WHERE "+this.CreateParameterizedWhereString()+") as 'existingCount'";
		}
		
		PreparedStatement statement = this.createPreparedStatement(this.existsSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		this.setPreparedStatementWhereClause(statement, key);
		
		return statement;
	}
	
	private String deleteSqlTemplate = null;
	public PreparedStatement createDeleteStatementByKey(TKey key) throws SQLException
	{
		// create shared template string
		if (this.deleteSqlTemplate == null)
		{
			this.deleteSqlTemplate = "DELETE FROM "+this.getTableName()+" WHERE " + this.CreateParameterizedWhereString();
		}
		
		PreparedStatement statement = this.createPreparedStatement(this.deleteSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		this.setPreparedStatementWhereClause(statement, key);
		
		return statement;
	}
	
	
	
	@Nullable
	public abstract PreparedStatement createInsertStatement(TDTO dto) throws SQLException;
	@Nullable
	public abstract PreparedStatement createUpdateStatement(TDTO dto) throws SQLException;
	
	
	
	
}
