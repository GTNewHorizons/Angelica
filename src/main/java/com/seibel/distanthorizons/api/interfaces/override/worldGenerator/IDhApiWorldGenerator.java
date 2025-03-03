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

package com.seibel.distanthorizons.api.interfaces.override.worldGenerator;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.api.enums.EDhApiDetailLevel;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * @author James Seibel
 * @version 2024-10-07
 * @since API 1.0.0
 */
public interface IDhApiWorldGenerator extends Closeable, IDhApiOverrideable
{
	//============//
	// parameters //
	//============//
	
	/**
	 * Defines the smallest datapoint size that can be generated at a time. <br>
	 * Maximum detail level (smallest numerical value) is 0 (1 block) <br>
	 * Default detail level is 0 <br>
	 * For more information on what detail levels represent see: {@link EDhApiDetailLevel}. <br><br>
	 * 
	 * @see EDhApiDetailLevel
	 * @since API 1.0.0
	 */
	default byte getSmallestDataDetailLevel() { return EDhApiDetailLevel.BLOCK.detailLevel; }
	/**
	 * Defines the largest datapoint size that can be generated at a time. <br>
	 * Maximum detail level (smallest numerical value) is 0 (1 block) <br>
	 * Default detail level is 0 <br>
	 * For more information on what detail levels represent see: {@link EDhApiDetailLevel}.
	 *
	 * @see EDhApiDetailLevel
	 * @since API 1.0.0
	 */
	default byte getLargestDataDetailLevel() { return EDhApiDetailLevel.BLOCK.detailLevel; }
	
	/** 
	 * Used if {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#API_CHUNKS} or {@link EDhApiWorldGeneratorReturnType#API_DATA_SOURCES}. <Br> 
	 * If true DH will run additional validation on the {@link DhApiChunk} or {@link IDhApiFullDataSource}'s returned. <Br>
	 * This should be disabled during release but should be enabled during development to help spot issues with your data format.
	 * 
	 * @see #getReturnType()
	 * @see DhApiChunk
	 * @see IDhApiFullDataSource
	 * @see EDhApiWorldGeneratorReturnType#API_CHUNKS
	 * @since API 4.0.0
	 */
	default boolean runApiValidation() { return true; }
	
	
	
	//=================//
	// world generator //
	//=================//
	
	/**
	 * This method is called by Distant Horizons to generate terrain over a given area when
	 * {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#VANILLA_CHUNKS}. <br><br>
	 *
	 * After a chunk has been generated it (and any necessary supporting objects as listed below) should be passed into the
	 * resultConsumer's {@link Consumer#accept} method. If the Consumer is given the wrong data
	 * type(s) it will disable the world generator and log an error with a list of objects it was expecting. <br>
	 * <strong>Note:</strong> these objects are minecraft version dependent and <i>will</i> change without notice!
	 * Please run your generator in game at least once to confirm the objects you are returning are correct. <br><br>
	 *
	 * Consumer expected inputs for each minecraft version (in order): <br>
	 * <strong>1.16</strong>, <strong>1.17</strong>, <strong>1.18</strong>, <strong>1.19</strong>, <strong>1.20</strong>: <br>
	 *  - [net.minecraft.world.level.chunk.ChunkAccess] <br>
	 *  - [net.minecraft.world.level.ServerLevel] or [net.minecraft.world.level.ClientLevel] <br>
	 *
	 * @implNote the default implementation of this method throws an {@link UnsupportedOperationException},
	 * and must be overridden when {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#VANILLA_CHUNKS}.
	 * since {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#VANILLA_CHUNKS} by default,
	 * this method must also be overridden when {@link #getReturnType()} is NOT overridden.
	 *
	 * @param chunkPosMinX the chunk X position closest to negative infinity
	 * @param chunkPosMinZ the chunk Z position closest to negative infinity
	 * @param generationRequestChunkWidthCount how many chunks wide you should generate 
	 * @param targetDataDetail the LOD Detail level requested to generate. See {@link EDhApiDetailLevel} for additional information.
	 * @param generatorMode how far into the world gen pipeline this method should run. See {@link EDhApiDistantGeneratorMode} for additional documentation.
	 * @param worldGeneratorThreadPool the thread pool that should be used when generating the returned {@link CompletableFuture}.
	 * @param resultConsumer the consumer that should be fired whenever a chunk finishes generating.
	 *
	 * @return a future that should run on the worldGeneratorThreadPool and complete once the given generation task has completed.
	 *
	 * @since API 1.0.0
	 */
	default CompletableFuture<Void> generateChunks(
		int chunkPosMinX,
		int chunkPosMinZ,
		int generationRequestChunkWidthCount,
		byte targetDataDetail,
		EDhApiDistantGeneratorMode generatorMode,
		ExecutorService worldGeneratorThreadPool,
		Consumer<Object[]> resultConsumer
		) 
	{
		throw new UnsupportedOperationException();
	}
	
	/**
	 * This method is called by Distant Horizons to generate terrain over a given area when
	 * {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#API_CHUNKS}. <br><br>
	 *
	 * After the {@link DhApiChunk} has been generated, it should be passed into the
	 * resultConsumer's {@link Consumer#accept(Object)} method.
	 * Note: if air blocks aren't included in the with the {@link DhApiChunk} with proper lighting, lower detail levels will appear as black/unlit.
	 *
	 * @implNote the default implementation of this method throws an {@link UnsupportedOperationException},
	 * and must be overridden when {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#API_CHUNKS}.
	 *
	 * @param chunkPosMinX the chunk X position closest to negative infinity
	 * @param chunkPosMinZ the chunk Z position closest to negative infinity
	 * @param generationRequestChunkWidthCount how many chunks wide you should generate
	 * @param targetDataDetail the LOD Detail level requested to generate. See {@link EDhApiDetailLevel} for additional information.
	 * @param generatorMode how far into the world gen pipeline this method should run. See {@link EDhApiDistantGeneratorMode} for additional documentation.
	 * @param worldGeneratorThreadPool the thread pool that should be used when generating the returned {@link CompletableFuture}.
	 * @param resultConsumer the consumer that should be fired whenever a chunk finishes generating.
	 *
	 * @return a future that should run on the worldGeneratorThreadPool and complete once the given generation task has completed.
	 *
	 * @since API 2.0.0
	 */
	default CompletableFuture<Void> generateApiChunks(
		int chunkPosMinX,
		int chunkPosMinZ,
		int generationRequestChunkWidthCount,
		byte targetDataDetail,
		EDhApiDistantGeneratorMode generatorMode,
		ExecutorService worldGeneratorThreadPool,
		Consumer<DhApiChunk> resultConsumer
		) 
	{
		throw new UnsupportedOperationException();
	}
	
	/**
	 * This method is called by Distant Horizons to generate terrain over a given area when
	 * {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#API_DATA_SOURCES}. <br><br>
	 *
	 * After the {@link IDhApiWorldGenerator} has been generated, it should be passed into the
	 * resultConsumer's {@link Consumer#accept(Object)} method.
	 * Note: if air blocks aren't included in the with the {@link DhApiChunk} with proper lighting, lower detail levels will appear as black/unlit.
	 *
	 * @implNote the default implementation of this method throws an {@link UnsupportedOperationException},
	 * and must be overridden when {@link #getReturnType()} returns {@link EDhApiWorldGeneratorReturnType#API_CHUNKS}.
	 *
	 * @param chunkPosMinX the chunk X position closest to negative infinity
	 * @param chunkPosMinZ the chunk Z position closest to negative infinity
	 * @param lodPosX the LOD's X position, relative to the given {@link EDhApiDetailLevel}
	 * @param lodPosZ the LOD's Z position, relative to the given {@link EDhApiDetailLevel}
	 * @param detailLevel the LOD Detail level requested to generate. See {@link EDhApiDetailLevel} for additional information.
	 * @param pooledFullDataSource The data source you should populate during your world generation.
	 *                              This data source is pooled by DH and may be reused multiple times by different internal DH systems. <br>
	 *                              This data source should <strong>not</strong> be referenced or stored outside of this method nor the executor provided by worldGeneratorThreadPool.
	 *                              <strong>Attempting to do so will corrupt DH's data.</strong>
	 * @param generatorMode how far into the world gen pipeline this method should run. See {@link EDhApiDistantGeneratorMode} for additional documentation.
	 * @param worldGeneratorThreadPool the thread pool that should be used when generating the returned {@link CompletableFuture}.
	 * @param resultConsumer the consumer that should be fired whenever a chunk finishes generating.
	 *
	 * @return a future that should run on the worldGeneratorThreadPool and complete once the given generation task has completed.
	 *
	 * @since API 4.0.0
	 */
	default CompletableFuture<Void> generateLod(
		int chunkPosMinX, int chunkPosMinZ,
		int lodPosX, int lodPosZ, byte detailLevel,
		IDhApiFullDataSource pooledFullDataSource,
		EDhApiDistantGeneratorMode generatorMode,
		ExecutorService worldGeneratorThreadPool,
		Consumer<IDhApiFullDataSource> resultConsumer
		) 
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * This method controls how Distant Horizons requests generated chunks.
	 * By default, the return value is {@link EDhApiWorldGeneratorReturnType#VANILLA_CHUNKS},
	 * which means that {@link #generateChunks(int, int, int, byte, EDhApiDistantGeneratorMode, ExecutorService, Consumer)}
	 * will be invoked whenever Distant Horizons wants to generate terrain with this world generator.
	 *
	 * @since API 2.0.0
	 */
	default EDhApiWorldGeneratorReturnType getReturnType() { return EDhApiWorldGeneratorReturnType.VANILLA_CHUNKS; }
	
	
	
	//===============//
	// event methods //
	//===============//
	
	/**
	 * Called before a new generator task is started. <br>
	 * This can be used to run cleanup on existing tasks before new tasks are started.
	 *
	 * @since API 1.0.0
	 */
	void preGeneratorTaskStart();
	
	
	
	//===========//
	// overrides //
	//===========//
	
	// This is overridden to remove the "throws IOException" 
	// that is present in the default Closeable.close() method 
	@Override
	void close();
	
	
	
}
