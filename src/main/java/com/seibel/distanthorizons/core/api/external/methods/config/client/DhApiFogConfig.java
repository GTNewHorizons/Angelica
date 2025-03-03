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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogDrawMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiHeightFogConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.coreapi.util.converters.ApiFogDrawModeConverter;

public class DhApiFogConfig implements IDhApiFogConfig
{
	public static DhApiFogConfig INSTANCE = new DhApiFogConfig();
	
	private DhApiFogConfig() { }
	
	
	
	//===============//
	// inner configs //
	//===============//
	
	@Override
	public IDhApiFarFogConfig farFog() { return DhApiFarFogConfig.INSTANCE; }
	@Override
	public IDhApiHeightFogConfig heightFog() { return DhApiHeightFogConfig.INSTANCE; }
	
	
	
	//====================//
	// basic fog settings //
	//====================//
	
	@Deprecated
	@Override
	public IDhApiConfigValue<EDhApiFogDrawMode> drawMode()
	{ return new DhApiConfigValue<Boolean, EDhApiFogDrawMode>(Config.Client.Advanced.Graphics.Fog.enableDhFog, new ApiFogDrawModeConverter()); }
	@Override
	public IDhApiConfigValue<Boolean> enableDhFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.enableDhFog); }
	
	@Override
	public IDhApiConfigValue<EDhApiFogColorMode> color()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.colorMode); }
	
	@Override
	@Deprecated
	public IDhApiConfigValue<Boolean> disableVanillaFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.disableVanillaFog); }
	@Override
	public IDhApiConfigValue<Boolean> enableVanillaFog()
	{ return new DhApiConfigValue<>(Config.Client.Advanced.Graphics.Fog.enableVanillaFog); }
	
	
	
}
