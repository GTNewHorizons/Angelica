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

import com.seibel.distanthorizons.api.enums.config.EDhApiHorizontalQuality;
import com.seibel.distanthorizons.api.enums.config.EDhApiMaxHorizontalResolution;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiVerticalQuality;
import com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiQualityPreset;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigEntryWithPresetOptions;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@SuppressWarnings("FieldCanBeLocal")
public class RenderQualityPresetConfigEventHandler extends AbstractPresetConfigEventHandler<EDhApiQualityPreset>
{
	public static final RenderQualityPresetConfigEventHandler INSTANCE = new RenderQualityPresetConfigEventHandler();
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, EDhApiMaxHorizontalResolution> drawResolution = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution,
			new HashMap<EDhApiQualityPreset, EDhApiMaxHorizontalResolution>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, EDhApiMaxHorizontalResolution.TWO_BLOCKS);
				this.put(EDhApiQualityPreset.LOW, EDhApiMaxHorizontalResolution.BLOCK);
				this.put(EDhApiQualityPreset.MEDIUM, EDhApiMaxHorizontalResolution.BLOCK);
				this.put(EDhApiQualityPreset.HIGH, EDhApiMaxHorizontalResolution.BLOCK);
				this.put(EDhApiQualityPreset.EXTREME, EDhApiMaxHorizontalResolution.BLOCK);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, EDhApiVerticalQuality> verticalQuality = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.verticalQuality,
			new HashMap<EDhApiQualityPreset, EDhApiVerticalQuality>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, EDhApiVerticalQuality.HEIGHT_MAP);
				this.put(EDhApiQualityPreset.LOW, EDhApiVerticalQuality.LOW);
				this.put(EDhApiQualityPreset.MEDIUM, EDhApiVerticalQuality.MEDIUM);
				this.put(EDhApiQualityPreset.HIGH, EDhApiVerticalQuality.HIGH);
				this.put(EDhApiQualityPreset.EXTREME, EDhApiVerticalQuality.EXTREME);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, EDhApiHorizontalQuality> horizontalQuality = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.horizontalQuality,
			new HashMap<EDhApiQualityPreset, EDhApiHorizontalQuality>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, EDhApiHorizontalQuality.LOWEST);
				this.put(EDhApiQualityPreset.LOW, EDhApiHorizontalQuality.LOW);
				this.put(EDhApiQualityPreset.MEDIUM, EDhApiHorizontalQuality.MEDIUM);
				this.put(EDhApiQualityPreset.HIGH, EDhApiHorizontalQuality.HIGH);
				this.put(EDhApiQualityPreset.EXTREME, EDhApiHorizontalQuality.EXTREME);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, EDhApiTransparency> transparency = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.transparency,
			new HashMap<EDhApiQualityPreset, EDhApiTransparency>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, EDhApiTransparency.DISABLED);
				this.put(EDhApiQualityPreset.LOW, EDhApiTransparency.DISABLED); // should be fake if/when fake is fixed
				this.put(EDhApiQualityPreset.MEDIUM, EDhApiTransparency.COMPLETE);
				this.put(EDhApiQualityPreset.HIGH, EDhApiTransparency.COMPLETE);
				this.put(EDhApiQualityPreset.EXTREME, EDhApiTransparency.COMPLETE);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, Boolean> ssaoEnabled = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Ssao.enableSsao,
			new HashMap<EDhApiQualityPreset, Boolean>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, false);
				this.put(EDhApiQualityPreset.LOW, false);
				this.put(EDhApiQualityPreset.MEDIUM, true);
				this.put(EDhApiQualityPreset.HIGH, true);
				this.put(EDhApiQualityPreset.EXTREME, true);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, EDhApiMcRenderingFadeMode> vanillaFade = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.vanillaFadeMode,
			new HashMap<EDhApiQualityPreset, EDhApiMcRenderingFadeMode>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, EDhApiMcRenderingFadeMode.NONE);
				this.put(EDhApiQualityPreset.LOW, EDhApiMcRenderingFadeMode.SINGLE_PASS);
				this.put(EDhApiQualityPreset.MEDIUM, EDhApiMcRenderingFadeMode.DOUBLE_PASS);
				this.put(EDhApiQualityPreset.HIGH, EDhApiMcRenderingFadeMode.DOUBLE_PASS);
				this.put(EDhApiQualityPreset.EXTREME, EDhApiMcRenderingFadeMode.DOUBLE_PASS);
			}});
	private final ConfigEntryWithPresetOptions<EDhApiQualityPreset, Boolean> dhDither = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.ditherDhFade,
			new HashMap<EDhApiQualityPreset, Boolean>()
			{{
				this.put(EDhApiQualityPreset.MINIMUM, false);
				this.put(EDhApiQualityPreset.LOW, true);
				this.put(EDhApiQualityPreset.MEDIUM, true);
				this.put(EDhApiQualityPreset.HIGH, true);
				this.put(EDhApiQualityPreset.EXTREME, true);
			}});
		
	
	
	//==============//
	// constructors //
	//==============//
	
	/** private since we only ever need one handler at a time */
	private RenderQualityPresetConfigEventHandler()
	{
		// add each config used by this preset
		this.configList.add(this.drawResolution);
		this.configList.add(this.verticalQuality);
		this.configList.add(this.horizontalQuality);
		this.configList.add(this.transparency);
		this.configList.add(this.ssaoEnabled);
		this.configList.add(this.vanillaFade);
		this.configList.add(this.dhDither);
		
		
		for (ConfigEntryWithPresetOptions<EDhApiQualityPreset, ?> config : this.configList)
		{
			// ignore try-using, the listener should only ever be added once and should never be removed
			new ConfigChangeListener<>(config.configEntry, (val) -> { this.onConfigValueChanged(); });
		}
	}
	
	
	
	//==============//
	// enum getters //
	//==============//
	
	@Override
	protected IConfigEntry<EDhApiQualityPreset> getPresetConfigEntry() { return Config.Client.qualityPresetSetting; }
	
	@Override
	protected List<EDhApiQualityPreset> getPresetEnumList() { return Arrays.asList(EDhApiQualityPreset.values()); }
	@Override
	protected EDhApiQualityPreset getCustomPresetEnum() { return EDhApiQualityPreset.CUSTOM; }
	
}
