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

package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.file.ConfigFileHandling;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import com.seibel.distanthorizons.core.config.types.ConfigCategory;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.config.types.ConfigUiLinkedEntry;
import com.seibel.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

/**
 * Indexes and sets everything up for the file handling and gui
 *
 * @author coolGi
 * @author Ran
 * @version 2023-8-26
 */
// Init the config after singletons have been blinded
public class ConfigBase
{
	/** Our own config instance, don't modify unless you are the DH mod */
	public static ConfigBase INSTANCE;
	public ConfigFileHandling configFileINSTANCE;
	
	private final Logger logger;
	public final String modID;
	public final String modName;
	public final int configVersion;
	
	public boolean isLoaded = false;
	
	
	
	/**
	 *      What the config works with
	 * <br> 
	 * <br> {@link Enum}
	 * <br> {@link Boolean}
	 * <br> {@link Byte}
	 * <br> {@link Integer}
	 * <br> {@link Double}
	 * <br> {@link Short}
	 * <br> {@link Long}
	 * <br> {@link Float}
	 * <br> {@link String}
	 * <br> 
	 * <br> // Below, "T" should be a value from above
	 * <br> // Note: This is not checked, so we trust that you are doing the right thing (TODO: Check it)
	 * <br> List<T>
	 * <br> ArrayList<T>
	 * <br> Map<String, T>
	 * <br> HashMap<String, T>
	 */
	public static final List<Class<?>> acceptableInputs = new ArrayList<Class<?>>()
	{{
		add(Boolean.class);
		add(Byte.class);
		add(Integer.class);
		add(Double.class);
		add(Short.class);
		add(Long.class);
		add(Float.class);
		add(String.class);
		
		// TODO[CONFIG]: Check the type of these is valid
		add(List.class);
		add(ArrayList.class);
		add(Map.class);
		add(HashMap.class);
	}};
	
	/** Disables the minimum and maximum of any variable */
	public boolean disableMinMax = false; // Very fun to use, but should always be disabled by default
	public final List<AbstractConfigType<?, ?>> entries = new ArrayList<>();
	
	
	public ConfigBase(String modID, String modName, Class<?> configClass)
	{
		this(modID, modName, configClass, getConfigPath(modName), -1);
	}
	public ConfigBase(String modID, String modName, Class<?> configClass, Path configPath)
	{
		this(modID, modName, configClass, configPath, -1);
	}
	public ConfigBase(String modID, String modName, Class<?> configClass, int configVersion)
	{
		this(modID, modName, configClass, getConfigPath(modName), configVersion);
	}
	
	public ConfigBase(String modID, String modName, Class<?> configClass, Path configPath, int configVersion)
	{
		this.logger = LogManager.getLogger(this.getClass().getSimpleName() + ", " + modID);
		
		this.logger.info("Initialising config for " + modName);
		this.modID = modID;
		this.modName = modName;
		this.configVersion = configVersion;
		
		this.initNestedClass(configClass, ""); // Init root category
		
		// File handling (load from file)
		this.configFileINSTANCE = new ConfigFileHandling(this, configPath);
		this.configFileINSTANCE.loadFromFile();
		
		this.isLoaded = true;
		this.logger.info("Config for " + modName + " initialised");
	}
	
	private void initNestedClass(Class<?> configClass, String category)
	{
		// Put all the entries in entries
		
		for (Field field : configClass.getFields())
		{
			if (AbstractConfigType.class.isAssignableFrom(field.getType()))
			{
				try
				{
					this.entries.add((AbstractConfigType<?, ?>) field.get(field.getType()));
				}
				catch (IllegalAccessException exception)
				{
					this.logger.warn(exception);
				}
				
				AbstractConfigType<?, ?> entry = this.entries.get(this.entries.size() - 1);
				entry.category = category;
				entry.name = field.getName();
				entry.configBase = this;
				
				if (ConfigEntry.class.isAssignableFrom(field.getType()))
				{ // If item is type ConfigEntry
					if (!isAcceptableType(entry.getType()))
					{
						this.logger.error("Invalid variable type at [" + (category.isEmpty() ? "" : category + ".") + field.getName() + "].");
						this.logger.error("Type [" + entry.getType() + "] is not one of these types [" + acceptableInputs.toString() + "]");
						this.entries.remove(this.entries.size() - 1); // Delete the entry if it is invalid so the game can still run
					}
				}
				
				if (ConfigCategory.class.isAssignableFrom(field.getType()))
				{ // If it's a category then init the stuff inside it and put it in the category list
					assert entry instanceof ConfigCategory;
					if (((ConfigCategory) entry).getDestination() == null)
						((ConfigCategory) entry).destination = entry.getNameWCategory();
					if (entry.get() != null)
					{
						this.initNestedClass(((ConfigCategory) entry).get(), ((ConfigCategory) entry).getDestination());
					}
				}
			}
		}
	}
	
	private static boolean isAcceptableType(Class<?> Clazz)
	{
		if (Clazz.isEnum())
			return true;
		return acceptableInputs.contains(Clazz);
	}
	
	
	/** Gets the default config path given a mod name */
	public static Path getConfigPath(String modName)
	{
		return SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class)
				.getInstallationDirectory().toPath().resolve("config").resolve(modName + ".toml");
	}
	
	
	/**
	 * Used for checking that all the lang files for the config exist
	 *
	 * @param onlyShowNew If disabled then it would basically remake the config lang
	 * @param checkEnums Checks if all the lang for the enum's exist
	 */
	// This is just to re-format the lang or check if there is something in the lang that is missing
	@SuppressWarnings("unchecked")
	public String generateLang(boolean onlyShowNew, boolean checkEnums)
	{
		ILangWrapper langWrapper = SingletonInjector.INSTANCE.get(ILangWrapper.class);
		List<Class<? extends Enum<?>>> enumList = new ArrayList<>();
		
		String generatedLang = "";
		
		String starter = "  \"";
		String separator = "\":\n    \"";
		String ending = "\",\n";
		
		for (AbstractConfigType<?, ?> entry : this.entries)
		{
			String entryPrefix = "lod.config." + entry.getNameWCategory();
			
			if (checkEnums && entry.getType().isEnum() && !enumList.contains(entry.getType()))
			{ // Put it in an enum list to work with at the end
				enumList.add((Class<? extends Enum<?>>) entry.getType());
			}
			if (!onlyShowNew || langWrapper.langExists(entryPrefix))
			{
				if (!ConfigUiLinkedEntry.class.isAssignableFrom(entry.getClass()))
				{ // If it is a linked item, dont generate the base lang
					generatedLang += starter
							+ entryPrefix
							+ separator
							+ langWrapper.getLang(entryPrefix)
							+ ending
					;
				}
				// Adds tooltips
				if (langWrapper.langExists(entryPrefix + ".@tooltip"))
				{
					generatedLang += starter
							+ entryPrefix + ".@tooltip"
							+ separator
							+ langWrapper.getLang(entryPrefix + ".@tooltip")
							.replaceAll("\n", "\\\\n")
							.replaceAll("\"", "\\\\\"")
							+ ending
					;
				}
			}
		}
		if (!enumList.isEmpty())
		{
			generatedLang += "\n"; // Separate the main lang with the enum's
			
			for (Class<? extends Enum> anEnum : enumList)
			{
				for (Object enumStr : new ArrayList<>(EnumSet.allOf(anEnum)))
				{
					String enumPrefix = "lod.config.enum." + anEnum.getSimpleName() + "." + enumStr.toString();
					
					if (!onlyShowNew || langWrapper.langExists(enumPrefix))
					{
						generatedLang += starter
								+ enumPrefix
								+ separator
								+ langWrapper.getLang(enumPrefix)
								+ ending
						;
					}
				}
			}
		}
		
		return generatedLang;
	}
	
}
