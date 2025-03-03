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

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.generation.BatchGenerator;
import com.seibel.distanthorizons.core.generation.WorldGenerationQueue;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.DependencyInjection.WorldGeneratorInjector;
import org.apache.logging.log4j.Logger;

public class ServerLevelModule implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final IDhServerLevel parentServerLevel;
	public final ISaveStructure saveStructure;
	public final GeneratedFullDataSourceProvider fullDataFileHandler;
	
	public final WorldGenModule worldGenModule;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ServerLevelModule(IDhServerLevel parentServerLevel, ISaveStructure saveStructure)
	{
		this.parentServerLevel = parentServerLevel;
		this.saveStructure = saveStructure;
		this.fullDataFileHandler = new GeneratedFullDataSourceProvider(parentServerLevel, saveStructure);
		this.worldGenModule = new WorldGenModule(this.parentServerLevel, this.fullDataFileHandler, () -> new ServerLevelModule.WorldGenState(this.parentServerLevel));
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close()
	{
		// shutdown the world-gen
		this.worldGenModule.close();
		this.fullDataFileHandler.close();
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class WorldGenState extends WorldGenModule.AbstractWorldGenState
	{
		WorldGenState(IDhServerLevel level)
		{
			IDhApiWorldGenerator worldGenerator = WorldGeneratorInjector.INSTANCE.get(level.getLevelWrapper());
			if (worldGenerator == null)
			{
				// no override generator is bound, use the Core world generator
				worldGenerator = new BatchGenerator(level);
				// binding the core generator won't prevent other mods from binding their own generators
				// since core world generator's should have the lowest override priority
				WorldGeneratorInjector.INSTANCE.bind(level.getLevelWrapper(), worldGenerator);
			}
			this.worldGenerationQueue = new WorldGenerationQueue(worldGenerator);
		}
		
	}
	
}
