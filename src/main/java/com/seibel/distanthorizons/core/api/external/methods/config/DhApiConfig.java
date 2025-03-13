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

package com.seibel.distanthorizons.core.api.external.methods.config;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.*;
import com.seibel.distanthorizons.core.api.external.methods.config.client.*;
import com.seibel.distanthorizons.core.api.external.methods.config.common.DhApiWorldGenerationConfig;

public class DhApiConfig implements IDhApiConfig
{
	public static final DhApiConfig INSTANCE = new DhApiConfig();
	
	private DhApiConfig() { }
	
	
	
	@Override
	public IDhApiGraphicsConfig graphics() { return DhApiGraphicsConfig.INSTANCE; }
	@Override
	public IDhApiWorldGenerationConfig worldGenerator() { return DhApiWorldGenerationConfig.INSTANCE; }
	@Override
	public IDhApiMultiplayerConfig multiplayer() { return DhApiMultiplayerConfig.INSTANCE; }
	@Override
	public IDhApiMultiThreadingConfig multiThreading() { return DhApiMultiThreadingConfig.INSTANCE; }
	@Override
	public IDhApiDebuggingConfig debugging() { return DhApiDebuggingConfig.INSTANCE; }
	
}
