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

package com.seibel.distanthorizons.core.wrapperInterfaces.world;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;

import java.io.File;

public interface IServerLevelWrapper extends ILevelWrapper
{
	File getMcSaveFolder();
	
	String getWorldFolderName();
	
	default String getKeyedLevelDimensionName()
	{
		String dimensionName = this.getDhIdentifier();
		
		if (Config.Server.sendLevelKeys.get())
		{
			String levelKeyPrefix = Config.Server.levelKeyPrefix.get();
			
			if (SharedApi.getEnvironment() == EWorldEnvironment.CLIENT_SERVER)
			{
				String cleanWorldFolderName = this.getWorldFolderName()
						.replaceAll("[^" + LevelInitMessage.PART_ALLOWED_CHARS_REGEX + " ]", "")
						.replaceAll(" ", "_");
				
				levelKeyPrefix += (!levelKeyPrefix.isEmpty() ? "_" : "") + cleanWorldFolderName
						+ "_" + this.getHashedSeedEncoded();
			}
			
			if (levelKeyPrefix.isEmpty())
			{
				levelKeyPrefix = this.getHashedSeedEncoded();
			}
			
			String mainPart = "@" + dimensionName;
			
			return levelKeyPrefix.substring(0, Math.min(
					LevelInitMessage.MAX_LENGTH - mainPart.length(),
					levelKeyPrefix.length()
			)) + mainPart;
		}
		
		return dimensionName;
	}
	
}
