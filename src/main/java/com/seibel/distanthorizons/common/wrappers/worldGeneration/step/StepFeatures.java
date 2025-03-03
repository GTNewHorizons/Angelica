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

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.ThreadedParameters;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.logging.log4j.Logger;

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif


public final class StepFeatures
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final ChunkStatus STATUS = ChunkStatus.FEATURES;
	
	private final BatchGenerationEnvironment environment;
	
	
	
	public StepFeatures(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
	public void generateGroup(
			ThreadedParameters tParams, DhLitWorldGenRegion worldGenRegion,
			ArrayGridList<ChunkWrapper> chunkWrappers)
	{
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
			}
			
			
			try
			{
				#if MC_VER < MC_1_18_2
				worldGenRegion.setOverrideCenter(chunk.getPos());
				environment.params.generator.applyBiomeDecoration(worldGenRegion, tParams.structFeat);
				#else
				if (worldGenRegion.hasChunk(chunkWrapper.getChunkPos().getX(), chunkWrapper.getChunkPos().getZ()))
				{
					this.environment.params.generator.applyBiomeDecoration(worldGenRegion, chunk, tParams.structFeat.forWorldGenRegion(worldGenRegion));
				}
				else
				{
					LOGGER.warn("Unable to generate features for chunk at pos ["+chunkWrapper.getChunkPos()+"], world gen region doesn't contain the chunk.");
				}
				#endif
				
				Heightmap.primeHeightmaps(chunk, STATUS.heightmapsAfter());
			}
			catch (Exception e)
			{
				LOGGER.warn("Unexpected issue when generating features for chunk at pos ["+chunkWrapper.getChunkPos()+"], error: ["+e.getMessage()+"].", e);
				// FIXME: Features concurrent modification issue. Something about cocobeans might just
				//  error out. For now just retry.
			}
		}
	}
	
}