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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Listens to the config and will automatically
 * clear the current render cache if certain settings are changed. <br> <br>
 *
 * Note: if additional settings should clear the render cache, add those to this listener, don't create a new listener
 */
public class WorldCurvatureConfigEventHandler implements IConfigListener
{
	public static WorldCurvatureConfigEventHandler INSTANCE = new WorldCurvatureConfigEventHandler();
	
	private static final int MIN_VALID_CURVE_VALUE = 50; 
	
	
	/** private since we only ever need one handler at a time */
	private WorldCurvatureConfigEventHandler() { }
	
	
	
	@Override
	public void onConfigValueSet()
	{
		int curveRatio = Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.get();
		if (curveRatio > 0 && curveRatio < MIN_VALID_CURVE_VALUE)
		{
			// shouldn't update the UI, otherwise we may end up fighting the user
			Config.Client.Advanced.Graphics.Experimental.earthCurveRatio.set(MIN_VALID_CURVE_VALUE);
		}
		
	}
	
	@Override
	public void onUiModify() { /* do nothing, we only care about modified config values */ }
	
	
}
