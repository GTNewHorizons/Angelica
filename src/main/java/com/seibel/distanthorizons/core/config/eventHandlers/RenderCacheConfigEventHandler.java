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

package com.seibel.distanthorizons.core.config.eventHandlers;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiTransparency;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.util.TimerUtil;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Listens to the config and will automatically
 * clear the current render cache if certain settings are changed. <br> <br>
 *
 * Note: if additional settings should clear the render cache, add those to this listener, don't create a new listener
 */
public class RenderCacheConfigEventHandler
{
	private static RenderCacheConfigEventHandler INSTANCE;
	
	
	// previous values used to check if a watched setting was actually modified
	private final ConfigChangeListener<EDhApiMaxHorizontalResolution> horizontalResolutionChangeListener;
	private final ConfigChangeListener<EDhApiVerticalQuality> verticalQualityChangeListener;
	private final ConfigChangeListener<EDhApiTransparency> transparencyChangeListener;
	private final ConfigChangeListener<EDhApiBlocksToAvoid> blocksToIgnoreChangeListener;
	private final ConfigChangeListener<Boolean> tintWithAvoidedBlocksChangeListener;
	
	private final ConfigChangeListener<Double> brightnessMultiplierChangeListener;
	private final ConfigChangeListener<Double> saturationMultiplierChangeListener;
	private final ConfigChangeListener<EDhApiLodShading> lodShadingChangeListener;
	private final ConfigChangeListener<EDhApiGrassSideRendering> grassSideChangeListener;
	
	private final ConfigChangeListener<EDhApiDebugRendering> debugRenderingChangeListener;
	
	/** how long to wait in milliseconds before applying the config changes */
	private static final long TIMEOUT_IN_MS = 4_000L;
	private Timer cacheClearingTimer;
	
	
	
	public static RenderCacheConfigEventHandler getInstance()
	{
		if (INSTANCE == null)
		{
			INSTANCE = new RenderCacheConfigEventHandler();
		}
		
		return INSTANCE;
	}
	
	/** private since we only ever need one handler at a time */
	private RenderCacheConfigEventHandler()
	{
		this.horizontalResolutionChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.verticalQualityChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.verticalQuality, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.transparencyChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.transparency, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.blocksToIgnoreChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.blocksToIgnore, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.tintWithAvoidedBlocksChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks, (newValue) -> this.refreshRenderDataAfterTimeout());
		
		this.brightnessMultiplierChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.brightnessMultiplier, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.saturationMultiplierChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.saturationMultiplier, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.lodShadingChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.lodShading, (newValue) -> this.refreshRenderDataAfterTimeout());
		this.grassSideChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.grassSideRendering, (newValue) -> this.refreshRenderDataAfterTimeout());
		
		this.debugRenderingChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Debugging.debugRendering, (newValue) -> this.refreshRenderDataAfterTimeout());
		
	}
	
	
	/** Calling this method multiple times will reset the timer */
	private void refreshRenderDataAfterTimeout()
	{
		// stop the previous timer if one exists
		if (this.cacheClearingTimer != null)
		{
			this.cacheClearingTimer.cancel();
		}
		
		// create a new timer task
		TimerTask timerTask = new TimerTask()
		{
			public void run()
			{
				DhApi.Delayed.renderProxy.clearRenderDataCache();
			}
		};
		this.cacheClearingTimer = TimerUtil.CreateTimer("RenderCacheClearConfigTimer");
		this.cacheClearingTimer.schedule(timerTask, TIMEOUT_IN_MS);
	}
	
}
