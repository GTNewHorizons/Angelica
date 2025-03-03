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

package com.seibel.distanthorizons.common.wrappers.worldGeneration.step;

import java.util.ArrayList;
import java.util.List;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.ThreadedParameters;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.levelgen.blending.Blender;
#endif

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif

public final class StepBiomes
{
	public static final ChunkStatus STATUS = ChunkStatus.BIOMES;
	
	private final BatchGenerationEnvironment environment;
	
	
	public StepBiomes(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
	public void generateGroup(
			ThreadedParameters tParams, WorldGenRegion worldGenRegion,
			List<ChunkWrapper> chunkWrappers)
	{
		
		ArrayList<ChunkAccess> chunksToDo = new ArrayList<>();
		
		for (ChunkWrapper chunkWrapper : chunkWrappers)
		{
			ChunkAccess chunk = chunkWrapper.getChunk();
			if (chunkWrapper.getStatus().isOrAfter(STATUS))
			{
				// this chunk has already generated this step
				continue;
			}
			else if (chunk instanceof ProtoChunk)
			{
				chunkWrapper.trySetStatus(STATUS);
				chunksToDo.add(chunk);
			}
		}
		
		for (ChunkAccess chunk : chunksToDo)
		{
			#if MC_VER < MC_1_18_2
			this.environment.params.generator.createBiomes(this.environment.params.biomes, chunk);
			#elif MC_VER < MC_1_19_2
			chunk = this.environment.confirmFutureWasRunSynchronously(
						this.environment.params.generator.createBiomes(
							this.environment.params.biomes, 
							Runnable::run, 
							Blender.of(worldGenRegion),
							tParams.structFeat.forWorldGenRegion(worldGenRegion), 
							chunk)
					);
			#elif MC_VER < MC_1_19_4
			chunk = this.environment.confirmFutureWasRunSynchronously(
						this.environment.params.generator.createBiomes(
							this.environment.params.biomes, 
							Runnable::run, 
							this.environment.params.randomState, Blender.of(worldGenRegion),
							tParams.structFeat.forWorldGenRegion(worldGenRegion), 
							chunk)
					);
			#elif MC_VER < MC_1_21_1
			chunk = this.environment.confirmFutureWasRunSynchronously(
						this.environment.params.generator.createBiomes(
							Runnable::run, 
							this.environment.params.randomState, 
							Blender.of(worldGenRegion),
							tParams.structFeat.forWorldGenRegion(worldGenRegion), 
							chunk)
					);
			#else
			chunk = this.environment.confirmFutureWasRunSynchronously(
						this.environment.params.generator.createBiomes(
							this.environment.params.randomState, 
							Blender.of(worldGenRegion),
							tParams.structFeat.forWorldGenRegion(worldGenRegion), 
							chunk)
					);
			#endif
		}
	}
	
}