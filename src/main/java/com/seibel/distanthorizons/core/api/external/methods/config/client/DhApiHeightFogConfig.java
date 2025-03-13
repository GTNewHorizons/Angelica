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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogDirection;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiHeightFogConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiHeightFogConfig implements IDhApiHeightFogConfig
{
	public static DhApiHeightFogConfig INSTANCE = new DhApiHeightFogConfig();
	
	private DhApiHeightFogConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<EDhApiHeightFogMixMode> heightFogMixMode()
	{ return new DhApiConfigValue<EDhApiHeightFogMixMode, EDhApiHeightFogMixMode>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMixMode); }
	
	@Override
	public IDhApiConfigValue<EDhApiHeightFogDirection> heightFogDirection()
	{ return new DhApiConfigValue<EDhApiHeightFogDirection, EDhApiHeightFogDirection>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDirection); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogBaseHeight()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogBaseHeight); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogStartingHeightPercent()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogStart); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogEndingHeightPercent()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogEnd); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogMinThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMin); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogMaxThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogMax); }
	
	@Override
	public IDhApiConfigValue<EDhApiFogFalloff> heightFogFalloff()
	{ return new DhApiConfigValue<EDhApiFogFalloff, EDhApiFogFalloff>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogFalloff); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogDensity()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.HeightFog.heightFogDensity); }
	
}
