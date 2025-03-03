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

package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.generation.IFullDataSourceRetrievalQueue;
import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListCheckout;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class GeneratedFullDataSourceProvider extends FullDataSourceProviderV2 implements IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** 
	 * Having this number too high causes the system to become overwhelmed by
	 * world gen requests and other jobs won't be done. <br>
	 * IE: LODs won't update or render because world gen is hogging the CPU.
	 * <br><br>
	 * TODO this should be dynamically allocated based on CPU load
	 *  and abilities.
	 */
	public static final int MAX_WORLD_GEN_REQUESTS_PER_THREAD = 20; 
	
	
	private final AtomicReference<IFullDataSourceRetrievalQueue> worldGenQueueRef = new AtomicReference<>(null);
	private final ArrayList<IOnWorldGenCompleteListener> onWorldGenTaskCompleteListeners = new ArrayList<>();
	
	protected final DelayedFullDataSourceSaveCache delayedFullDataSourceSaveCache = new DelayedFullDataSourceSaveCache(this::onDataSourceSaveAsync, 10_000);
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public GeneratedFullDataSourceProvider(IDhLevel level, ISaveStructure saveStructure) { super(level, saveStructure); }
	public GeneratedFullDataSourceProvider(IDhLevel level, ISaveStructure saveStructure, @Nullable File saveDirOverride) { super(level, saveStructure, saveDirOverride); }
	
	
	
	//=================//
	// event listeners //
	//=================//
	
	public void addWorldGenCompleteListener(IOnWorldGenCompleteListener listener) 
	{
		synchronized (this.onWorldGenTaskCompleteListeners)
		{
			this.onWorldGenTaskCompleteListeners.add(listener);
		}
	}
	public void removeWorldGenCompleteListener(IOnWorldGenCompleteListener listener) 
	{
		synchronized (this.onWorldGenTaskCompleteListeners)
		{
			this.onWorldGenTaskCompleteListeners.remove(listener);
		}
	}
	
	
	
	//========//
	// events //
	//========//
	
	private void onWorldGenTaskComplete(WorldGenResult genTaskResult, Throwable exception)
	{
		if (exception != null)
		{
			// don't log shutdown exceptions
			if (!(exception instanceof CancellationException || exception.getCause() instanceof CancellationException))
			{
				LOGGER.error("Uncaught Gen Task Exception at ["+genTaskResult.pos+"], error: ["+exception.getMessage()+"].", exception);
			}
		}
		else if (genTaskResult.success)
		{
			this.fireOnGenPosSuccessListeners(genTaskResult.pos);
			return;
		}
		else
		{
			// generation didn't complete
			LOGGER.debug("Gen Task Failed at " + genTaskResult.pos);
		}
		
		
		// if the generation task was split up into smaller positions, add the on-complete event to them
		for (CompletableFuture<WorldGenResult> siblingFuture : genTaskResult.childFutures)
		{
			siblingFuture.whenComplete((siblingGenTaskResult, siblingEx) -> this.onWorldGenTaskComplete(siblingGenTaskResult, siblingEx));
		}
	}
	
	// TODO only fire after the section has finished generated or once every X seconds
	private void fireOnGenPosSuccessListeners(long pos)
	{
		// synchronized to prevent a rare issue where the world generator is being shut down while this listener is firing
		synchronized (this.onWorldGenTaskCompleteListeners)
		{
			// fire the event listeners 
			for (IOnWorldGenCompleteListener listener : this.onWorldGenTaskCompleteListeners)
			{
				listener.onWorldGenTaskComplete(pos);
			}
		}
	}
	
	
	
	//===================================//
	// world gen (data source retrieval) //
	//===================================//
	
	public byte lowestDataDetailLevel()
	{
		IFullDataSourceRetrievalQueue fullDataSourceRetrievalQueue = this.worldGenQueueRef.get();
		if (fullDataSourceRetrievalQueue == null)
		{
			return DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
		}
		
		return (byte) (DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + fullDataSourceRetrievalQueue.lowestDataDetail());
	}
	
	/**
	 * Assigns the queue for handling world gen and does first time setup as well. <br> 
	 * Assumes there isn't a pre-existing queue. 
	 */
	public void setWorldGenerationQueue(IFullDataSourceRetrievalQueue newWorldGenQueue)
	{
		boolean oldQueueExists = this.worldGenQueueRef.compareAndSet(null, newWorldGenQueue);
		LodUtil.assertTrue(oldQueueExists, "previous world gen queue is still here!");
		LOGGER.info("Set world gen queue for level [" + this.level.getLevelWrapper().getDhIdentifier() + "].");
	}
	
	@Override
	public boolean canRetrieveMissingDataSources() { return true; }
	
	@Override
	public void setEstimatedRemainingRetrievalChunkCount(int newCount) 
	{
		IFullDataSourceRetrievalQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue != null)
		{
			worldGenQueue.setRetrievalEstimatedRemainingChunkCount(newCount);
		}
	}
	
	@Override
	public boolean canQueueRetrieval() { return this.canQueueRetrieval(false); }
	public boolean canQueueRetrieval(boolean pruneWaitingTasksAboveLimit)
	{
		if (!super.canQueueRetrieval())
		{
			return false;
		}
		
		
		IFullDataSourceRetrievalQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue == null)
		{
			// we can't queue anything if the world generator isn't set up yet
			return false;
		}
		
		
		PriorityTaskPicker.Executor fileExecutor = ThreadPoolUtil.getFileHandlerExecutor();
		if (fileExecutor == null || fileExecutor.getQueueSize() >= getMaxUpdateTaskCount() / 2)
		{
			// don't queue additional world gen requests if the file handler is overwhelmed,
			// otherwise LODs may not load in properly
			return false;
		}
		
		
		int maxQueuedChunkCount = MAX_WORLD_GEN_REQUESTS_PER_THREAD * Config.Common.MultiThreading.numberOfThreads.get(); // for now we're just using the same logic as the world gen threads, it works well enough
		if (SharedApi.INSTANCE.getQueuedChunkUpdateCount() >= maxQueuedChunkCount)
		{
			// don't queue additional world gen requests if there are
			// a lot of chunks waiting to update
			// (this is done to reduce thread starvation for chunk updates)
			return false;
		}
		
		
		int maxWorldGenQueueCount = MAX_WORLD_GEN_REQUESTS_PER_THREAD * Config.Common.MultiThreading.numberOfThreads.get();

		if (this.delayedFullDataSourceSaveCache.getUnsavedCount() >= maxWorldGenQueueCount)
		{
			// don't queue additional world gen requests if there are
			// a lot of data sources in memory 
			// (this is done to prevent infinite memory growth)
			
			// clear out the data sources that are in memory so
			// we can start queuing new world gen tasks
			this.delayedFullDataSourceSaveCache.flush();
			
			return false;
		}
		
		int availableTaskSlots = maxWorldGenQueueCount - worldGenQueue.getWaitingTaskCount();
		if (availableTaskSlots <= 0)
		{
			if (pruneWaitingTasksAboveLimit)
			{
				AtomicInteger tasksToCancel = new AtomicInteger(-availableTaskSlots + 1);
				worldGenQueue.removeRetrievalRequestIf(x -> tasksToCancel.getAndDecrement() > 0);
			}
			else
			{
				// don't queue additional world gen requests beyond the max allotted count
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public CompletableFuture<WorldGenResult> queuePositionForRetrieval(Long genPos)
	{
		IFullDataSourceRetrievalQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue == null)
		{
			return null;
		}
		
		WorldGenTaskTracker genTaskTracker = new WorldGenTaskTracker(genPos);
		CompletableFuture<WorldGenResult> worldGenFuture = worldGenQueue.submitRetrievalTask(genPos, (byte) (DhSectionPos.getDetailLevel(genPos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL), genTaskTracker);
		worldGenFuture.whenComplete((genTaskResult, ex) ->
		{
			//LOGGER.info("gen task complete ["+DhSectionPos.toString(genPos)+"]");
			//this.onWorldGenTaskComplete(genTaskResult, ex);
		});
		
		return worldGenFuture;
	}
	
	@Override
	protected void updateDataSourceAtPos(long updatePos, @NotNull FullDataSourceV2 inputData, boolean lockOnUpdatePos)
	{
		super.updateDataSourceAtPos(updatePos, inputData, lockOnUpdatePos);
		
		//if (SharedApi.getEnvironment() != EWorldEnvironment.CLIENT_ONLY)
		//	LOGGER.info("updated ["+DhSectionPos.toString(updatePos)+"]");
		
		this.onWorldGenTaskComplete(WorldGenResult.CreateSuccess(updatePos), null);
	}
	
	@Override
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf)
	{
		IFullDataSourceRetrievalQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue != null)
		{
			worldGenQueue.removeRetrievalRequestIf(removeIf);
		}
	}
	
	@Override
	public void clearRetrievalQueue() { this.worldGenQueueRef.set(null); }
	
	
	public boolean isFullyGenerated(ByteArrayList columnGenerationSteps)
	{
		return IntStream.range(0, columnGenerationSteps.size())
				.noneMatch(i ->
				{
					byte value = columnGenerationSteps.getByte(i);
					return value == EDhApiWorldGenerationStep.EMPTY.value
							|| value == EDhApiWorldGenerationStep.DOWN_SAMPLED.value;
				});
	}
	
	public static final PhantomArrayListPool ARRAY_LIST_POOL = new PhantomArrayListPool("Generated Provider");
	
	
	@Override
	public LongArrayList getPositionsToRetrieve(Long pos)
	{
		IFullDataSourceRetrievalQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue == null)
		{
			return null;
		}
		
		
		// don't check any child positions if this position is already fully generated 
		if (this.repo.existsWithKey(pos))
		{
			try(PhantomArrayListCheckout checkout = ARRAY_LIST_POOL.checkoutArrays(1, 0, 0))
			{
				ByteArrayList columnGenStepArray = checkout.getByteArray(0, FullDataSourceV2.WIDTH*FullDataSourceV2.WIDTH);
				this.repo.getColumnGenerationStepForPos(pos, columnGenStepArray);
				if (!columnGenStepArray.isEmpty())
				{
					boolean positionFullyGenerated = true;
					
					// check if any positions are ungenerated
					for (int i = 0; i < columnGenStepArray.size(); i++)
					{
						if (columnGenStepArray.getByte(i) == EDhApiWorldGenerationStep.EMPTY.value
							|| columnGenStepArray.getByte(i) == EDhApiWorldGenerationStep.DOWN_SAMPLED.value)
						{
							positionFullyGenerated = false;
							break;
						}
					}
					
					if (positionFullyGenerated)
					{
						return new LongArrayList();
					}
				}
			}
		}
		
		
		
		// this section is missing one or more columns, queue the missing ones for generation.
		// TODO speed up this logic by only checking ungenerated columns
		LongArrayList generationList = new LongArrayList();
		
		byte lowestGeneratorDetailLevel = (byte) Math.min(
				worldGenQueue.lowestDataDetail()  + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL,
				DhSectionPos.getDetailLevel(pos));
		
		DhSectionPos.forEachChildAtDetailLevel(pos, lowestGeneratorDetailLevel, (genPos) ->
		{
			if (!this.repo.existsWithKey(genPos))
			{
				// nothing exists for this position, it needs generation
				generationList.add(genPos);
			}
			else
			{
				
				EDhApiWorldGenerationStep currentMinWorldGenStep = EDhApiWorldGenerationStep.LIGHT;
				try(PhantomArrayListCheckout checkout = ARRAY_LIST_POOL.checkoutArrays(1, 0, 0))
				{
					ByteArrayList columnGenerationSteps = checkout.getByteArray(0, FullDataSourceV2.WIDTH*FullDataSourceV2.WIDTH);
					this.repo.getColumnGenerationStepForPos(genPos, columnGenerationSteps);
					if (columnGenerationSteps.isEmpty())
					{
						// shouldn't happen, but just in case
						return;
					}
					
					
					
					checkWorldGenLoop:
					for (int x = 0; x < FullDataSourceV2.WIDTH; x++)
					{
						for (int z = 0; z < FullDataSourceV2.WIDTH; z++)
						{
							int index = FullDataSourceV2.relativePosToIndex(x, z);
							byte genStepValue = columnGenerationSteps.getByte(index);
							
							if (genStepValue < currentMinWorldGenStep.value)
							{
								EDhApiWorldGenerationStep newWorldGenStep = EDhApiWorldGenerationStep.fromValue(genStepValue);
								if (newWorldGenStep != null && newWorldGenStep.value < currentMinWorldGenStep.value)
								{
									currentMinWorldGenStep = newWorldGenStep;
								}
							}
							
							if (currentMinWorldGenStep == EDhApiWorldGenerationStep.EMPTY 
								|| currentMinWorldGenStep == EDhApiWorldGenerationStep.DOWN_SAMPLED)
							{
								// queue the task
								break checkWorldGenLoop;
							}
						}
					}
				}
				
				if (currentMinWorldGenStep != EDhApiWorldGenerationStep.EMPTY
					&& currentMinWorldGenStep != EDhApiWorldGenerationStep.DOWN_SAMPLED)
				{
					// no world gen needed for this position
					return;
				}
				
				generationList.add(genPos);
			}
		});
		
		return generationList;
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public void close()
	{
		super.close();
		
		this.delayedFullDataSourceSaveCache.close();
	}
	
	
	
	
	
	//================//
	// helper classes //
	//================//
	
	// TODO may not be needed
	private class WorldGenTaskTracker implements IWorldGenTaskTracker
	{
		/** just used when debugging/troubleshooting */
		private final long pos;
		
		public WorldGenTaskTracker(long pos) { this.pos = pos; }
		
		
		@Override
		public Consumer<FullDataSourceV2> getDataSourceConsumer()
		{
			return (dataSource) ->
			{
				GeneratedFullDataSourceProvider.this.delayedFullDataSourceSaveCache.writeDataSourceToMemoryAndQueueSave(dataSource);
			};
		}
		
		@Override
		public CompletableFuture<Boolean> shouldGenerateSplitChild(long pos)
		{
			return GeneratedFullDataSourceProvider.this.getAsync(pos).thenApply(fullDataSource ->
			{
				//noinspection TryFinallyCanBeTryWithResources
				try
				{
					return !GeneratedFullDataSourceProvider.this.isFullyGenerated(fullDataSource.columnGenerationSteps);
				}
				finally
				{
					fullDataSource.close();
				}
			});
		}
		
	}
	private CompletableFuture<Void> onDataSourceSaveAsync(FullDataSourceV2 fullDataSource) 
	{
		// block lights should have been populated at the chunkWrapper stage
		// waiting to populate the data source's skylight at this stage prevents re-lighting and
		// allows us to reduce cross-chunk lighting issues by lighting the whole 4x4 LOD at once
		DhLightingEngine.INSTANCE.bakeDataSourceSkyLight(fullDataSource, LodUtil.MAX_MC_LIGHT);
		
		return this.updateDataSourceAsync(fullDataSource);
	}
	
	
	
	/** used by external event listeners */
	public interface IOnWorldGenCompleteListener
	{
		boolean shouldDoWorldGen();
		
		@Nullable
		DhBlockPos2D getTargetPosForGeneration();
		
		/** Fired whenever a section has completed generating */
		void onWorldGenTaskComplete(long pos);
		
	}
	
}
