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

import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigEntryWithPresetOptions;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("FieldCanBeLocal")
public class ThreadPresetConfigEventHandler extends AbstractPresetConfigEventHandler<EDhApiThreadPreset>
{
	public static final ThreadPresetConfigEventHandler INSTANCE = new ThreadPresetConfigEventHandler();
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	public static int getDefaultThreadCount() { return getThreadCountByPercent(0.5); }
	private final ConfigEntryWithPresetOptions<EDhApiThreadPreset, Integer> threadCount = new ConfigEntryWithPresetOptions<>(Config.Common.MultiThreading.numberOfThreads,
			new HashMap<EDhApiThreadPreset, Integer>()
			{{
				this.put(EDhApiThreadPreset.MINIMAL_IMPACT, getThreadCountByPercent(0.1));
				this.put(EDhApiThreadPreset.LOW_IMPACT, getThreadCountByPercent(0.25));
				this.put(EDhApiThreadPreset.BALANCED, getDefaultThreadCount());
				this.put(EDhApiThreadPreset.AGGRESSIVE, getThreadCountByPercent(0.75));
				this.put(EDhApiThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, getThreadCountByPercent(1.0));
			}});
	public static double getDefaultRunTimeRatio() { return 1.0; }
	private final ConfigEntryWithPresetOptions<EDhApiThreadPreset, Double> threadRunTime = new ConfigEntryWithPresetOptions<>(Config.Common.MultiThreading.threadRunTimeRatio,
			new HashMap<EDhApiThreadPreset, Double>()
			{{
				this.put(EDhApiThreadPreset.MINIMAL_IMPACT, 0.5);
				this.put(EDhApiThreadPreset.LOW_IMPACT, 1.0);
				this.put(EDhApiThreadPreset.BALANCED, getDefaultRunTimeRatio());
				this.put(EDhApiThreadPreset.AGGRESSIVE, 1.0);
				this.put(EDhApiThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, 1.0);
			}});
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** private since we only ever need one handler at a time */
	private ThreadPresetConfigEventHandler()
	{
		// add each config used by this preset
		this.configList.add(this.threadCount);
		this.configList.add(this.threadRunTime);
		
		for (ConfigEntryWithPresetOptions<EDhApiThreadPreset, ?> config : this.configList)
		{
			// ignore try-using, the listeners should only ever be added once and should never be removed
			new ConfigChangeListener<>(config.configEntry, (val) -> { this.onConfigValueChanged(); });
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * Pre-computed values for your convenience: <br>
	 * Format: percent: 4coreCpu-8coreCpu-16coreCpu <br><br>
	 * <code>
	 * 0.1: 1-1-2	<br>
	 * 0.2: 1-2-4	<br>
	 * 0.4: 2-4-7	<br>
	 * 0.6: 3-5-10	<br>
	 * 0.8: 4-7-13	<br>
	 * 1.0: 4-8-16	<br>
	 * </code>
	 */
	private static int getThreadCountByPercent(double percent) throws IllegalArgumentException
	{
		if (percent <= 0 || percent > 1)
		{
			throw new IllegalArgumentException("percent must be greater than 0 and less than or equal to 1.");
		}
		
		// this is logical processor count, not physical CPU cores
		int totalProcessorCount = Runtime.getRuntime().availableProcessors();
		int coreCount = (int) Math.ceil(totalProcessorCount * percent);
		return MathUtil.clamp(1, coreCount, totalProcessorCount);
	}
	
	
	
	//==============//
	// enum getters //
	//==============//
	
	@Override
	protected IConfigEntry<EDhApiThreadPreset> getPresetConfigEntry() { return Config.Client.threadPresetSetting; }
	
	@Override
	protected List<EDhApiThreadPreset> getPresetEnumList() { return Arrays.asList(EDhApiThreadPreset.values()); }
	@Override
	protected EDhApiThreadPreset getCustomPresetEnum() { return EDhApiThreadPreset.CUSTOM; }
	
	
	
}
