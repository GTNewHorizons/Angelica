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


package com.seibel.distanthorizons.common.wrappers.worldGeneration;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment.PerfCalculator;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.WorldGenStructFeatManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.levelgen.structure.StructureCheck;
#endif

public final class ThreadedParameters
{
	private static final ThreadLocal<ThreadedParameters> LOCAL_PARAM = new ThreadLocal<>();
	
	final ServerLevel level;
	public WorldGenStructFeatManager structFeat = null;
	#if MC_VER >= MC_1_18_2
	public StructureCheck structCheck;
	#endif
	boolean isValid = true;
	public final PerfCalculator perf = new PerfCalculator();
	
	private static GlobalParameters previousGlobalParameters = null;
	
	
	
	public static ThreadedParameters getOrMake(GlobalParameters param)
	{
		ThreadedParameters tParam = LOCAL_PARAM.get();
		if (tParam != null && tParam.isValid && tParam.level == param.level)
		{
			return tParam;
		}
		
		tParam = new ThreadedParameters(param);
		LOCAL_PARAM.set(tParam);
		return tParam;
	}
	
	private ThreadedParameters(GlobalParameters param)
	{
		previousGlobalParameters = param;
		
		this.level = param.level;
		#if MC_VER < MC_1_18_2
		this.structFeat = new WorldGenStructFeatManager(param.worldGenSettings, level);
		#elif MC_VER < MC_1_19_2
		this.structCheck = this.createStructureCheck(param);
		#else
		this.structCheck = new StructureCheck(param.chunkScanner, param.registry, param.structures,
				param.level.dimension(), param.generator, param.randomState, level, param.generator.getBiomeSource(), param.worldSeed,
				param.fixerUpper);
		#endif
	}
	
	
	
	public void markAsInvalid() { isValid = false; }
	
	public void makeStructFeat(WorldGenLevel genLevel, GlobalParameters param)
	{
		#if MC_VER < MC_1_19_4
		structFeat = new WorldGenStructFeatManager(param.worldGenSettings, genLevel #if MC_VER >= MC_1_18_2 , structCheck #endif );
		#else
		structFeat = new WorldGenStructFeatManager(param.worldOptions, genLevel, structCheck);
		#endif
	}
	
	
	#if MC_VER >= MC_1_18_2 && MC_VER < MC_1_19_2
	public void recreateStructureCheck()
	{
		if (previousGlobalParameters != null)
		{
			this.structCheck = createStructureCheck(previousGlobalParameters);
		}
	}
	private StructureCheck createStructureCheck(GlobalParameters param)
	{
		return new StructureCheck(param.chunkScanner, param.registry, param.structures,
				param.level.dimension(), param.generator, this.level, param.generator.getBiomeSource(), param.worldSeed,
				param.fixerUpper);
	}
	#else
	public void recreateStructureCheck() { /* do nothing */ }	
	#endif
	
}