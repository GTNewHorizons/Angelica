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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorProgressDisplayLocation;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.generation.IFullDataSourceRetrievalQueue;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.RollingAverage;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Handles both single-player/server-side world gen and client side LOD requests.
 * TODO rename
 */
public class WorldGenModule implements Closeable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final GeneratedFullDataSourceProvider.IOnWorldGenCompleteListener onWorldGenCompleteListener;
	
	private final GeneratedFullDataSourceProvider dataSourceProvider;
	private final Supplier<? extends AbstractWorldGenState> worldGenStateSupplier;
	
	private final AtomicReference<AbstractWorldGenState> worldGenStateRef = new AtomicReference<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public WorldGenModule(
			GeneratedFullDataSourceProvider.IOnWorldGenCompleteListener onWorldGenCompleteListener,
			GeneratedFullDataSourceProvider dataSourceProvider,
			Supplier<? extends AbstractWorldGenState> worldGenStateSupplier
		)
	{
		this.onWorldGenCompleteListener = onWorldGenCompleteListener;
		this.dataSourceProvider = dataSourceProvider;
		this.worldGenStateSupplier = worldGenStateSupplier;
	}
	
	
	
	//===================//
	// world gen control //
	//===================//
	
	public void startWorldGen(GeneratedFullDataSourceProvider dataFileHandler, AbstractWorldGenState newWgs)
	{
		// create the new world generator
		if (!this.worldGenStateRef.compareAndSet(null, newWgs))
		{
			LOGGER.warn("Failed to start world gen due to concurrency");
			newWgs.closeAsync(false);
		}
		dataFileHandler.addWorldGenCompleteListener(this.onWorldGenCompleteListener);
		dataFileHandler.setWorldGenerationQueue(newWgs.worldGenerationQueue);
	}
	
	public void stopWorldGen(GeneratedFullDataSourceProvider dataFileHandler)
	{
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState == null)
		{
			LOGGER.warn("Attempted to stop world gen when it was not running");
			return;
		}
		
		// shut down the world generator
		while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
		{
			worldGenState = this.worldGenStateRef.get();
			if (worldGenState == null)
			{
				return;
			}
		}
		dataFileHandler.clearRetrievalQueue();
		worldGenState.closeAsync(true).join(); //TODO: Make it async.
		dataFileHandler.removeWorldGenCompleteListener(this.onWorldGenCompleteListener);
	}
	
	public void worldGenTick()
	{
		boolean shouldDoWorldGen = this.onWorldGenCompleteListener.shouldDoWorldGen();
		// if the world is read only don't generate anything
		shouldDoWorldGen &= !DhApiWorldProxy.INSTANCE.getReadOnly();
		
		boolean isWorldGenRunning = this.isWorldGenRunning();
		if (shouldDoWorldGen && !isWorldGenRunning)
		{
			// start world gen
			this.startWorldGen(this.dataSourceProvider, this.worldGenStateSupplier.get());
		}
		else if (!shouldDoWorldGen && isWorldGenRunning)
		{
			// stop world gen
			this.stopWorldGen(this.dataSourceProvider);
		}
		
		if (this.isWorldGenRunning())
		{
			AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
			if (worldGenState != null)
			{
				DhBlockPos2D targetPosForGeneration = this.onWorldGenCompleteListener.getTargetPosForGeneration();
				if (targetPosForGeneration != null)
				{
					worldGenState.startGenerationQueueAndSetTargetPos(targetPosForGeneration);
				}
			}
		}
	}
	
	
	
	//=======================//
	// base method overrides //
	//=======================//
	
	@Override
	public void close()
	{
		// shutdown the world-gen
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState != null)
		{
			while (!this.worldGenStateRef.compareAndSet(worldGenState, null))
			{
				worldGenState = this.worldGenStateRef.get();
				if (worldGenState == null)
				{
					break;
				}
			}
			
			if (worldGenState != null)
			{
				worldGenState.closeAsync(true).join(); //TODO: Make it async.
			}
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public boolean isWorldGenRunning() { return this.worldGenStateRef.get() != null; }
	
	/** mutates a list so it can be added to an existing {@link IDhLevel}'s debug list  */
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		AbstractWorldGenState worldGenState = this.worldGenStateRef.get();
		if (worldGenState == null)
		{
			return;
		}
		
		
		// estimated tasks
		String waitingCountStr = F3Screen.NUMBER_FORMAT.format(worldGenState.worldGenerationQueue.getWaitingTaskCount());
		String inProgressCountStr = F3Screen.NUMBER_FORMAT.format(worldGenState.worldGenerationQueue.getInProgressTaskCount());
		String totalCountEstimateStr = F3Screen.NUMBER_FORMAT.format(worldGenState.worldGenerationQueue.getRetrievalEstimatedRemainingChunkCount());
		String message = "World Gen/Import Tasks: "+waitingCountStr+"/"+totalCountEstimateStr+" (in progress "+inProgressCountStr+")";
		
		// estimated chunks/sec
		double chunksPerSec = worldGenState.getEstimatedChunksPerSecond();
		if (chunksPerSec > -1)
		{
			message += ", " + F3Screen.NUMBER_FORMAT.format(chunksPerSec) + " chunks/sec";
		}
		
		messageList.add(message);
		
		worldGenState.worldGenerationQueue.addDebugMenuStringsToList(messageList);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** Handles the {@link IFullDataSourceRetrievalQueue} and any other necessary world gen information. */
	public static abstract class AbstractWorldGenState
	{
		/** static so we only send the disable message once per session */
		private static long firstProgressMessageSentMs = 0;
		
		public IFullDataSourceRetrievalQueue worldGenerationQueue;
		
		private static final ThreadPoolExecutor PROGRESS_UPDATER_THREAD = ThreadUtil.makeSingleDaemonThreadPool("World Gen Progress Updater");
		private boolean progressUpdateThreadRunning = false;
		
		
		CompletableFuture<Void> closeAsync(boolean doInterrupt)
		{
			// this should stop the updater thread
			this.progressUpdateThreadRunning = false;
			
			return this.worldGenerationQueue.startClosingAsync(true, doInterrupt)
				.exceptionally(e ->
				{
					LOGGER.error("Error during first stage of generation queue shutdown, Error: ["+e.getMessage()+"].", e);
					return null;
				}
				).thenRun(this.worldGenerationQueue::close)
				.exceptionally(e ->
				{
					LOGGER.error("Error during second stage of generation queue shutdown, Error: ["+e.getMessage()+"].", e);
					return null;
				});
		}
		
		/** @param targetPosForGeneration the position that world generation should be centered around */
		public void startGenerationQueueAndSetTargetPos(DhBlockPos2D targetPosForGeneration) 
		{ 
			this.worldGenerationQueue.startAndSetTargetPos(targetPosForGeneration);
			this.startProgressUpdateThread();
		}
		private void startProgressUpdateThread()
		{
			// only start the thread once
			if (!this.progressUpdateThreadRunning)
			{
				this.progressUpdateThreadRunning = true;
				
				PROGRESS_UPDATER_THREAD.execute(() -> 
				{
					while (this.progressUpdateThreadRunning)
					{
						try
						{
							this.sendRetrievalProgress();
							
							// sleep so we only see an update once in a while
							int sleepTimeInSec = Config.Common.WorldGenerator.generationProgressDisplayIntervalInSeconds.get();
							Thread.sleep(sleepTimeInSec * 1_000L);
						}
						catch (Exception e)
						{
							LOGGER.error("Unexpected issue displaying chunk retrieval progress [" + e.getMessage() + "].", e);
						}
					}
				});
			}
		}
		private void sendRetrievalProgress()
		{
			// format the remaining chunks
			int remainingChunkCount = this.worldGenerationQueue.getRetrievalEstimatedRemainingChunkCount();
			remainingChunkCount += this.worldGenerationQueue.getQueuedChunkCount();
			String remainingChunkCountStr = F3Screen.NUMBER_FORMAT.format(remainingChunkCount);
			
			String message = "DH Gen/Import: " + remainingChunkCountStr + " chunks";
			
			
			// add the remaining time estimate if available
			double chunksPerSec = this.getEstimatedChunksPerSecond();
			if (chunksPerSec > 0)
			{
				long estimatedRemainingTime = (long) (remainingChunkCount / chunksPerSec);
				message += " Estimated Time: " + formatSeconds(estimatedRemainingTime);//+ " at " + F3Screen.NUMBER_FORMAT.format(chunksPerSec) + " chunks/sec";
			}
			
			
			
			// show a message about how to disable progress logging if requested
			int msToShowDisableInstructions = Config.Common.WorldGenerator.generationProgressDisableMessageDisplayTimeInSeconds.get() * 1_000;
			if (msToShowDisableInstructions > 0)
			{
				long timeSinceFirstMessageInMs = (System.currentTimeMillis() - firstProgressMessageSentMs);
				// always show this message for the first tick
				if (firstProgressMessageSentMs == 0
					// show this message if there is still time
					|| timeSinceFirstMessageInMs < msToShowDisableInstructions)
				{
					// replace the current message
					message = "DH Gen/Import progress. This message can be hidden in the DH config. ["+remainingChunkCountStr+"]";
				}
			}
			
			
			
			// only log if there are chunks needing to be generated
			if (remainingChunkCount != 0)
			{
				// determine where to log
				EDhApiDistantGeneratorProgressDisplayLocation displayLocation = Config.Common.WorldGenerator.showGenerationProgress.get();
				if (displayLocation == EDhApiDistantGeneratorProgressDisplayLocation.OVERLAY)
				{
					ClientApi.INSTANCE.showOverlayMessageNextFrame(message);
				}
				else if (displayLocation == EDhApiDistantGeneratorProgressDisplayLocation.CHAT)
				{
					ClientApi.INSTANCE.showChatMessageNextFrame(message);
				}
				else if (displayLocation == EDhApiDistantGeneratorProgressDisplayLocation.LOG)
				{
					LOGGER.info(message);
				}
				
				
				// mark when the first message was sent
				if (firstProgressMessageSentMs == 0)
				{
					firstProgressMessageSentMs = System.currentTimeMillis();
				}
			}
		}
		private static String formatSeconds(long totalSeconds) 
		{
			long days = totalSeconds / (24 * 3600);  // 24 hours in a day
			long hours = (totalSeconds % (24 * 3600)) / 3600;  // Hours
			long minutes = (totalSeconds % 3600) / 60;  // Minutes
			long seconds = totalSeconds % 60;  // Seconds
			
			
			String timeString = "";
			if (days > 0)
			{
				timeString += days+" ";
			}
			if (hours > 0)
			{
				timeString += hours+":";
			}
			if (minutes > 0)
			{
				timeString += String.format("%02d", minutes)+":";
			}
			timeString += String.format("%02d", seconds);
			
			return timeString;
		}
		
		/** @return -1 if this method isn't supported or available */
		public double getEstimatedChunksPerSecond()
		{
			RollingAverage avg = this.worldGenerationQueue.getRollingAverageChunkGenTimeInMs();
			if (avg == null)
			{
				return -1;
			}
			
			
			PriorityTaskPicker.Executor executor = ThreadPoolUtil.getWorldGenExecutor();
			int threadCount = 1;
			if (executor != null)
			{
				threadCount = executor.getPoolSize();
			}
			
			// convert chunk generation time in milliseconds to chunks per second
			double chunksPerSecond = (1 / avg.getAverage()) * 1_000;
			// estimate the number of chunks that can be processed per second by all threads
			// Note: this is probably higher than the actual number, we might want to drop this by 1 or 2 to give a more realistic estimate
			chunksPerSecond = threadCount * chunksPerSecond;
			
			return chunksPerSecond;
		}
		
	}
	
	
	
}
