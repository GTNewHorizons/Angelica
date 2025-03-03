/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2021  Tom Lee (TomTheFurry)
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

import com.google.common.collect.ImmutableMap;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.*;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.IDhServerLevel;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.objects.EventTimer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.seibel.distanthorizons.common.wrappers.DependencySetupDoneCheck;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepBiomes;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepFeatures;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepNoise;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureReference;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureStart;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepSurface;

import net.minecraft.server.level.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.LogManager;

#if MC_VER >= MC_1_19_4
import net.minecraft.core.registries.Registries;
#else
import net.minecraft.core.Registry;
#endif

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;

import javax.annotation.Nullable;
#endif

/*
Total:                   3.135214124s
=====================================
Empty Chunks:            0.000558328s
StructureStart Step:     0.025177207s
StructureReference Step: 0.00189559s
Biome Step:              0.13789155s
Noise Step:              1.570347555s
Surface Step:            0.741238194s
Carver Step:             0.000009923s
Feature Step:            0.389072425s
Lod Generation:          0.269023348s
*/
public final class BatchGenerationEnvironment extends AbstractBatchGenerationEnvironmentWrapper
{
	public static final ConfigBasedSpamLogger PREF_LOGGER =
			new ConfigBasedSpamLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Common.Logging.logWorldGenPerformance.get(), 1);
	public static final ConfigBasedLogger EVENT_LOGGER =
			new ConfigBasedLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Common.Logging.logWorldGenEvent.get());
	public static final ConfigBasedLogger LOAD_LOGGER =
			new ConfigBasedLogger(LogManager.getLogger("LodWorldGen"),
					() -> Config.Common.Logging.logWorldGenLoadEvent.get());

	private static final TicketType<ChunkPos> DH_SERVER_GEN_TICKET = TicketType.create("dh_server_gen_ticket", Comparator.comparingLong(ChunkPos::toLong));

	private static final IModChecker MOD_CHECKER = SingletonInjector.INSTANCE.get(IModChecker.class);


	private final IDhServerLevel serverlevel;

	/**
	 * will be true if C2ME is installed (since they require us to
	 * pull chunks using their async method), or if there
	 * was an issue with the sync pulling method.
	 */
	private boolean pullExistingChunkUsingMcAsyncMethod = false;



	//=================Generation Step===================

	public final LinkedBlockingQueue<GenerationEvent> generationEventList = new LinkedBlockingQueue<>();
	public final GlobalParameters params;
	public final StepStructureStart stepStructureStart = new StepStructureStart(this);
	public final StepStructureReference stepStructureReference = new StepStructureReference(this);
	public final StepBiomes stepBiomes = new StepBiomes(this);
	public final StepNoise stepNoise = new StepNoise(this);
	public final StepSurface stepSurface = new StepSurface(this);
	public final StepFeatures stepFeatures = new StepFeatures(this);
	public boolean unsafeThreadingRecorded = false;
	public static final long EXCEPTION_TIMER_RESET_TIME = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
	public static final int EXCEPTION_COUNTER_TRIGGER = 20;
	public static final int RANGE_TO_RANGE_EMPTY_EXTENSION = 1;
	public int unknownExceptionCount = 0;
	public long lastExceptionTriggerTime = 0;

	private final AtomicReference<RegionFileStorageExternalCache> regionFileStorageCacheRef = new AtomicReference<>();
	public RegionFileStorageExternalCache getOrCreateRegionFileCache(RegionFileStorage storage)
	{
		RegionFileStorageExternalCache cache = this.regionFileStorageCacheRef.get();
		if (cache == null)
		{
			cache = new RegionFileStorageExternalCache(storage);
			if (!this.regionFileStorageCacheRef.compareAndSet(null, cache))
			{
				cache = this.regionFileStorageCacheRef.get();
			}
		}
		return cache;
	}

	public static ThreadLocal<Boolean> isDistantGeneratorThread = new ThreadLocal<>();
	public static boolean isCurrentThreadDistantGeneratorThread() { return (isDistantGeneratorThread.get() != null); }



	//==============//
	// constructors //
	//==============//

	public static final ImmutableMap<EDhApiWorldGenerationStep, Integer> WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP;
	public static final int MAX_WORLD_GEN_CHUNK_BORDER_NEEDED;

	static
	{
		DependencySetupDoneCheck.getIsCurrentThreadDistantGeneratorThread = BatchGenerationEnvironment::isCurrentThreadDistantGeneratorThread;

		boolean isTerraFirmaCraft = false;
		try
		{
			Class.forName("net.dries007.tfc.world.TFCChunkGenerator");
			isTerraFirmaCraft = true;
		}
		catch (ClassNotFoundException e)
		{
			//Ignore
		}
		EVENT_LOGGER.info("DH TerraFirmaCraft detection: " + isTerraFirmaCraft);
		ImmutableMap.Builder<EDhApiWorldGenerationStep, Integer> builder = ImmutableMap.builder();
		builder.put(EDhApiWorldGenerationStep.EMPTY, 1);
		builder.put(EDhApiWorldGenerationStep.STRUCTURE_START, 0);
		builder.put(EDhApiWorldGenerationStep.STRUCTURE_REFERENCE, 0);
		builder.put(EDhApiWorldGenerationStep.BIOMES, isTerraFirmaCraft ? 1 : 0);
		builder.put(EDhApiWorldGenerationStep.NOISE, isTerraFirmaCraft ? 1 : 0);
		builder.put(EDhApiWorldGenerationStep.SURFACE, 0);
		builder.put(EDhApiWorldGenerationStep.CARVERS, 0);
		builder.put(EDhApiWorldGenerationStep.LIQUID_CARVERS, 0);
		builder.put(EDhApiWorldGenerationStep.FEATURES, 0);
		builder.put(EDhApiWorldGenerationStep.LIGHT, 0);
		WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP = builder.build();

		// TODO this is a test to see if the additional boarder is actually necessary or not.
		//  If world generators end up having infinite loops or other unexplained issues,
		//  this should be set back to the commented out logic below
		MAX_WORLD_GEN_CHUNK_BORDER_NEEDED = 0;
		//MAX_WORLD_GEN_CHUNK_BORDER_NEEDED = WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP.values().stream().mapToInt(Integer::intValue).max().getAsInt();
	}

	public BatchGenerationEnvironment(IDhServerLevel serverlevel)
	{
		super(serverlevel);
		this.serverlevel = serverlevel;

		EVENT_LOGGER.info("================WORLD_GEN_STEP_INITING=============");

		serverlevel.getServerLevelWrapper().getDimensionType();

		ChunkGenerator generator = ((ServerLevelWrapper) (serverlevel.getServerLevelWrapper())).getLevel().getChunkSource().getGenerator();
		if (!(generator instanceof NoiseBasedChunkGenerator ||
				generator instanceof DebugLevelSource ||
				generator instanceof FlatLevelSource))
		{
			if (generator.getClass().toString().equals("class com.terraforged.mod.chunk.TFChunkGenerator"))
			{
				EVENT_LOGGER.info("TerraForge Chunk Generator detected: [" + generator.getClass() + "], Distant Generation will try its best to support it.");
				EVENT_LOGGER.info("If it does crash, turn Distant Generation off or set it to to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
			else if (generator.getClass().toString().equals("class net.dries007.tfc.world.TFCChunkGenerator"))
			{
				EVENT_LOGGER.info("TerraFirmaCraft Chunk Generator detected: [" + generator.getClass() + "], Distant Generation will try its best to support it.");
				EVENT_LOGGER.info("If it does crash, turn Distant Generation off or set it to to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
			else
			{
				EVENT_LOGGER.warn("Unknown Chunk Generator detected: [" + generator.getClass() + "], Distant Generation May Fail!");
				EVENT_LOGGER.warn("If it does crash, disable Distant Generation or set the Generation Mode to [" + EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY + "].");
			}
		}

		if (MOD_CHECKER.isModLoaded("c2me"))
		{
			EVENT_LOGGER.info("C2ME detected: DH's pre-existing chunk accessing will use methods handled by C2ME.");
			this.pullExistingChunkUsingMcAsyncMethod = true;
		}

		this.params = new GlobalParameters(serverlevel);
	}



	//=================//
	// synchronization //
	//=================//

	/**
	 * This method checks to make sure that all world gen is being
	 * run on DH threads instead of leaking out to other MC threads.
	 * This is done to prevent putting undue stress on MC threads
	 * and prevent potential issues with concurrent processing.
	 */
	public <T> T confirmFutureWasRunSynchronously(CompletableFuture<T> future)
	{
		// this operation should be done since DH wants the
		// operation to be done synchronously
		if (!this.unsafeThreadingRecorded && !future.isDone())
		{
			EVENT_LOGGER.warn(
					"Unsafe MultiThreading in Distant Horizons Chunk Generator. \n" +
					"This can happen if world generation is run on one of Minecraft's thread pools " +
					"instead of the thread DH provided. \n" +
					"This can likely be ignored, however if world generator crashes occur " +
					"setting DH's world generation thread count to 1 may improve stability. ",
					new RuntimeException("Incorrect thread pool use"));
			this.unsafeThreadingRecorded = true;
		}

		// if the future wasn't done synchronously,
		// wait for it to finish so we can continue the world gen
		// lifecycle like normal
		return future.join();
	}

	public void updateAllFutures()
	{
		if (this.unknownExceptionCount > 0)
		{
			if (System.nanoTime() - this.lastExceptionTriggerTime >= EXCEPTION_TIMER_RESET_TIME)
			{
				this.unknownExceptionCount = 0;
			}
		}


		// Update all current out standing jobs
		Iterator<GenerationEvent> iter = this.generationEventList.iterator();
		while (iter.hasNext())
		{
			GenerationEvent event = iter.next();
			if (event.future.isDone())
			{
				if (event.future.isCompletedExceptionally() && !event.future.isCancelled())
				{
					try
					{
						event.future.get(); // Should throw exception
						LodUtil.assertNotReach();
					}
					catch (Exception e)
					{
						this.unknownExceptionCount++;
						this.lastExceptionTriggerTime = System.nanoTime();
						EVENT_LOGGER.error("Batching World Generator event ["+event+"] threw an exception: "+e.getMessage(), e);
					}
				}

				iter.remove();
			}
		}

		if (this.unknownExceptionCount > EXCEPTION_COUNTER_TRIGGER)
		{
			EVENT_LOGGER.error("Too many exceptions in Batching World Generator! Disabling the generator.");
			this.unknownExceptionCount = 0;
			Config.Common.WorldGenerator.enableDistantGeneration.set(false);
		}
	}



	//==================//
	// world generation //
	//==================//

	// TODO this is already being run on a generator thread,
	//  why are we passing in an executor?
	/** @throws RejectedExecutionException if the given {@link Executor} is cancelled. */
	public CompletableFuture<Void> generateLodFromListAsync(GenerationEvent genEvent, Executor executor) throws RejectedExecutionException, InterruptedException
	{
		EVENT_LOGGER.debug("Lod Generate Event: " + genEvent.minPos);

		// Minecraft's generation events expect odd chunk width areas (3x3, 7x7, or 11x11),
		// but DH submits square generation events (4x4).
		// We handle this later, although that handling would need to change if the gen size ever changes.
		LodUtil.assertTrue(genEvent.size % 2 == 0, "Generation events are expected to be an evan number of chunks wide.");

		if (genEvent.generatorMode == EDhApiDistantGeneratorMode.INTERNAL_SERVER)
		{
			return this.generateChunksViaInternalServerAsync(genEvent);
		}

		int borderSize = MAX_WORLD_GEN_CHUNK_BORDER_NEEDED;
		// genEvent.size - 1 converts the even width size to an odd number for MC compatability
		int refSize = (genEvent.size - 1) + (borderSize * 2);
		int refPosX = genEvent.minPos.getX() - borderSize;
		int refPosZ = genEvent.minPos.getZ() - borderSize;

		LightGetterAdaptor lightGetterAdaptor = new LightGetterAdaptor(this.params.level);
		DummyLightEngine dummyLightEngine = new DummyLightEngine(lightGetterAdaptor);



		//====================================//
		// offset and generate odd width area //
		//====================================//

		// reused data between each offset
		Map<DhChunkPos, ChunkLightStorage> chunkSkyLightingByDhPos = Collections.synchronizedMap(new HashMap<>());
		Map<DhChunkPos, ChunkLightStorage> chunkBlockLightingByDhPos = Collections.synchronizedMap(new HashMap<>());
		Map<DhChunkPos, ChunkAccess> generatedChunkByDhPos = Collections.synchronizedMap(new HashMap<>());
		Map<DhChunkPos, ChunkWrapper> chunkWrappersByDhPos = Collections.synchronizedMap(new HashMap<>());

		// futures to handle getting empty chunks
		CompletableFuture<?>[] readFutures =
				// the extra radius of 8 is to account for structure references which need a chunk radius of 8
				getChunkPosToGenerateStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.size, 8)
				.map((chunkPos) -> this.createEmptyOrPreExistingChunkAsync(chunkPos.x, chunkPos.z, chunkSkyLightingByDhPos, chunkBlockLightingByDhPos, generatedChunkByDhPos))
				.toArray(CompletableFuture[]::new);

		// join to prevent an issue where DH queues too many tasks or something(?)
		// also allows file IO to run in parallel so no one thread is waiting on disk IO (this is only an issue when C2ME is present)
		CompletableFuture.allOf(readFutures).join();

		// future chain for generation
		return CompletableFuture.runAsync(() ->
			{
				// offset 1 chunk in both X and Z direction so we can generate an even number of chunks wide
				// while still submitting an odd number width to MC's internal generators
				for (int xOffset = 0; xOffset < 2; xOffset++)
				{
					// final is so the offset can be used in lambdas
					final int xOffsetFinal = xOffset;
					for (int zOffset = 0; zOffset < 2; zOffset++)
					{
						final int zOffsetFinal = zOffset;



						//================//
						// variable setup //
						//================//

						int radius = refSize / 2;
						int centerX = refPosX + radius + xOffset;
						int centerZ = refPosZ + radius + zOffset;

						// get/create the list of chunks we're going to generate
						IEmptyChunkRetrievalFunc fallbackFunc =
								(chunkPosX, chunkPosZ) -> Objects.requireNonNull(
											generatedChunkByDhPos.get(new DhChunkPos(chunkPosX, chunkPosZ)),
											() -> String.format("Requested chunk [%d, %d] unavailable during world generation", chunkPosX, chunkPosZ));

						ArrayGridList<ChunkAccess> regionChunks = new ArrayGridList<>(
								refSize,
								(relX, relZ) -> fallbackFunc.getChunk(
										relX + refPosX + xOffsetFinal,
										relZ + refPosZ + zOffsetFinal));

						ChunkAccess centerChunk = regionChunks.stream()
								.filter((chunk) -> chunk.getPos().x == centerX && chunk.getPos().z == centerZ)
								.findFirst()
								.orElseGet(() -> regionChunks.getFirst());

						genEvent.refreshTimeout();
						DhLitWorldGenRegion region = new DhLitWorldGenRegion(
								centerX, centerZ,
								centerChunk,
								this.params.level, dummyLightEngine, regionChunks,
								ChunkStatus.STRUCTURE_STARTS, radius,
								// this method shouldn't be necessary since we're passing in a pre-populated
								// list of chunks, but just in case
								fallbackFunc
							);
						lightGetterAdaptor.setRegion(region);
						genEvent.threadedParam.makeStructFeat(region, this.params);



						//=============================//
						// create chunk wrappers       //
						// and process existing chunks //
						//=============================//

						ArrayGridList<ChunkWrapper> chunkWrapperList = new ArrayGridList<>(regionChunks.gridSize);
						regionChunks.forEachPos((relX, relZ) ->
						{
							// ArrayGridList's use relative positions and don't have a center position
							// so we need to use the offsetFinal to select the correct position
							DhChunkPos chunkPos = new DhChunkPos(relX + refPosX + xOffsetFinal, relZ + refPosZ + zOffsetFinal);
							ChunkAccess chunk = regionChunks.get(relX, relZ);

							if (chunkWrappersByDhPos.containsKey(chunkPos))
							{
								chunkWrapperList.set(relX, relZ, chunkWrappersByDhPos.get(chunkPos));
							}
							else if (chunk != null)
							{
								// wrap the chunk
								ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, this.serverlevel.getLevelWrapper());
								chunkWrapperList.set(relX, relZ, chunkWrapper);

								// try setting the wrapper's lighting
								if (chunkBlockLightingByDhPos.containsKey(chunkWrapper.getChunkPos()))
								{
									chunkWrapper.setBlockLightStorage(chunkBlockLightingByDhPos.get(chunkWrapper.getChunkPos()));
									chunkWrapper.setSkyLightStorage(chunkSkyLightingByDhPos.get(chunkWrapper.getChunkPos()));
									chunkWrapper.setIsDhBlockLightCorrect(true);
									chunkWrapper.setIsDhSkyLightCorrect(true);
								}

								chunkWrappersByDhPos.put(chunkPos, chunkWrapper);
							}
							else //if (chunk == null)
							{
								LodUtil.assertNotReach("Programmer Error: No chunk found in grid list, position offset is likely wrong.");
							}
						});



						//=================//
						// generate chunks //
						//=================//

						try
						{
							this.generateDirect(genEvent, chunkWrapperList, region);
						}
						catch (InterruptedException e)
						{
							throw new CompletionException(e);
						}

						genEvent.timer.nextEvent("cleanup");
					}
				}

				genEvent.timer.nextEvent("cleanup");



				//=========================//
				// submit generated chunks //
				//=========================//

				Iterator<ChunkPos> iterator = getChunkPosToGenerateStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.size, 0).iterator();
				while (iterator.hasNext())
				{
					ChunkPos pos = iterator.next();
					DhChunkPos dhPos = new DhChunkPos(pos.x, pos.z);
					ChunkWrapper wrappedChunk = chunkWrappersByDhPos.get(dhPos);
					genEvent.resultConsumer.accept(wrappedChunk);
				}

				genEvent.timer.complete();
				genEvent.refreshTimeout();
				if (PREF_LOGGER.canMaybeLog())
				{
					genEvent.threadedParam.perf.recordEvent(genEvent.timer);
					PREF_LOGGER.debugInc(genEvent.timer.toString());
				}
			}, executor);
	}
	/** @param extraRadius in both the positive and negative directions */
	private static Stream<ChunkPos> getChunkPosToGenerateStream(int genMinX, int genMinZ, int width, int extraRadius)
	{
		return StreamSupport.stream(new InclusiveChunkPosStream(genMinX, genMinZ, width, extraRadius), false);

		// method this is replacing
		//return ChunkPos.rangeClosed(
		//		new ChunkPos(genMinX - extraRadius, genMinZ - extraRadius),
		//		new ChunkPos(genMinX + (width - 1) + extraRadius, genMinZ + (width - 1) + extraRadius)
		//);
	}
	/**
	 * If the given chunk pos already exists in the world, that chunk will be returned,
	 * otherwise this will return an empty chunk.
	 */
	private CompletableFuture<ChunkAccess> createEmptyOrPreExistingChunkAsync(
			int x, int z,
			Map<DhChunkPos, ChunkLightStorage> chunkSkyLightingByDhPos,
			Map<DhChunkPos, ChunkLightStorage> chunkBlockLightingByDhPos,
			Map<DhChunkPos, ChunkAccess> generatedChunkByDhPos)
	{
		ChunkPos chunkPos = new ChunkPos(x, z);
		DhChunkPos dhChunkPos = new DhChunkPos(x, z);

		if (generatedChunkByDhPos.containsKey(dhChunkPos))
		{
			return CompletableFuture.completedFuture(generatedChunkByDhPos.get(dhChunkPos));
		}

		return this.getChunkNbtDataAsync(chunkPos)
			.thenApply((chunkData) ->
			{
				ChunkAccess newChunk = this.loadOrMakeChunk(chunkPos, chunkData);

				if (Config.Common.LodBuilding.pullLightingForPregeneratedChunks.get())
				{
					// attempt to get chunk lighting
					ChunkLoader.CombinedChunkLightStorage combinedLights = ChunkLoader.readLight(newChunk, chunkData);
					if (combinedLights != null)
					{
						chunkSkyLightingByDhPos.put(dhChunkPos, combinedLights.skyLightStorage);
						chunkBlockLightingByDhPos.put(dhChunkPos, combinedLights.blockLightStorage);
					}
				}

				return newChunk;
			})
			// separate handle so we can cleanly handle missing chunks and/or thrown errors
			.handle((newChunk, throwable) ->
			{
				if (newChunk != null)
				{
					return newChunk;
				}
				else
				{
					return CreateEmptyChunk(this.params.level, chunkPos);
				}
			})
			.thenApply((newChunk) ->
			{
				generatedChunkByDhPos.put(dhChunkPos, newChunk);
				return newChunk;
			});
	}
	// TODO FIXME this method can be called up to 25 times for the same chunk position, why?
	private CompletableFuture<CompoundTag> getChunkNbtDataAsync(ChunkPos chunkPos)
	{
		ServerLevel level = this.params.level;

		//if (true)
		//	return CompletableFuture.completedFuture(null);

		// TODO disabling drastically reduces GC overhead (2Gb/s -> 1GB/s)

		try
		{
			IOWorker ioWorker = level.getChunkSource().chunkMap.worker;

			#if MC_VER <= MC_1_18_2
			return CompletableFuture.completedFuture(ioWorker.load(chunkPos));
			#else

			// storage will be null if C2ME is installed
			if (!this.pullExistingChunkUsingMcAsyncMethod && ioWorker.storage != null)
			{
				try
				{
					RegionFileStorage storage = this.params.level.getChunkSource().chunkMap.worker.storage;
					RegionFileStorageExternalCache cache = this.getOrCreateRegionFileCache(storage);
					return CompletableFuture.completedFuture(cache.read(chunkPos));
				}
				catch (NullPointerException e)
				{
					// this shouldn't happen, if anything is null it should be
					// ioWorker.storage
					// but just in case
					EVENT_LOGGER.error("Unexpected issue pulling pre-existing chunk ["+chunkPos+"], falling back to async chunk pulling. This may cause server-tick lag.", e);
					this.pullExistingChunkUsingMcAsyncMethod = true;

					// try again now using the async method
					return this.getChunkNbtDataAsync(chunkPos);
				}
			}
			else
			{
				// log if we unexpectedly weren't able to run the sync chunk pulling
				if (!this.pullExistingChunkUsingMcAsyncMethod)
				{
					// this shouldn't happen, but just in case
					EVENT_LOGGER.info("Unable to pull pre-existing chunk using synchronous method. Falling back to async method. this may cause server-tick lag.");
					this.pullExistingChunkUsingMcAsyncMethod = true;
				}

				//GET_CHUNK_COUNT_REF.incrementAndGet();

				// When running in vanilla MC on versions before 1.21.4,
				// DH would attempt to run loadAsync on this same thread via a threading mixin,
				// to prevent causing lag on the server thread.
				// However, if a mod like C2ME is installed this will run on a C2ME thread instead.
				return ioWorker.loadAsync(chunkPos)
						.thenApply(optional ->
						{
							// Debugging note:
							// If there are reports of extreme memory use when C2ME is installed, that probably means
							// this method is queuing a lot of tasks (1,000+), which causes C2ME to explode.

							//GET_CHUNK_COUNT_REF.decrementAndGet();
							//PREF_LOGGER.info("chunk getter count ["+F3Screen.NUMBER_FORMAT.format(GET_CHUNK_COUNT_REF.get())+"]");
							return optional.orElse(null);
						})
						.exceptionally((throwable) ->
						{
							// unwrap the CompletionException if necessary
							Throwable actualThrowable = throwable;
							while (actualThrowable instanceof CompletionException completionException)
							{
								actualThrowable = completionException.getCause();
							}

							LOAD_LOGGER.warn("DistantHorizons: Couldn't load or make chunk ["+chunkPos+"], error: ["+actualThrowable.getMessage()+"].", actualThrowable);
							return null;
						});
			}
			#endif
		}
		catch (Exception e)
		{
			LOAD_LOGGER.warn("DistantHorizons: Couldn't load or make chunk [" + chunkPos + "]. Error: [" + e.getMessage() + "].", e);
			return CompletableFuture.completedFuture(null);
		}
	}
	private Chunk loadOrMakeChunk(ChunkPos chunkPos, CompoundTag chunkData)
	{
		WorldServer level = this.params.level;

		if (chunkData == null)
		{
			return CreateEmptyChunk(level, chunkPos);
		}
		else
		{
			try
			{
				LOAD_LOGGER.debug("DistantHorizons: Loading chunk [" + chunkPos + "] from disk.");

				@Nullable
                Chunk chunk = ChunkLoader.read(level, chunkPos, chunkData);
				if (chunk != null)
				{
					if (Config.Common.LodBuilding.assumePreExistingChunksAreFinished.get())
					{
						// Sometimes the chunk status is wrong
						// (this might be an issue with some versions of chunky)
						// which can cause issues with some world gen steps re-running and locking up
						ChunkWrapper.trySetStatus(chunk, ChunkStatus.FULL);
					}
				}
				else
				{
					chunk = CreateEmptyChunk(level, chunkPos);
				}
				return chunk;
			}
			catch (Exception e)
			{
				LOAD_LOGGER.error(
						"DistantHorizons: couldn't load or make chunk at [" + chunkPos + "]." +
								"Please try optimizing your world to fix this issue. \n" +
								"World optimization can be done from the singleplayer world selection screen.\n" +
								"Error: [" + e.getMessage() + "]."
						, e);

				return CreateEmptyChunk(level, chunkPos);
			}
		}
	}
	private static ProtoChunk CreateEmptyChunk(ServerLevel level, ChunkPos chunkPos)
	{
		#if MC_VER <= MC_1_16_5
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY);
		#elif MC_VER <= MC_1_17_1
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level);
		#elif MC_VER <= MC_1_19_2
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), null);
		#elif MC_VER <= MC_1_19_4
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
		#elif MC_VER < MC_1_21_3
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
		#else
		return new ProtoChunk(chunkPos, UpgradeData.EMPTY, level, level.registryAccess().lookupOrThrow(Registries.BIOME), null);
		#endif
	}



	private CompletableFuture<Void> generateChunksViaInternalServerAsync(GenerationEvent genEvent) throws InterruptedException
	{
		genEvent.timer.nextEvent("requestFromServer");
		LinkedBlockingQueue<Runnable> runnableQueue = new LinkedBlockingQueue<>();

		Map<DhChunkPos, ChunkWrapper> chunkWrappersByDhPos = Collections.synchronizedMap(new HashMap<>());



		//===================================//
		// create generation queue runnables //
		//===================================//

		// request each chunk pos from the server
		CompletableFuture<?>[] requestFutures =
			getChunkPosToGenerateStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.size, 0)
				.map(chunkPos ->
				{
					return requestChunkFromServerAsync(this.params.level, chunkPos, true)
						.whenCompleteAsync((chunk, throwable) ->
						{
							// unwrap the CompletionException if necessary
							Throwable actualThrowable = throwable;
							while (actualThrowable instanceof CompletionException)
							{
								actualThrowable = actualThrowable.getCause();
							}

							if (throwable != null)
							{
								LOAD_LOGGER.warn("DistantHorizons: Couldn't load chunk [" + chunkPos + "] from server, error: [" + actualThrowable.getMessage() + "].", actualThrowable);
							}

							if (chunk != null)
							{
								ChunkWrapper chunkWrapper = new ChunkWrapper(chunk, this.serverlevel.getLevelWrapper());
								chunkWrappersByDhPos.put(new DhChunkPos(chunkPos.x, chunkPos.z), chunkWrapper);
							}
						}, runnableQueue::add);
				})
				.toArray(CompletableFuture[]::new);

		// handle each generated chunk
		CompletableFuture<Void> processGeneratedChunksFuture =
			CompletableFuture.allOf(requestFutures)
				.whenCompleteAsync((voidObj, throwable) ->
				{
					// generate chunk lighting using DH's lighting engine
					genEvent.timer.nextEvent("light");
					int maxSkyLight = this.serverlevel.getServerLevelWrapper().hasSkyLight() ? LodUtil.MAX_MC_LIGHT : LodUtil.MIN_MC_LIGHT;

					ArrayList<IChunkWrapper> generatedChunks = new ArrayList<>(chunkWrappersByDhPos.values());
					for (IChunkWrapper iChunkWrapper : generatedChunks)
					{
						((ChunkWrapper) iChunkWrapper).recalculateDhHeightMapsIfNeeded();

						// pre-generated chunks should have lighting but new ones won't
						if (!iChunkWrapper.isDhBlockLightingCorrect())
						{
							DhLightingEngine.INSTANCE.bakeChunkBlockLighting(iChunkWrapper, generatedChunks, maxSkyLight);
						}

						this.serverlevel.updateBeaconBeamsForChunk(iChunkWrapper, generatedChunks);
					}

					genEvent.timer.nextEvent("cleanup");
					for (IChunkWrapper iChunkWrapper : generatedChunks)
					{
						genEvent.resultConsumer.accept(iChunkWrapper);
					}
				}, runnableQueue::add)
				.whenCompleteAsync((unused, throwable) ->
				{
					// cleanup
					// release the generated chunks

					Iterator<ChunkPos> iterator = getChunkPosToGenerateStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.size, 0).iterator();
					while (iterator.hasNext())
					{
						ChunkPos chunkPos = iterator.next();
						releaseChunkToServer(this.params.level, chunkPos, true);
					}

					genEvent.timer.complete();
					genEvent.refreshTimeout();
					if (PREF_LOGGER.canMaybeLog())
					{
						genEvent.threadedParam.perf.recordEvent(genEvent.timer);
						PREF_LOGGER.debugInc(genEvent.timer.toString());
					}
				});

		processGeneratedChunksFuture.whenCompleteAsync((unused, throwable) -> { }, runnableQueue::add); // trigger wakeup



		//===============//
		// run each step //
		//===============//

		while (!processGeneratedChunksFuture.isDone())
		{
			try
			{
				Runnable command = runnableQueue.poll(1, TimeUnit.SECONDS);
				if (command != null)
				{
					command.run();
				}
			}
			catch (InterruptedException e)
			{
				// interrupted, release chunk to server
				Iterator<ChunkPos> iterator = getChunkPosToGenerateStream(genEvent.minPos.getX(), genEvent.minPos.getZ(), genEvent.size, 0).iterator();
				while (iterator.hasNext())
				{
					ChunkPos chunkPos = iterator.next();
					releaseChunkToServer(this.params.level, chunkPos, true);
				}

				throw e;
			}
		}

		return processGeneratedChunksFuture;
	}
	/** @param generateUpToFeatures if false this generate the chunk up to "FULL" status */
	private static CompletableFuture<ChunkAccess> requestChunkFromServerAsync(ServerLevel level, ChunkPos pos, boolean generateUpToFeatures)
	{
		return CompletableFuture.supplyAsync(() ->
		{
			int chunkLevel;
			#if MC_VER <= MC_1_19_4
			// 33 is equivalent to FULL Chunk
			chunkLevel = generateUpToFeatures ? 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES) : 33;
			#else
			// 33 is equivalent to FULL Chunk
			chunkLevel = generateUpToFeatures ? ChunkLevel.byStatus(ChunkStatus.FEATURES) : 33;
			#endif

			level.getChunkSource().distanceManager.addTicket(DH_SERVER_GEN_TICKET, pos, chunkLevel, pos);
			level.getChunkSource().distanceManager.runAllUpdates(level.getChunkSource().chunkMap); // probably not the most optimal to run updates here, but fast enough
			ChunkHolder holder = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.toLong());
			if (holder == null)
			{
				throw new IllegalStateException("No chunk holder after ticket has been added");
			}

			#if MC_VER <= MC_1_20_4
			return holder.getOrScheduleFuture(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.left().orElseThrow(() -> new RuntimeException(result.right().get().toString()))); // can throw if the server is shutting down
			#elif MC_VER <= MC_1_20_6
			return holder.getOrScheduleFuture(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.orElseThrow(() -> new RuntimeException(result.toString()))); // can throw if the server is shutting down
			#else
			return holder.scheduleChunkGenerationTask(ChunkStatus.FEATURES, level.getChunkSource().chunkMap)
					.thenApply(result -> result.orElseThrow(() -> new RuntimeException(result.getError()))); // can throw if the server is shutting down
			#endif

		}, level.getChunkSource().chunkMap.mainThreadExecutor).thenCompose(Function.identity());
	}
	/** @param chunkWasGeneratedUpToFeatures if false this assumes the chunk was generated to "FULL" status */
	private static void releaseChunkToServer(ServerLevel level, ChunkPos pos, boolean chunkWasGeneratedUpToFeatures)
	{
		level.getChunkSource().chunkMap.mainThreadExecutor.execute(() ->
		{
			try
			{
				int chunkLevel;
				#if MC_VER <= MC_1_19_4
				// 33 is equivalent to FULL Chunk
				chunkLevel = chunkWasGeneratedUpToFeatures ? 33 + ChunkStatus.getDistance(ChunkStatus.FEATURES) : 33;
				#else
				// 33 is equivalent to FULL Chunk
				chunkLevel = chunkWasGeneratedUpToFeatures ? ChunkLevel.byStatus(ChunkStatus.FEATURES) : 33;
				#endif

				level.getChunkSource().distanceManager.removeTicket(DH_SERVER_GEN_TICKET, pos, chunkLevel, pos);

				// mitigate OOM issues in vanilla chunk system: see https://github.com/pop4959/Chunky/pull/383
				level.getChunkSource().chunkMap.tick(() -> false);
				#if MC_VER > MC_1_16_5
				level.entityManager.tick();
				#endif
			}
			catch (Exception e)
			{
				EVENT_LOGGER.warn("Failed to release chunk back to internal server. Error: ["+e.getMessage()+"]", e);
			}
		});
	}

	public void generateDirect(
			GenerationEvent genEvent, ArrayGridList<ChunkWrapper> chunkWrappersToGenerate,
			DhLitWorldGenRegion region) throws InterruptedException
	{
		if (Thread.interrupted())
		{
			return;
		}

		try
		{
			chunkWrappersToGenerate.forEach((chunkWrapper) ->
			{
				ChunkAccess chunk = chunkWrapper.getChunk();
				if (chunk instanceof ProtoChunk)
				{
					ProtoChunk protoChunk = ((ProtoChunk) chunk);

					protoChunk.setLightEngine(region.getLightEngine());
				}
			});

			EDhApiWorldGenerationStep step = genEvent.targetGenerationStep;
			if (step == EDhApiWorldGenerationStep.EMPTY)
			{
				// shouldn't normally happen but is here for consistency with the other world gen steps
				return;
			}

			genEvent.timer.nextEvent("structStart");
			throwIfThreadInterrupted();
			this.stepStructureStart.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.STRUCTURE_START));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.STRUCTURE_START)
			{
				return;
			}

			genEvent.timer.nextEvent("structRef");
			throwIfThreadInterrupted();
			this.stepStructureReference.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.STRUCTURE_REFERENCE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.STRUCTURE_REFERENCE)
			{
				return;
			}

			genEvent.timer.nextEvent("biome");
			throwIfThreadInterrupted();
			this.stepBiomes.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.BIOMES));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.BIOMES)
			{
				return;
			}

			genEvent.timer.nextEvent("noise");
			throwIfThreadInterrupted();
			this.stepNoise.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.NOISE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.NOISE)
			{
				return;
			}

			genEvent.timer.nextEvent("surface");
			throwIfThreadInterrupted();
			this.stepSurface.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.SURFACE));
			genEvent.refreshTimeout();
			if (step == EDhApiWorldGenerationStep.SURFACE)
			{
				return;
			}

			genEvent.timer.nextEvent("carver");
			throwIfThreadInterrupted();
			// caves can generally be ignored since they aren't generally visible from far away
			if (step == EDhApiWorldGenerationStep.CARVERS)
			{
				return;
			}

			genEvent.timer.nextEvent("feature");
			throwIfThreadInterrupted();
			this.stepFeatures.generateGroup(genEvent.threadedParam, region, GetCutoutFrom(chunkWrappersToGenerate, EDhApiWorldGenerationStep.FEATURES));
			genEvent.refreshTimeout();
		}
		finally
		{
			genEvent.timer.nextEvent("light");

			// generate lighting using DH's lighting engine

			int maxSkyLight = this.serverlevel.getServerLevelWrapper().hasSkyLight() ? 15 : 0;

			// only light generated chunks,
			// attempting to light un-generated chunks will cause lighting issues on bordering generated chunks
			ArrayList<IChunkWrapper> iChunkWrapperList = new ArrayList<>();
			for (int i = 0; i < chunkWrappersToGenerate.size(); i++) // regular for loop since enhanced for loops increase GC pressure slightly
			{
				ChunkWrapper chunkWrapper = chunkWrappersToGenerate.get(i);
				if (chunkWrapper.getStatus() != ChunkStatus.EMPTY)
				{
					iChunkWrapperList.add(chunkWrapper);
				}
			}

			// light each chunk in the list
			for (int i = 0; i < iChunkWrapperList.size(); i++)
			{
				ChunkWrapper centerChunk = (ChunkWrapper) iChunkWrapperList.get(i);
				if (centerChunk == null)
				{
					continue;
				}

				throwIfThreadInterrupted();

				// make sure the height maps are all properly generated
				// if this isn't done everything else afterward may fail
				Heightmap.primeHeightmaps(centerChunk.getChunk(), ChunkStatus.FEATURES.heightmapsAfter());
				centerChunk.recalculateDhHeightMapsIfNeeded();

				// pre-generated chunks should have lighting but new ones won't
				if (!centerChunk.isDhBlockLightingCorrect())
				{
					DhLightingEngine.INSTANCE.bakeChunkBlockLighting(centerChunk, iChunkWrapperList, maxSkyLight);
				}

				this.serverlevel.updateBeaconBeamsForChunk(centerChunk, iChunkWrapperList);
			}

			genEvent.refreshTimeout();
		}
	}
	private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, int border) { return new ArrayGridList<>(total, border, total.gridSize - border); }
	//private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, EDhApiWorldGenerationStep step) { return GetCutoutFrom(total, MaxBorderNeeded - WORLD_GEN_CHUNK_BORDER_NEEDED_BY_GEN_STEP.get(step)); }
	private static <T> ArrayGridList<T> GetCutoutFrom(ArrayGridList<T> total, EDhApiWorldGenerationStep step) { return GetCutoutFrom(total, 0); }


	@Override
	public int getEventCount() { return this.generationEventList.size(); }

	@Override
	public void stop()
	{
		EVENT_LOGGER.info(BatchGenerationEnvironment.class.getSimpleName() + " shutting down...");

		EVENT_LOGGER.info("Canceling in progress generation event futures...");
		Iterator<GenerationEvent> iter = this.generationEventList.iterator();
		while (iter.hasNext())
		{
			GenerationEvent event = iter.next();
			event.future.cancel(true);
			iter.remove();
		}

		// clear the chunk cache
		RegionFileStorageExternalCache regionStorage = this.regionFileStorageCacheRef.get();
		if (regionStorage != null)
		{
			try
			{
				regionStorage.close();
			}
			catch (IOException e)
			{
				EVENT_LOGGER.error("Failed to close region file storage cache!", e);
			}
		}

		EVENT_LOGGER.info(BatchGenerationEnvironment.class.getSimpleName() + " shutdown complete.");
	}

	@Override
	public CompletableFuture<Void> generateChunks(
			int minX, int minZ, int genSize,
			EDhApiDistantGeneratorMode generatorMode, EDhApiWorldGenerationStep targetStep,
			ExecutorService worldGeneratorThreadPool, Consumer<IChunkWrapper> resultConsumer)
	{
		//System.out.println("GenerationEvent: "+genSize+"@"+minX+","+minZ+" "+targetStep);

		// TODO: Check event overlap via e.tooClose()
		GenerationEvent genEvent = GenerationEvent.startEvent(new DhChunkPos(minX, minZ), genSize, this, generatorMode, targetStep, resultConsumer, worldGeneratorThreadPool);
		this.generationEventList.add(genEvent);
		return genEvent.future;
	}



	//================//
	// helper methods //
	//================//

	/**
	 * Called before code that may run for an extended period of time. <br>
	 * This is necessary to allow canceling world gen since waiting
	 * for some world gen requests to finish can take a while.
	 */
	public static void throwIfThreadInterrupted() throws InterruptedException
	{
		if (Thread.interrupted())
		{
			throw new InterruptedException(BatchGenerationEnvironment.class.getSimpleName() + " task interrupted.");
		}
	}



	//================//
	// helper classes //
	//================//

	@FunctionalInterface
	public interface IEmptyChunkRetrievalFunc
	{
		ChunkAccess getChunk(int chunkPosX, int chunkPosZ);
	}

	private static class InclusiveChunkPosStream extends Spliterators.AbstractSpliterator<ChunkPos>
	{
		private final int minX;
		private final int minZ;

		private final int maxX;
		private final int maxZ;


		/** current X pos */
		int x;
		/** current Z pos */
		private int z;



		//=============//
		// constructor //
		//=============//

		protected InclusiveChunkPosStream(int genMinX, int genMinZ, int width, int extraRadius)
		{
			super(getCount(width, extraRadius), Spliterator.SIZED);

			this.minX = genMinX - extraRadius;
			this.minZ = genMinZ - extraRadius;

			this.maxX = genMinX + (width - 1) + extraRadius;
			this.maxZ = genMinZ + (width - 1) + extraRadius;

			// X starts at 1 minus the minX so we can immediately re-add 1 in the tryAdvance() loop
			this.x = this.minX - 1;
			this.z = this.minZ;
		}
		private static int getCount(int width, int extraRadius)
		{
			int widthPlusExtra = width + (extraRadius * 2);
			return widthPlusExtra * widthPlusExtra;
		}



		//=================//
		// iterator method //
		//=================//

		public boolean tryAdvance(Consumer<? super ChunkPos> consumer)
		{
			if (this.x == this.maxX && this.z == this.maxZ)
			{
				// the last returned position was the final valid position
				return false;
			}

			if (this.x == this.maxX)
			{
				// we reached the max X position, loop back around in the next Z row
				this.x = this.minX;
				this.z++;
			}
			else
			{
				this.x++;
			}

			consumer.accept(new ChunkPos(this.x, this.z));
			return true;
		}
	}

	public static class PerfCalculator
	{
		private static final String[] TIME_NAMES = {
				"total",
				"setup",
				"structStart",
				"structRef",
				"biome",
				"noise",
				"surface",
				"carver",
				"feature",
				"light",
				"cleanup",
				//"lodCreation" (No longer used)
		};

		public static final int SIZE = 50;
		ArrayList<Rolling> times = new ArrayList<>();

		public PerfCalculator()
		{
			for (int i = 0; i < 11; i++)
			{
				times.add(new Rolling(SIZE));
			}
		}

		public void recordEvent(EventTimer event)
		{
			for (EventTimer.Event e : event.events)
			{
				String name = e.name;
				int index = Arrays.asList(TIME_NAMES).indexOf(name);
				if (index == -1) continue;
				times.get(index).add(e.timeNs);
			}
			times.get(0).add(event.getTotalTimeNs());
		}

		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < times.size(); i++)
			{
				if (times.get(i).getAverage() == 0) continue;
				sb.append(TIME_NAMES[i]).append(": ").append(times.get(i).getAverage()).append("\n");
			}
			return sb.toString();
		}

	}


}
