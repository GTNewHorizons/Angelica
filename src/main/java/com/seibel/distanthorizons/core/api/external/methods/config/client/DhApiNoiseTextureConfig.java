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
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiNoiseTextureConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiNoiseTextureConfig implements IDhApiNoiseTextureConfig
{
	public static DhApiNoiseTextureConfig INSTANCE = new DhApiNoiseTextureConfig();
	
	private DhApiNoiseTextureConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> noiseEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.NoiseTexture.enableNoiseTexture); }
	
	@Override
	public IDhApiConfigValue<Integer> noiseSteps()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.NoiseTexture.noiseSteps); }
	
	@Override
	public IDhApiConfigValue<Double> noiseIntensity()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.NoiseTexture.noiseIntensity); }
	
	@Override
	public IDhApiConfigValue<Integer> noiseDropoff()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.NoiseTexture.noiseDropoff); }
	
}
