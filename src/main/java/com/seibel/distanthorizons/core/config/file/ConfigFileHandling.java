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

package com.seibel.distanthorizons.core.config.file;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles reading and writing config files.
 *
 * @author coolGi
 * @version 2023-8-26
 */
public class ConfigFileHandling
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftSharedWrapper MC_SHARED = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);
	
	
	public final ConfigBase configBase;
	public final Path configPath;
	
	private final Logger logger;
	
	/** This is the object for night-config */
	private final CommentedFileConfig nightConfig;
	
	/** prevents readers/writers from overlapping and causing the config file from being duplicated or corrupted */
	private final ReentrantLock readWriteLock = new ReentrantLock();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ConfigFileHandling(ConfigBase configBase, Path configPath)
	{
		this.logger = LogManager.getLogger(this.getClass().getSimpleName() + ", " + configBase.modID);
		this.configBase = configBase;
		this.configPath = configPath;
		
		this.nightConfig = CommentedFileConfig.builder(this.configPath.toFile()).build();
	}
	
	
	
	
	/** Saves the entire config to the file */
	public void saveToFile() { this.saveToFile(this.nightConfig); }
	/** Saves the entire config to the file */
	public void saveToFile(CommentedFileConfig nightConfig)
	{
		try
		{
			this.readWriteLock.lock();
			
			
			
			if (!Files.exists(this.configPath)) // Try to check if the config exists
			{
				reCreateFile(this.configPath);
			}
			
			
			this.loadNightConfig(nightConfig);
			
			
			for (AbstractConfigType<?, ?> entry : this.configBase.entries)
			{
				if (ConfigEntry.class.isAssignableFrom(entry.getClass()))
				{
					this.createComment((ConfigEntry<?>) entry, nightConfig);
					this.saveEntry((ConfigEntry<?>) entry, nightConfig);
				}
			}
			
			
			try
			{
				nightConfig.save();
			}
			catch (Exception e)
			{
				// If it fails to save, crash game
				SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Failed to save config at [" + this.configPath + "]", e);
			}
			
		}
		finally
		{
			this.readWriteLock.unlock();
		}
	}
	
	/**
	 * Loads the entire config from the file
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadFromFile()
	{
		try
		{
			this.readWriteLock.lock();
			
			int currentCfgVersion = this.configBase.configVersion;
			try
			{
				// Dont load the real `this.nightConfig`, instead create a tempoary one
				CommentedFileConfig tmpNightConfig = CommentedFileConfig.builder(this.configPath.toFile()).build();
				tmpNightConfig.load();
				// Attempt to get the version number
				currentCfgVersion = (Integer) tmpNightConfig.get("_version");
				tmpNightConfig.close();
			}
			catch (Exception ignored) { }
			
			if (currentCfgVersion == this.configBase.configVersion)
			{
				// handle normally
			}
			else if (currentCfgVersion > this.configBase.configVersion)
			{
				this.logger.warn("Found config version [" + currentCfgVersion + "] which is newer than current mods config version of [" + this.configBase.configVersion + "]. You may have downgraded the mod and items may have been moved, you have been warned");
			}
			else // if (currentCfgVersion < configBase.configVersion)
			{
				this.logger.warn(this.configBase.modName + " config is of an older version, currently there is no config updater... so resetting config");
				try
				{
					Files.delete(this.configPath);
				}
				catch (Exception e)
				{
					this.logger.error(e);
				}
			}
			
			this.loadFromFile(this.nightConfig);
			this.nightConfig.set("_version", this.configBase.configVersion);
		}
		finally
		{
			this.readWriteLock.unlock();
		}
	}
	/**
	 * Loads the entire config from the file
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	private void loadFromFile(CommentedFileConfig nightConfig)
	{
		// Attempt to load the file and if it fails then save config to file
		if (Files.exists(this.configPath))
		{
			this.loadNightConfig(nightConfig);
		}
		else
		{
			reCreateFile(this.configPath);
		}
		
		
		// Load all the entries
		for (AbstractConfigType<?, ?> entry : this.configBase.entries)
		{
			if (ConfigEntry.class.isAssignableFrom(entry.getClass())
				&& entry.getAppearance().showInFile)
			{
				this.createComment((ConfigEntry<?>) entry, nightConfig);
				this.loadEntry((ConfigEntry<?>) entry, nightConfig);
			}
		}
		
		
		try
		{
			nightConfig.save();
		}
		catch (Exception e)
		{
			// If it fails to save, crash game
			SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Failed to save config at [" + this.configPath + "]", e);
		}
	}
	
	
	
	
	// Save an entry when only given the entry
	public void saveEntry(ConfigEntry<?> entry)
	{
		this.saveEntry(entry, this.nightConfig);
		this.nightConfig.save();
	}
	/** Save an entry */
	public void saveEntry(ConfigEntry<?> entry, CommentedFileConfig workConfig)
	{
		if (!entry.getAppearance().showInFile)
		{
			return;
		}
		else if (entry.getTrueValue() == null)
		{
			// TODO when can this happen?
			throw new IllegalArgumentException("Entry [" + entry.getNameWCategory() + "] is null, this may be a problem with [" + this.configBase.modName + "]. Please contact the authors.");
		}
		
		workConfig.set(entry.getNameWCategory(), ConfigTypeConverters.attemptToConvertToString(entry.getType(), entry.getTrueValue()));
	}
	
	/** Loads an entry when only given the entry */
	public void loadEntry(ConfigEntry<?> entry) { this.loadEntry(entry, this.nightConfig); }
	/** Loads an entry */
	@SuppressWarnings("unchecked")
	public <T> void loadEntry(ConfigEntry<T> entry, CommentedFileConfig nightConfig)
	{
		if (!entry.getAppearance().showInFile)
			return;
		
		if (!nightConfig.contains(entry.getNameWCategory()))
		{
			this.saveEntry(entry, nightConfig);
			return;
		}
		
		
		try
		{
			if (entry.getType().isEnum())
			{
				entry.pureSet((T) (nightConfig.getEnum(entry.getNameWCategory(), (Class<? extends Enum>) entry.getType())));
				return;
			}
			
			// try converting the value if necessary
			Class<?> expectedValueClass = entry.getType();
			Object value = nightConfig.get(entry.getNameWCategory());
			Object convertedValue = ConfigTypeConverters.attemptToConvertFromString(expectedValueClass, value);
			if (!convertedValue.getClass().equals(expectedValueClass))
			{
				this.logger.error("Unable to convert config value ["+value+"] from ["+(value != null ? value.getClass() : "NULL")+"] to ["+expectedValueClass+"] for config ["+entry.name+"], " +
						"the default config value will be used instead ["+entry.getDefaultValue()+"]. " +
						"Make sure a converter is defined in ["+ConfigTypeConverters.class.getSimpleName()+"].");
				convertedValue = entry.getDefaultValue();
			}
			entry.pureSet((T) convertedValue);
			
			if (entry.getTrueValue() == null) 
			{
				this.logger.warn("Entry [" + entry.getNameWCategory() + "] returned as null from the config. Using default value.");
				entry.pureSet(entry.getDefaultValue());
			}
		}
		catch (Exception e)
		{
//                e.printStackTrace();
			this.logger.warn("Entry [" + entry.getNameWCategory() + "] had an invalid value when loading the config. Using default value.");
			entry.pureSet(entry.getDefaultValue());
		}
	}
	
	// Creates the comment for an entry when only given the entry
	public void createComment(ConfigEntry<?> entry) { this.createComment(entry, this.nightConfig); }
	// Creates a comment for an entry
	public void createComment(ConfigEntry<?> entry, CommentedFileConfig nightConfig)
	{
		if (!entry.getAppearance().showInFile 
			|| entry.getComment() == null)
		{
			return;
		}
		
		
		
		String comment = entry.getComment().replaceAll("\n", "\n ").trim();
		// the new line makes it easier to read and separate configs
		// the space makes sure the first word of a comment isn't directly in line with the "#" 
		comment = "\n " + comment;
		nightConfig.setComment(entry.getNameWCategory(), comment);
	}
	
	
	
	
	
	/**
	 * Uses {@link ConfigFileHandling#nightConfig} to do {@link CommentedFileConfig#load()} but with error checking
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadNightConfig()
	{
		loadNightConfig(this.nightConfig);
	}
	/**
	 * Does {@link CommentedFileConfig#load()} but with error checking
	 *
	 * @apiNote This overwrites any value currently stored in the config
	 */
	public void loadNightConfig(CommentedFileConfig nightConfig)
	{
		try
		{
			try
			{
				if (!Files.exists(this.configPath))
					Files.createFile(this.configPath);
				nightConfig.load();
			}
			catch (Exception e)
			{
				this.logger.warn("Loading file failed because of this expectation:\n" + e);
				
				reCreateFile(this.configPath);
				
				nightConfig.load();
			}
		}
		catch (Exception ex)
		{
			System.out.println("Creating file failed");
			this.logger.error(ex);
			SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class).crashMinecraft("Loading file and resetting config file failed at path [" + configPath + "]. Please check the file is ok and you have the permissions", ex);
		}
	}
	
	
	
	public static void reCreateFile(Path path)
	{
		try
		{
			Files.deleteIfExists(path);
			
			if (!path.getParent().toFile().exists())
			{
				Files.createDirectory(path.getParent());
			}
			Files.createFile(path);
		}
		catch (IOException e)
		{
			LOGGER.error("Unable to recreate config file, error: ["+e.getMessage()+"].", e);
		}
	}
	
	
	
}
