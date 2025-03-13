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

package com.seibel.distanthorizons.core.sql;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.sql.dto.IBaseDTO;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

/** Handles both initial setup and updating of the sql databases. */
public class DatabaseUpdater
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final String SCHEMA_TABLE_NAME = "Schema";
	/** Since java can only run one sql query at a time this string is used to split up our scripts into individual queries. */
	public static final String UPDATE_SCRIPT_BATCH_SEPARATOR = "--batch--";
	/** 
	 * If this comment is present anywhere in the auto update script, then transactions won't be used. <br>
	 * This is necessary for some commands that will auto-commit after running, IE: "PRAGMA journal_mode = TRUNCATE"
	 */
	public static final String UPDATE_SCRIPT_NO_TRANSACTION_FLAG = "--No Transactions--";
	
	private static final String SQL_SCRIPT_RESOURCE_FOLDER = "sqlScripts/";
	/** 
	 * Unfortunately dynamically pulling in resource files is very unstable in Java so we need a file that lists all the scripts to expect. <br>
	 * (If anyone has a good way to automatically pull all resource files ending in `.sql` instead, please replace the existing code.)
	 */
	private static final String SQL_SCRIPT_LIST_FILE = SQL_SCRIPT_RESOURCE_FOLDER+"scriptList.txt";
	
	
	
	//================//
	// script running //
	//================//
	
	public static <TKey, TDTO extends IBaseDTO<TKey>> void runAutoUpdateScripts(AbstractDhRepo<TKey, TDTO> repo) throws SQLException
	{
		// get the resource scripts
		ArrayList<SqlScript> scriptList;
		try
		{
			scriptList = getAutoUpdateScripts();
		}
		catch (IOException e)
		{
			LOGGER.error("Get auto update SQL scripts failed. Error: " + e.getMessage(), e);
			return;
		}
		
		
		
		// create the base update table if necessary
		Map<String, Object> schemaTableExistsResult = repo.queryDictionaryFirst("SELECT COUNT(name) as 'tableCount' FROM sqlite_master WHERE type='table' AND name='"+SCHEMA_TABLE_NAME+"';");
		if (schemaTableExistsResult == null || (int) schemaTableExistsResult.get("tableCount") == 0)
		{
			// This table should never be modified, however if for some reason that needs to happen, additional logic will need to be added to migrate over the old data
			String createBaseSchemaTable =
					"CREATE TABLE "+SCHEMA_TABLE_NAME+" ( \n" +
					"    SchemaVersionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, \n" +
					"    ScriptName TEXT NOT NULL UNIQUE, \n" +
					"    AppliedDateTime DATETIME NOT NULL default CURRENT_TIMESTAMP \n" + // shows time in UTC
					")";
			repo.queryDictionaryFirst(createBaseSchemaTable);
		}
		
		
		// attempt to run any new update scripts
		for (SqlScript resource : scriptList)
		{
			Map<String, Object> scriptAlreadyRunResult = repo.queryDictionaryFirst("SELECT EXISTS(SELECT 1 FROM "+SCHEMA_TABLE_NAME+" WHERE ScriptName='"+resource.name+"') as 'existingCount';");
			if (scriptAlreadyRunResult != null && (int) scriptAlreadyRunResult.get("existingCount") == 0)
			{
				LOGGER.info("Running SQL update script: ["+resource.name+"], for repo: ["+repo.databaseFile +"]");
				
				
				int sqlIndex = 0;
				try
				{
					// split up each individual statement so Java can handle the script as a whole
					String[] fileUpdateSqlArray = resource.queryString.split(UPDATE_SCRIPT_BATCH_SEPARATOR);
					
					boolean transactScript = !resource.queryString.contains(UPDATE_SCRIPT_NO_TRANSACTION_FLAG);
					
					
					
					Connection connection = repo.getConnection();
					connection.setAutoCommit(!transactScript);
					
					try (Statement statement = connection.createStatement())
					{
						statement.setQueryTimeout(AbstractDhRepo.TIMEOUT_SECONDS);
						
						// adding the scripts to a batched statement allows them to execute together and rollback together if there are any issues
						for (String updateSql : fileUpdateSqlArray)
						{
							statement.execute(updateSql);
						}
						
						if (transactScript)
						{
							connection.commit();
						}
					}
					catch (SQLException e)
					{
						LOGGER.error("Unexpected SQL Error: ["+e.getMessage()+"] \n" +
							"returned for auto update script: [" + resource.name + "], \n" +
							"query: [" + fileUpdateSqlArray[sqlIndex] + "]. \n" +
							"Changes should have been rolled back.",
							new SQLException()
						);
						
						if (transactScript)
						{
							connection.rollback();
						}
						throw e;
					}
					
					if (transactScript)
					{ 
						// revert to default setting
						connection.setAutoCommit(true);
					}
				}
				catch (RuntimeException e)
				{
					// updating needs to stop to prevent data corruption
					LOGGER.error("Unexpected error running database update script ["+resource.name+"] on database ["+repo.databaseFile +"], stopping database update. Database reading/writing may fail if you continue. \n" +
							"Error: ["+e.getMessage()+"]. \n" +
							"Sql Script:["+resource.queryString+"]", e);
					throw e;
				}
				
				
				// record the successfully run script
				repo.queryDictionaryFirst("INSERT INTO "+SCHEMA_TABLE_NAME+" (ScriptName) VALUES('"+resource.name+"');");
			}
		}
	}
	
	
	
	//===============//
	// file handling //
	//===============//
	
	/** @throws NullPointerException if any of the script files failed to be read. */
	private static ArrayList<SqlScript> getAutoUpdateScripts() throws NullPointerException, IOException
	{
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		
		
		// get the script list
		String scriptListString;
		try (InputStream scriptListInputStream = loader.getResourceAsStream(SQL_SCRIPT_LIST_FILE))
		{
			if (scriptListInputStream == null)
			{
				throw new NullPointerException("Failed to find the SQL Script list file [" + SQL_SCRIPT_LIST_FILE + "], no auto update scripts can be run.");
			}
			
			try (Scanner scanner = new Scanner(scriptListInputStream).useDelimiter("\\A"))
			{
				scriptListString = scanner.hasNext() ? scanner.next() : "";
			}
		}
		
		
		
		// get each script
		ArrayList<SqlScript> scriptList = new ArrayList<>();
		String[] sqlScriptNames = scriptListString.split("\n");
		for (String scriptName : sqlScriptNames)
		{
			scriptName = scriptName.trim();
			if (scriptName.isEmpty())
			{
				// ignore any empty lines
				continue;
			}
			scriptName = SQL_SCRIPT_RESOURCE_FOLDER + scriptName.trim();
			
			// get the script's content
			try(InputStream scriptInputStream = loader.getResourceAsStream(scriptName))
			{
				if (scriptInputStream == null)
				{
					throw new NullPointerException("Failed to find the SQL Script file [" + scriptName + "], no auto update scripts can be run.");
				}
				
				try (Scanner fileScanner = new Scanner(scriptInputStream).useDelimiter("\\A"))
				{
					scriptListString = fileScanner.hasNext() ? fileScanner.next() : "";
				}
				
				scriptList.add(new SqlScript(scriptName, scriptListString));
			}
		}
		
		return scriptList;
	}
	
	
	
	//====================//
	// startup validation //
	//====================//
	
	/** 
	 * Can be used to confirm the auto update scripts are in their expected folder. 
	 * Will throw an exception if the scripts are missing or malformed.
	 */
	public static int getAutoUpdateScriptCount() throws NullPointerException, IOException { return getAutoUpdateScripts().size(); }
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class SqlScript
	{
		public String name;
		public String queryString;
		
		public SqlScript(String name, String queryString)
		{
			this.name = name;
			this.queryString = queryString;
		}
	}
	
}
