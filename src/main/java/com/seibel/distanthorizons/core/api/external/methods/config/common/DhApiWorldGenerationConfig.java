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

package com.seibel.distanthorizons.core.api.external.methods.config.common;

import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.Config;

/**
 * Distant Horizons' world generation configuration. <br><br>
 *
 * Note: LODs are NOT saved in Minecraft's save system.
 *
 * @author James Seibel
 * @version 2023-9-14
 */
public class DhApiWorldGenerationConfig implements IDhApiWorldGenerationConfig
{
	public static DhApiWorldGenerationConfig INSTANCE = new DhApiWorldGenerationConfig();
	
	private DhApiWorldGenerationConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Boolean> enableDistantWorldGeneration()
	{ return new DhApiConfigValue<>(Config.Common.WorldGenerator.enableDistantGeneration); }
	
	@Override
	public IDhApiConfigValue<EDhApiDistantGeneratorMode> distantGeneratorMode()
	{ return new DhApiConfigValue<>(Config.Common.WorldGenerator.distantGeneratorMode); }
	
	
}
