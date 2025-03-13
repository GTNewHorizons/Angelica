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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;

public class QuickRenderToggleConfigEventHandler
{
	public static QuickRenderToggleConfigEventHandler INSTANCE = new QuickRenderToggleConfigEventHandler();
	
	private final ConfigChangeListener<Boolean> quickRenderChangeListener;
	private final ConfigChangeListener<EDhApiRendererMode> rendererModeChangeListener;
	
	
	
	/** private since we only ever need one handler at a time */
	private QuickRenderToggleConfigEventHandler()
	{
		this.quickRenderChangeListener = new ConfigChangeListener<>(Config.Client.quickEnableRendering,
				(val) -> {
					Config.Client.Advanced.Debugging.rendererMode.set(Config.Client.quickEnableRendering.get()
							? EDhApiRendererMode.DEFAULT
							: EDhApiRendererMode.DISABLED);
				});
		this.rendererModeChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Debugging.rendererMode,
				(val) -> {
					Config.Client.quickEnableRendering.set(
							Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED);
				});
	}
	
	/**
	 * Set the UI only config based on what is set in the file. <br>
	 * This should only be called once.
	 */
	public void setUiOnlyConfigValues()
	{
		boolean enableRendering = Config.Client.Advanced.Debugging.rendererMode.get() != EDhApiRendererMode.DISABLED;
		Config.Client.quickEnableRendering.set(enableRendering);
	}
	
}
