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

package com.seibel.distanthorizons.core.config.eventHandlers.presets;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorProgressDisplayLocation;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;

public class QuickShowWorldGenProgressConfigEventHandler
{
	public static QuickShowWorldGenProgressConfigEventHandler INSTANCE = new QuickShowWorldGenProgressConfigEventHandler();
	
	private final ConfigChangeListener<Boolean> quickChangeListener;
	private final ConfigChangeListener<EDhApiDistantGeneratorProgressDisplayLocation> fullChangeListener;
	
	
	
	/** private since we only ever need one handler at a time */
	private QuickShowWorldGenProgressConfigEventHandler()
	{
		this.quickChangeListener = new ConfigChangeListener<>(Config.Client.quickShowWorldGenProgress, 
				(val) -> 
				{
					Config.Common.WorldGenerator.showGenerationProgress.set(Config.Client.quickShowWorldGenProgress.get() 
							? Config.Common.WorldGenerator.showGenerationProgress.getDefaultValue()
							: EDhApiDistantGeneratorProgressDisplayLocation.DISABLED); 
				});
		this.fullChangeListener = new ConfigChangeListener<>(Config.Common.WorldGenerator.showGenerationProgress, 
				(val) -> 
				{
					Config.Client.quickShowWorldGenProgress.set(Config.Common.WorldGenerator.showGenerationProgress.get() != EDhApiDistantGeneratorProgressDisplayLocation.DISABLED); 
				});
	}
	
	/**
	 * Set the UI only config based on what is set in the file. <br>
	 * This should only be called once.
	 */
	public void setUiOnlyConfigValues()
	{
		boolean showProgress = Config.Common.WorldGenerator.showGenerationProgress.get() != EDhApiDistantGeneratorProgressDisplayLocation.DISABLED;
		Config.Client.quickShowWorldGenProgress.set(showProgress);
	}
	
}
