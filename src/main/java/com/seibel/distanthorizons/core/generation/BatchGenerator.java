/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2021 Tom Lee (TomTheFurry) & James Seibel (Original code)
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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * @author Leetom
 * @version 2022-12-10
 */
public class BatchGenerator implements IDhApiWorldGenerator
{
	private static final IWrapperFactory FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public AbstractBatchGenerationEnvironmentWrapper generationEnvironment;
	public IDhLevel targetDhLevel;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public BatchGenerator(IDhLevel targetDhLevel)
	{
		this.targetDhLevel = targetDhLevel;
		this.generationEnvironment = FACTORY.createBatchGenerator(targetDhLevel);
		LOGGER.info("Batch Chunk Generator initialized");
	}
	
	
	
	//=====================//
	// override parameters // 
	//=====================//
	
	@Override
	public int getPriority() { return IOverrideInjector.CORE_PRIORITY; }
	
	
	
	//======================//
	// generator parameters //
	//======================//
	
	@Override
	public byte getSmallestDataDetailLevel() { return LodUtil.BLOCK_DETAIL_LEVEL; }
	@Override
	public byte getLargestDataDetailLevel() { return LodUtil.BLOCK_DETAIL_LEVEL; }
	
	
	
	
	//===================//
	// generator methods //
	//===================//
	
	@Override
	public CompletableFuture<Void> generateChunks(
			int chunkPosMinX, int chunkPosMinZ, int generationRequestChunkWidthCount, byte targetDataDetail, EDhApiDistantGeneratorMode generatorMode,
			ExecutorService worldGeneratorThreadPool, Consumer<Object[]> resultConsumer)
	{
		EDhApiWorldGenerationStep targetStep = null;
		switch (generatorMode)
		{
			case PRE_EXISTING_ONLY: // Only load in existing chunks. Note: this requires the biome generation step in order for biomes to be properly initialized.
			//case BIOME_ONLY: // No blocks. Require fake height in LodBuilder
				targetStep = EDhApiWorldGenerationStep.BIOMES; 
				break;
			//case BIOME_ONLY_SIMULATE_HEIGHT:
			//	targetStep = EDhApiWorldGenerationStep.NOISE; // Stone only. Requires a fake surface
			//	break;
			case SURFACE:
				targetStep = EDhApiWorldGenerationStep.SURFACE;
				break;
			case FEATURES:
				targetStep = EDhApiWorldGenerationStep.FEATURES;
				break;
			case INTERNAL_SERVER:
				targetStep = EDhApiWorldGenerationStep.LIGHT;
				break;
		}
		
		// the consumer needs to be wrapped like this because the API can't use DH core objects (and IChunkWrapper can't be easily put into the API project)
		Consumer<IChunkWrapper> consumerWrapper = (chunkWrapper) -> resultConsumer.accept(new Object[]{chunkWrapper});
		try
		{
			return this.generationEnvironment.generateChunks(
					chunkPosMinX, chunkPosMinZ, generationRequestChunkWidthCount, 
					generatorMode, targetStep, 
					worldGeneratorThreadPool, consumerWrapper);
		}
		catch (Exception e)
		{
			if (!LodUtil.isInterruptOrReject(e)) LOGGER.error("Error starting future for chunk generation", e);
			CompletableFuture<Void> future = new CompletableFuture<>();
			future.completeExceptionally(e);
			return future;
		}
	}
	
	@Override
	public void preGeneratorTaskStart() { this.generationEnvironment.updateAllFutures(); }
	
	
	
	//=========//
	// cleanup //
	//=========//
	
	@Override
	public void close()
	{
		LOGGER.info(BatchGenerator.class.getSimpleName() + " shutting down...");
		this.generationEnvironment.stop();
	}
	
	
}
