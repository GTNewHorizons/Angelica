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

package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGenericRenderingConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiGenericRenderingConfig implements IDhApiGenericRenderingConfig
{
	public static DhApiGenericRenderingConfig INSTANCE = new DhApiGenericRenderingConfig();
	
	private DhApiGenericRenderingConfig() { }
	
	
	
	@Override 
	public IDhApiConfigValue<Boolean> renderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableGenericRendering); }
	@Override
	public IDhApiConfigValue<Boolean> beaconRenderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableBeaconRendering); }
	@Override 
	public IDhApiConfigValue<Boolean> cloudRenderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.GenericRendering.enableCloudRendering); }
	
}
