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

import com.seibel.distanthorizons.core.config.types.ConfigEntry;

import java.util.HashMap;
import java.util.HashSet;

public class ConfigEntryWithPresetOptions<TQuickEnum, TConfig>
{
	public final ConfigEntry<TConfig> configEntry;
	
	private final HashMap<TQuickEnum, TConfig> configOptionByQualityOption;
	
	
	
	public ConfigEntryWithPresetOptions(ConfigEntry<TConfig> configEntry, HashMap<TQuickEnum, TConfig> configOptionByQualityOption)
	{
		this.configEntry = configEntry;
		this.configOptionByQualityOption = configOptionByQualityOption;
	}
	
	
	
	public void updateConfigEntry(TQuickEnum quickQuality)
	{
		TConfig newValue = this.configOptionByQualityOption.get(quickQuality);
		this.configEntry.set(newValue);
	}
	
	public HashSet<TQuickEnum> getPossibleQualitiesFromCurrentOptionValue()
	{
		TConfig inputOptionValue = this.configEntry.get();
		HashSet<TQuickEnum> possibleQualities = new HashSet<>();
		
		for (TQuickEnum key : this.configOptionByQualityOption.keySet())
		{
			TConfig optionValue = this.configOptionByQualityOption.get(key);
			if (optionValue.equals(inputOptionValue))
			{
				possibleQualities.add(key);
			}
		}
		
		return possibleQualities;
	}
	
}
