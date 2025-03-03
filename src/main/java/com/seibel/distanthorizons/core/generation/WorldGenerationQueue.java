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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.override.worldGenerator.IDhApiWorldGenerator;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGeneratorReturnType;
import com.seibel.distanthorizons.api.objects.data.DhApiChunk;
import com.seibel.distanthorizons.api.objects.data.IDhApiFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.AbstractDataSourceHandler;
import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.InProgressWorldGenTaskGroup;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenTask;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenTaskGroup;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.transformers.LodDataBuilder;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil.AssertFailureException;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class WorldGenerationQueue implements IFullDataSourceRetrievalQueue, IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
	
	/**
	 * Defines how many tasks can be queued per thread. <br><br>
	 *
	 * TODO the multiplier here should change dynamically based on how fast the generator is vs the queuing thread,
	 *  if this is too high it may cause issues when moving,
	 *  but if it is too low the generator threads won't have enough tasks to work on
	 */
	private static final int MAX_QUEUED_TASKS_PER_THREAD = 3;
	
	
	private final IDhApiWorldGenerator generator;
	
	/** contains the positions that need to be generated */
	private final ConcurrentHashMap<Long, WorldGenTask> waitingTasks = new ConcurrentHashMap<>();
	
	private final ConcurrentHashMap<Long, InProgressWorldGenTaskGroup> inProgressGenTasksByLodPos = new ConcurrentHashMap<>();
	
	/** largest numerical detail level allowed */
	public final byte lowestDataDetail;
	/** smallest numerical detail level allowed */
	public final byte highestDataDetail;
	
	
	/** If not null this generator is in the process of shutting down */
	private volatile CompletableFuture<Void> generatorClosingFuture = null;
	
	// TODO this logic isn't great and can cause a limit to how many threads could be used for world generation, 
	//  however it won't cause duplicate requests or concurrency issues, so it will be good enough for now.
	//  A good long term fix may be to either:
	//  1. allow the generator to deal with larger sections (let the generator threads split up larger tasks into smaller ones
	//  2. batch requests better. instead of sending 4 individual tasks of detail level N, send 1 task of detail level n+1
	private final ExecutorService queueingThread = ThreadUtil.makeSingleThreadPool("World Gen Queue");
	private boolean generationQueueRunning = false;
	private DhBlockPos2D generationTargetPos = DhBlockPos2D.ZERO;
		
	/** just used for rendering to the F3 menu */
	private int estimatedRemainingTaskCount = 0;
	private int estimatedRemainingChunkCount = 0;
	
	private final RollingAverage rollingAverageChunkGenTimeInMs = new RollingAverage(Runtime.getRuntime().availableProcessors() * 500);
	public RollingAverage getRollingAverageChunkGenTimeInMs() { return this.rollingAverageChunkGenTimeInMs; }
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public WorldGenerationQueue(IDhApiWorldGenerator generator)
	{
		LOGGER.info("Creating world gen queue");
		this.generator = generator;
		this.lowestDataDetail = generator.getLargestDataDetailLevel();
		this.highestDataDetail = generator.getSmallestDataDetailLevel();
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showWorldGenQueue);
		LOGGER.info("Created world gen queue");
	}
	
	
	
	//=================//
	// world generator //
	// task handling   //
	//=================//
	
	@Override
	public CompletableFuture<WorldGenResult> submitRetrievalTask(long pos, byte requiredDataDetail, IWorldGenTaskTracker tracker)
	{
		// the generator is shutting down, don't add new tasks
		if (this.generatorClosingFuture != null)
		{
			return CompletableFuture.completedFuture(WorldGenResult.CreateFail());
		}
		
		
		// make sure the generator can provide the requested position
		if (requiredDataDetail < this.highestDataDetail)
		{
			throw new UnsupportedOperationException("Current generator does not meet requiredDataDetail level");
		}
		if (requiredDataDetail > this.lowestDataDetail)
		{
			requiredDataDetail = this.lowestDataDetail;
		}
		
		// Assert that the data at least can fill in 1 single ChunkSizedFullDataAccessor
		LodUtil.assertTrue(DhSectionPos.getDetailLevel(pos) > requiredDataDetail + LodUtil.CHUNK_DETAIL_LEVEL);
		
		
		CompletableFuture<WorldGenResult> future = new CompletableFuture<>();
		this.waitingTasks.put(pos, new WorldGenTask(pos, requiredDataDetail, tracker, future));
		return future;
	}
	
	@Override
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf)
	{
		this.waitingTasks.forEachKey(100, (genPos) -> 
		{
			if (removeIf.accept(genPos))
			{
				this.waitingTasks.remove(genPos);
			}
		});
	}
	
	
	
	
	//===============//
	// running tasks //
	//===============//
	
	@Override
	public void startAndSetTargetPos(DhBlockPos2D targetPos)
	{
		// update the target pos
		this.generationTargetPos = targetPos;
		
		// ensure the queuing thread is running
		if (!this.generationQueueRunning)
		{
			this.startWorldGenQueuingThread();
		}
	}
	private void startWorldGenQueuingThread()
	{
		this.generationQueueRunning = true;
		
		// queue world generation tasks on its own thread since this process is very slow and would lag the server thread
		this.queueingThread.execute(() ->
		{
			try
			{
				// loop until the generator is shutdown
				while (!Thread.interrupted() && DhApiWorldProxy.INSTANCE.worldLoaded() && !DhApiWorldProxy.INSTANCE.getReadOnly())
				{
					this.generator.preGeneratorTaskStart();
					
					// queue generation tasks until the generator is full, or there are no more tasks to generate
					boolean taskStarted = true;
					while (!this.isGeneratorBusy() && taskStarted)
					{
						taskStarted = this.startNextWorldGenTask(this.generationTargetPos);
						if (!taskStarted)
						{
							int debugPointOne = 0;
						}
					}
					
					// if there aren't any new tasks, wait a second before checking again // TODO replace with a listener instead
					Thread.sleep(1000);
				}
			}
			catch (InterruptedException e)
			{
				/* do nothing, this means the thread is being shut down */
			}
			catch (Exception e)
			{
				LOGGER.error("queueing exception: " + e.getMessage(), e);
			}
			finally
			{
				this.generationQueueRunning = false;
			}
		});
	}
	public boolean isGeneratorBusy()
	{
		PriorityTaskPicker.Executor executor = ThreadPoolUtil.getWorldGenExecutor();
		if (executor == null)
		{
			// shouldn't happen, but just in case, don't queue more tasks
			return true;
		}
		
		int worldGenThreadCount = Math.max(Config.Common.MultiThreading.numberOfThreads.get(), 1);
		int maxWorldGenTaskCount = worldGenThreadCount * MAX_QUEUED_TASKS_PER_THREAD;
		return executor.getQueueSize() > maxWorldGenTaskCount;
	}
	
	/**
	 * @param targetPos the position to center the generation around
	 * @return false if no tasks were found to generate
	 */
	private boolean startNextWorldGenTask(DhBlockPos2D targetPos)
	{
		if (this.waitingTasks.isEmpty())
		{
			return false;
		}
		
		
		
		Mapper closestTaskMap = this.waitingTasks.reduceEntries(1024,
				entry -> new Mapper(entry.getValue(), DhSectionPos.getSectionBBoxPos(entry.getValue().pos).getCenterBlockPos().toPos2D().chebyshevDist(targetPos.toPos2D())),
				(aMapper, bMapper) -> aMapper.dist < bMapper.dist ? aMapper : bMapper);
		
		if (closestTaskMap == null)
		{
			// FIXME concurrency issue
			return false;
		}
		
		WorldGenTask closestTask = closestTaskMap.task;
		
		// remove the task we found, we are going to start it and don't want to run it multiple times
		this.waitingTasks.remove(closestTask.pos, closestTask);
		
		// do we need to modify this task to generate it?
		if (this.canGeneratePos(closestTask.pos))
		{
			// detail level is correct for generation, start generation
			
			WorldGenTaskGroup closestTaskGroup = new WorldGenTaskGroup(closestTask.pos, (byte)(closestTask.pos - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL));
			closestTaskGroup.worldGenTasks.add(closestTask);
			
			if (!this.inProgressGenTasksByLodPos.containsKey(closestTask.pos))
			{
				// no task exists for this position, start one
				InProgressWorldGenTaskGroup newTaskGroup = new InProgressWorldGenTaskGroup(closestTaskGroup);
				boolean taskStarted = this.tryStartingWorldGenTaskGroup(newTaskGroup);
				if (!taskStarted)
				{
					//LOGGER.trace("Unable to start task: "+closestTask.pos+", skipping. Task position may have already been generated.");
				}
			}
			else
			{
				// TODO replace the previous inProgress task if one exists
				// Note: Due to concurrency reasons, even if the currently running task is compatible with 
				// 		   the newly selected task, we cannot use it,
				//         as some chunks may have already been written into.
				
				//LOGGER.trace("A task already exists for this position, todo: "+closestTask.pos);
			}
			
			// a task has been started
			return true;
		}
		else
		{
			// detail level is too high (if the detail level was too low, the generator would've ignored the request),
			// split up the task
			
			
			// split up the task and add each one to the tree
			LinkedList<CompletableFuture<WorldGenResult>> childFutures = new LinkedList<>();
			long sectionPos = closestTask.pos;
			WorldGenTask finalClosestTask = closestTask;
			DhSectionPos.forEachChild(sectionPos, (childDhSectionPos) ->
			{
				CompletableFuture<WorldGenResult> newFuture = new CompletableFuture<>();
				childFutures.add(newFuture);
				
				WorldGenTask newGenTask = new WorldGenTask(childDhSectionPos, DhSectionPos.getDetailLevel(childDhSectionPos), finalClosestTask.taskTracker, newFuture);
				this.waitingTasks.put(newGenTask.pos, newGenTask);
			});
			
			// send the child futures to the future recipient, to notify them of the new tasks
			closestTask.future.complete(WorldGenResult.CreateSplit(childFutures));
			
			// return true so we attempt to generate again
			return true;
		}
	}
	/** @return true if the task was started, false otherwise */
	private boolean tryStartingWorldGenTaskGroup(InProgressWorldGenTaskGroup newTaskGroup)
	{
		byte taskDetailLevel = newTaskGroup.group.dataDetail;
		long taskPos = newTaskGroup.group.pos;
		LodUtil.assertTrue(taskDetailLevel >= this.highestDataDetail && taskDetailLevel <= this.lowestDataDetail);
		
		int generationRequestChunkWidthCount = BitShiftUtil.powerOfTwo(DhSectionPos.getDetailLevel(taskPos) - taskDetailLevel - 4); // minus 4 is equal to dividing by 16 to convert to chunk scale
		
		long generationStartMsTime = System.currentTimeMillis();
		CompletableFuture<Void> generationFuture = this.startGenerationEvent(taskPos, taskDetailLevel, generationRequestChunkWidthCount, newTaskGroup.group::consumeDataSource);
		generationFuture.thenRun(() -> 
		{
			long totalGenTimeInMs = System.currentTimeMillis() - generationStartMsTime;
			int chunkCount = generationRequestChunkWidthCount * generationRequestChunkWidthCount;
			double timePerChunk = (double)totalGenTimeInMs / (double)chunkCount;
			this.rollingAverageChunkGenTimeInMs.addValue(timePerChunk);
		});
		
		newTaskGroup.genFuture = generationFuture;
		LodUtil.assertTrue(newTaskGroup.genFuture != null);
		
		newTaskGroup.genFuture.whenComplete((voidObj, exception) ->
		{
			try
			{
				if (exception != null)
				{
					// don't log the shutdown exceptions
					if (!LodUtil.isInterruptOrReject(exception))
					{
						LOGGER.error("Error generating data for pos: " + DhSectionPos.toString(taskPos), exception);
					}
					
					newTaskGroup.group.worldGenTasks.forEach(worldGenTask -> worldGenTask.future.complete(WorldGenResult.CreateFail()));
				}
				else
				{
					newTaskGroup.group.worldGenTasks.forEach(worldGenTask -> worldGenTask.future.complete(WorldGenResult.CreateSuccess(taskPos)));
				}
				boolean worked = this.inProgressGenTasksByLodPos.remove(taskPos, newTaskGroup);
				LodUtil.assertTrue(worked, "Unable to find in progress generator task with position ["+DhSectionPos.toString(taskPos)+"]");
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error completing world gen task at pos: ["+DhSectionPos.toString(taskPos)+"].", e);
			}
		});
		
		this.inProgressGenTasksByLodPos.put(taskPos, newTaskGroup);
		return true;
	}
	private CompletableFuture<Void> startGenerationEvent(
		long requestPos, 
		byte targetDataDetail,
		int generationRequestChunkWidthCount,
		Consumer<FullDataSourceV2> dataSourceConsumer
		)
	{
		DhChunkPos chunkPosMin = new DhChunkPos(DhSectionPos.getSectionBBoxPos(requestPos).getCornerBlockPos());
		
		EDhApiDistantGeneratorMode generatorMode = Config.Common.WorldGenerator.distantGeneratorMode.get();
		EDhApiWorldGeneratorReturnType returnType = this.generator.getReturnType();
		switch (returnType) 
		{
			case VANILLA_CHUNKS: 
			{
				return this.generator.generateChunks(
					chunkPosMin.getX(), chunkPosMin.getZ(),
					generationRequestChunkWidthCount,
					targetDataDetail,
					generatorMode,
					ThreadPoolUtil.getWorldGenExecutor(),
					(Object[] generatedObjectArray) -> 
					{
						try
						{
							IChunkWrapper chunk = WRAPPER_FACTORY.createChunkWrapper(generatedObjectArray);
							try (FullDataSourceV2 dataSource = LodDataBuilder.createFromChunk(chunk))
							{
								LodUtil.assertTrue(dataSource != null);
								dataSourceConsumer.accept(dataSource);
							}
						}
						catch (ClassCastException e)
						{
							LOGGER.error("World generator return type incorrect. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Common.WorldGenerator.enableDistantGeneration.set(false);
						}
						catch (Exception e)
						{
							LOGGER.error("Unexpected world generator error. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Common.WorldGenerator.enableDistantGeneration.set(false);
						}
					}
				);
			}
			case API_CHUNKS: 
			{
				return this.generator.generateApiChunks(
					chunkPosMin.getX(), chunkPosMin.getZ(),
					generationRequestChunkWidthCount,
					targetDataDetail,
					generatorMode,
					ThreadPoolUtil.getWorldGenExecutor(),
					(DhApiChunk dataPoints) ->
					{
						try(FullDataSourceV2 dataSource = LodDataBuilder.createFromApiChunkData(dataPoints, this.generator.runApiValidation()))
						{
							dataSourceConsumer.accept(dataSource);
						}
						catch (DataCorruptedException | IllegalArgumentException e)
						{
							LOGGER.error("World generator returned a corrupt chunk. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Common.WorldGenerator.enableDistantGeneration.set(false);
						}
						catch (ClassCastException e)
						{
							LOGGER.error("World generator return type incorrect. Error: [" + e.getMessage() + "]. World generator disabled.", e);
							Config.Common.WorldGenerator.enableDistantGeneration.set(false);
						}
					}
				);
			}
			case API_DATA_SOURCES:
			{
				// done to reduce GC overhead
				FullDataSourceV2 pooledDataSource = FullDataSourceV2.createEmpty(requestPos);
				// set here so the API user doesn't have to pass in this value anywhere themselves
				pooledDataSource.setRunApiChunkValidation(this.generator.runApiValidation());
				
				// only apply to children if we aren't at the bottom of the tree
				pooledDataSource.applyToChildren = DhSectionPos.getDetailLevel(pooledDataSource.getPos()) > DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
				pooledDataSource.applyToParent = DhSectionPos.getDetailLevel(pooledDataSource.getPos()) < DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 12;
				
				
				return this.generator.generateLod(
						chunkPosMin.getX(), chunkPosMin.getZ(),
						DhSectionPos.getX(requestPos), DhSectionPos.getZ(requestPos),
						(byte) (DhSectionPos.getDetailLevel(requestPos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL),
						pooledDataSource,
						generatorMode,
						ThreadPoolUtil.getWorldGenExecutor(),
						(IDhApiFullDataSource apiDataSource) ->
						{
							try
							{
								FullDataSourceV2 fullDataSource = (FullDataSourceV2) apiDataSource;
								try
								{
									dataSourceConsumer.accept(fullDataSource);
								}
								finally
								{
									fullDataSource.close();
								}
							}
							catch (IllegalArgumentException e)
							{
								LOGGER.error("World generator returned a corrupt data source. Error: [" + e.getMessage() + "]. World generator disabled.", e);
								Config.Common.WorldGenerator.enableDistantGeneration.set(false);
							}
							catch (ClassCastException e)
							{
								LOGGER.error("World generator return type incorrect. Error: [" + e.getMessage() + "]. World generator disabled.", e);
								Config.Common.WorldGenerator.enableDistantGeneration.set(false);
							}
						}
				);
			}
			default: 
			{
				Config.Common.WorldGenerator.enableDistantGeneration.set(false);
				throw new AssertFailureException("Unknown return type: " + returnType);
			}
		}
	}
	
	
	
	//===================//
	// getters / setters //
	//===================//
	
	@Override public int getWaitingTaskCount() { return this.waitingTasks.size(); }
	@Override public int getInProgressTaskCount() { return this.inProgressGenTasksByLodPos.size(); }
	
	@Override
	public byte lowestDataDetail() { return this.lowestDataDetail; }
	@Override
	public byte highestDataDetail() { return this.highestDataDetail; }
	
	@Override
	public int getEstimatedRemainingTaskCount() { return this.estimatedRemainingTaskCount; }
	@Override
	public void setEstimatedRemainingTaskCount(int newEstimate) { this.estimatedRemainingTaskCount = newEstimate; }
	
	@Override
	public int getRetrievalEstimatedRemainingChunkCount() { return this.estimatedRemainingChunkCount; }
	@Override
	public void setRetrievalEstimatedRemainingChunkCount(int newEstimate) { this.estimatedRemainingChunkCount = newEstimate; }
	
	@Override 
	public void addDebugMenuStringsToList(List<String> messageList) { }
	
	@Override
	public int getQueuedChunkCount()
	{
		int chunkCount = 0;
		for (long pos : this.waitingTasks.keySet())
		{
			int chunkWidth = DhSectionPos.getBlockWidth(pos) / LodUtil.CHUNK_WIDTH;
			chunkCount += (chunkWidth * chunkWidth);
		}
		
		return chunkCount;
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override public CompletableFuture<Void> startClosingAsync(boolean cancelCurrentGeneration, boolean alsoInterruptRunning)
	{
		LOGGER.info("Closing world gen queue");
		this.queueingThread.shutdownNow();
		
		
		// stop and remove any in progress tasks
		ArrayList<CompletableFuture<Void>> inProgressTasksCancelingFutures = new ArrayList<>(this.inProgressGenTasksByLodPos.size());
		this.inProgressGenTasksByLodPos.values().forEach(runningTaskGroup ->
		{
			CompletableFuture<Void> genFuture = runningTaskGroup.genFuture; // Do this to prevent it getting swapped out
			if (genFuture == null)
			{
				// genFuture's shouldn't be null, but sometimes they are...
				LOGGER.info("Null gen future: "+runningTaskGroup.group.pos);
				return;
			}
			
			
			if (cancelCurrentGeneration)
			{
				genFuture.cancel(alsoInterruptRunning);
			}
			
			inProgressTasksCancelingFutures.add(genFuture.handle((voidObj, exception) ->
			{
				if (exception instanceof CompletionException)
				{
					exception = exception.getCause();
				}
				
				if (!UncheckedInterruptedException.isInterrupt(exception) && !(exception instanceof CancellationException))
				{
					LOGGER.error("Error when terminating data generation for section " + runningTaskGroup.group.pos, exception);
				}
				
				return null;
			}));
		});
		this.generatorClosingFuture = CompletableFuture.allOf(inProgressTasksCancelingFutures.toArray(new CompletableFuture[0]));
		
		return this.generatorClosingFuture;
	}
	
	@Override
	public void close()
	{
		LOGGER.info("Closing " + WorldGenerationQueue.class.getSimpleName() + "...");
		
		if (this.generatorClosingFuture == null)
		{
			this.startClosingAsync(true, true);
		}
		LodUtil.assertTrue(this.generatorClosingFuture != null);
		
		
		LOGGER.info("Awaiting world generator thread pool termination...");
		try
		{
			int waitTimeInSeconds = 3;
			AbstractExecutorService executor = ThreadPoolUtil.getWorldGenExecutor();
			if (executor != null && !executor.awaitTermination(waitTimeInSeconds, TimeUnit.SECONDS))
			{
				LOGGER.warn("World generator thread pool shutdown didn't complete after [" + waitTimeInSeconds + "] seconds. Some world generator requests may still be running.");
			}
		}
		catch (InterruptedException e)
		{
			LOGGER.warn("World generator thread pool shutdown interrupted! Ignoring child threads...", e);
		}
		
		
		this.generator.close();
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showWorldGenQueue);
		
		
		try
		{
			this.generatorClosingFuture.cancel(true);
		}
		catch (Throwable e)
		{
			LOGGER.warn("Failed to close generation queue: ", e);
		}
		
		
		LOGGER.info("Finished closing " + WorldGenerationQueue.class.getSimpleName());
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	@Override
	public void debugRender(DebugRenderer renderer)
	{
		this.waitingTasks.keySet().forEach((pos) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 64f, 0.05f, Color.blue)); });
		this.inProgressGenTasksByLodPos.forEach((pos, t) -> { renderer.renderBox(new DebugRenderer.Box(pos, -32f, 64f, 0.05f, Color.red)); });
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private boolean canGeneratePos(long taskPos)
	{
		byte requestedDetailLevel = (byte) (DhSectionPos.getDetailLevel(taskPos) - DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		return (this.highestDataDetail <= requestedDetailLevel && requestedDetailLevel <= this.lowestDataDetail);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class Mapper
	{
		public final WorldGenTask task;
		public final int dist;
		public Mapper(WorldGenTask task, int dist)
		{
			this.task = task;
			this.dist = dist;
		}
		
	}
	
}
