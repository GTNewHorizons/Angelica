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

package com.seibel.distanthorizons.core.file.structure;

import com.seibel.distanthorizons.api.interfaces.override.levelHandling.IDhApiSaveStructure;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.world.EWorldEnvironment;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Designed for {@link EWorldEnvironment#CLIENT_SERVER} & {@link EWorldEnvironment#SERVER_ONLY} environments.
 */
public class LocalSaveStructure implements ISaveStructure
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final ConcurrentHashMap<ILevelWrapper, File> levelWrapperToFileMap = new ConcurrentHashMap<>();
	
	
	
	public LocalSaveStructure() { }
	
	
	
	//================//
	// folder methods //
	//================//
	
	@Override
	public File getSaveFolder(ILevelWrapper levelWrapper)
	{
		return this.levelWrapperToFileMap.computeIfAbsent(levelWrapper, (newLevelWrapper) ->
		{
			IServerLevelWrapper serverLevelWrapper = (IServerLevelWrapper) levelWrapper;
			File saveFolder = serverLevelWrapper.getMcSaveFolder();
			
			
			// Allow API users to override the save folder
			IDhApiSaveStructure saveStructureOverride = OverrideInjector.INSTANCE.get(IDhApiSaveStructure.class);
			if (saveStructureOverride != null)
			{
				File overrideFile = saveStructureOverride.overrideFilePath(saveFolder, levelWrapper);
				if (overrideFile != null)
				{
					LOGGER.info("Save folder overridden from [" + saveFolder.getPath() + "] -> [" + overrideFile.getPath() + "].");
					saveFolder = overrideFile;
				}
			}
			
			return saveFolder;
		});
	}
	
	@Override
	public File getPre23SaveFolder(ILevelWrapper levelWrapper) { return this.getSaveFolder(levelWrapper); }
	
	
	//==================//
	// override methods //
	//==================//
	
	@Override
	public void close() throws Exception { }
	
	@Override
	public String toString() 
	{ return "[" + this.getClass().getSimpleName() + "@(" + StringUtil.join(";", this.levelWrapperToFileMap.values()) + ")]"; }
	
}
