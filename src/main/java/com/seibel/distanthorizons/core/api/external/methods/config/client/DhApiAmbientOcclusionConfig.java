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
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiAmbientOcclusionConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiAmbientOcclusionConfig implements IDhApiAmbientOcclusionConfig
{
	public static DhApiAmbientOcclusionConfig INSTANCE = new DhApiAmbientOcclusionConfig();
	
	private DhApiAmbientOcclusionConfig() { }
	
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> enabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.Ssao.enableSsao); }
	
	@Override
	public IDhApiConfigValue<Integer> sampleCount()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Ssao.sampleCount); }
	
	@Override
	public IDhApiConfigValue<Double> radius()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.radius); }
	
	@Override
	public IDhApiConfigValue<Double> strength()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.strength); }
	
	@Override
	public IDhApiConfigValue<Double> bias()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.bias); }
	
	@Override
	public IDhApiConfigValue<Double> minLight()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Ssao.minLight); }
	
	@Override
	public IDhApiConfigValue<Integer> blurRadius()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Ssao.blurRadius); }
	
}
