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

package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldLoadEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiWorldUnloadEvent;
import com.seibel.distanthorizons.core.Initializer;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

/** Contains code and variables used by both {@link ClientApi} and {@link ServerApi} */
public class SharedApi
{
	public static final SharedApi INSTANCE = new SharedApi();
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	/** will be null on the server-side */
	@Nullable
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	/** will be null on the server-side */
	@Nullable
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftSharedWrapper MC_SHARED = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);
	
	private static final UpdateChunkPosManager UPDATE_POS_MANAGER = new UpdateChunkPosManager();
	/** 
	 * how many chunks can be queued for updating per thread + player (in multiplayer), 
	 * used to prevent updates from infinitely pilling up if the user flies around extremely fast 
	 */
	private static final int MAX_UPDATING_CHUNK_COUNT_PER_THREAD_AND_PLAYER = 1_000;
	
	/** how many milliseconds must pass before an overloaded message can be sent in chat or the log */
	private static final int MIN_MS_BETWEEN_OVERLOADED_LOG_MESSAGE = 30_000;
	
	
	private static AbstractDhWorld currentWorld;
	private static int lastWorldGenTickDelta = 0;
	private static long lastOverloadedLogMessageMsTime = 0;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private SharedApi() { }
	public static void init() { Initializer.init(); }
	
	
	
	//===============//
	// world methods //
	//===============//
	
	public static EWorldEnvironment getEnvironment() { return (currentWorld == null) ? null : currentWorld.environment; }
	
	public static void setDhWorld(AbstractDhWorld newWorld)
	{
		currentWorld = newWorld;
		
		// starting and stopping the DataRenderTransformer is necessary to prevent attempting to
		// access the MC level at inappropriate times, which can cause exceptions
		if (currentWorld != null)
		{
			ThreadPoolUtil.setupThreadPools();
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiWorldLoadEvent.class, new DhApiWorldLoadEvent.EventParam());
		}
		else
		{
			ThreadPoolUtil.shutdownThreadPools();
			DebugRenderer.clearRenderables();
			
			if (MC_RENDER != null)
			{
				MC_RENDER.clearTargetFrameBuffer();
			}
			
			// shouldn't be necessary, but if we missed closing one of the connections this should make sure they're all closed
			AbstractDhRepo.closeAllConnections();
			// needs to be closed on world shutdown to clear out un-processed chunks
			UPDATE_POS_MANAGER.clear();
			
			// recommend that the garbage collector cleans up any objects from the old world and thread pools
			System.gc();
			
			ApiEventInjector.INSTANCE.fireAllEvents(DhApiWorldUnloadEvent.class, new DhApiWorldUnloadEvent.EventParam());
			
			// fired after the unload event so API users can't change the read-only for any new worlds
			DhApiWorldProxy.INSTANCE.setReadOnly(false, false);
		}
	}
	
	public static void worldGenTick(Runnable worldGenRunnable)
	{
		lastWorldGenTickDelta--;
		if (lastWorldGenTickDelta <= 0)
		{
			worldGenRunnable.run();
			lastWorldGenTickDelta = 20;
		}
	}
	
	public static AbstractDhWorld getAbstractDhWorld() { return currentWorld; }
	/** returns null if the {@link SharedApi#currentWorld} isn't a {@link DhClientServerWorld} */
	public static DhClientServerWorld getDhClientServerWorld() { return (currentWorld instanceof DhClientServerWorld) ? (DhClientServerWorld) currentWorld : null; }
	/** returns null if the {@link SharedApi#currentWorld} isn't a {@link DhClientWorld} or {@link DhClientServerWorld} */
	public static IDhClientWorld getIDhClientWorld() { return (currentWorld instanceof IDhClientWorld) ? (IDhClientWorld) currentWorld : null; }
	/** returns null if the {@link SharedApi#currentWorld} isn't a {@link DhServerWorld} or {@link DhClientServerWorld} */
	public static IDhServerWorld getIDhServerWorld() { return (currentWorld instanceof IDhServerWorld) ? (IDhServerWorld) currentWorld : null; }
	
	
	
	//==============//
	// chunk update //
	//==============//
	
	/** 
	 * Used to prevent getting a full chunk from MC if it isn't necessary. <br>
	 * This is important since asking MC for a chunk is slow and may block the render thread.
	 */
	public static boolean isChunkAtBlockPosAlreadyUpdating(int blockPosX, int blockPosZ)
	{ return UPDATE_POS_MANAGER.contains(new DhChunkPos(new DhBlockPos2D(blockPosX, blockPosZ))); }
	
	public static boolean isChunkAtChunkPosAlreadyUpdating(int chunkPosX, int chunkPosZ)
	{ return UPDATE_POS_MANAGER.contains(new DhChunkPos(chunkPosX, chunkPosZ)); }
	
	/** 
	 * This is often fired when unloading a level.
	 * This is done to prevent overloading the system when
	 * rapidly changing dimensions.
	 * (IE prevent DH from infinitely allocating memory 
	 */
	public void clearQueuedChunkUpdates() { UPDATE_POS_MANAGER.clear(); }
	
	public int getQueuedChunkUpdateCount() { return UPDATE_POS_MANAGER.closestQueue.size(); }
	
	
	
	/** handles both block place and break events */
	public void chunkBlockChangedEvent(IChunkWrapper chunk, ILevelWrapper level) { this.applyChunkUpdate(chunk, level, true); }
	public void chunkLoadEvent(IChunkWrapper chunk, ILevelWrapper level) { this.applyChunkUpdate(chunk, level, false); }
	
	public void applyChunkUpdate(IChunkWrapper chunkWrapper, ILevelWrapper level, boolean updateNeighborChunks)
	{
		//========================//
		// world and level checks //
		//========================//
		
		if (chunkWrapper == null)
		{
			// shouldn't happen, but just in case
			return;
		}
		
		AbstractDhWorld dhWorld = SharedApi.getAbstractDhWorld();
		if (dhWorld == null)
		{
			if (level instanceof IClientLevelWrapper)
			{
				// If the client world isn't loaded yet, keep track of which chunks were loaded so we can use them later.
				// This may happen if the client world and client level load events happen out of order
				IClientLevelWrapper clientLevel = (IClientLevelWrapper) level;
				ClientApi.INSTANCE.waitingChunkByClientLevelAndPos.replace(new Pair<>(clientLevel, chunkWrapper.getChunkPos()), chunkWrapper);
			}
			
			return;
		}
		
		// ignore updates if the world is read-only
		if (DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return;
		}
		
		
		// only continue if the level is loaded
		IDhLevel dhLevel = dhWorld.getLevel(level);
		if (dhLevel == null)
		{
			if (level instanceof IClientLevelWrapper)
			{
				// the client level isn't loaded yet
				IClientLevelWrapper clientLevel = (IClientLevelWrapper) level;
				ClientApi.INSTANCE.waitingChunkByClientLevelAndPos.replace(new Pair<>(clientLevel, chunkWrapper.getChunkPos()), chunkWrapper);
			}
			
			return;
		}
		
		if (dhLevel instanceof DhClientLevel)
		{
			if (!((DhClientLevel) dhLevel).shouldProcessChunkUpdate(chunkWrapper.getChunkPos()))
			{
				return;
			}
		}
		
		// shoudln't normally happen, but just in case
		if (UPDATE_POS_MANAGER.contains(chunkWrapper.getChunkPos()))
		{
			// TODO this will prevent some LODs from updating across dimensions if multiple levels are loaded
			return;
		}
		
		
		
		//===============================//
		// update the necessary chunk(s) //
		//===============================//
		
		if (!updateNeighborChunks)
		{
			// only update the center chunk
			
			queueChunkUpdate(chunkWrapper, null, dhLevel);
		}
		else
		{
			// update the center with any existing neighbour chunks. 
			// this is done so lighting changes are propagated correctly
			queueChunkUpdate(chunkWrapper, getNeighbourChunkListForChunk(chunkWrapper,dhLevel), dhLevel);
		}
	}
	private static ArrayList<IChunkWrapper> getNeighbourChunkListForChunk(IChunkWrapper chunkWrapper, IDhLevel dhLevel)
	{
		// get the neighboring chunk list
		ArrayList<IChunkWrapper> neighbourChunkList = new ArrayList<>(9);
		for (int xOffset = -1; xOffset <= 1; xOffset++)
		{
			for (int zOffset = -1; zOffset <= 1; zOffset++)
			{
				if (xOffset == 0 && zOffset == 0)
				{
					// center chunk
					neighbourChunkList.add(chunkWrapper);
				}
				else
				{
					// neighboring chunk
					DhChunkPos neighbourPos = new DhChunkPos(chunkWrapper.getChunkPos().getX() + xOffset, chunkWrapper.getChunkPos().getZ() + zOffset);
					IChunkWrapper neighbourChunk = dhLevel.getLevelWrapper().tryGetChunk(neighbourPos);
					if (neighbourChunk != null)
					{
						neighbourChunkList.add(neighbourChunk);
					}
				}
			}
		}
		return neighbourChunkList;
	}
	
	private static void queueChunkUpdate(IChunkWrapper chunkWrapper, @Nullable ArrayList<IChunkWrapper> neighbourChunkList, IDhLevel dhLevel) 
	{ queueChunkUpdate(chunkWrapper, neighbourChunkList, dhLevel,false); }
	private static void queueChunkUpdate(IChunkWrapper chunkWrapper, @Nullable ArrayList<IChunkWrapper> neighbourChunkList, IDhLevel dhLevel, boolean lightUpdateOnly)
	{
		int maxUpdateSizeMultiplier;
		if (MC_CLIENT != null && MC_CLIENT.playerExists())
		{
			// Local worlds & multiplayer
			UPDATE_POS_MANAGER.setCenter(MC_CLIENT.getPlayerChunkPos());
			maxUpdateSizeMultiplier = MC_CLIENT.clientConnectedToDedicatedServer() ? 1 : MC_SHARED.getPlayerCount();
		}
		else
		{
			// Dedicated servers
			// Also includes spawn chunks since they're likely to be intentionally utilized with updates
			maxUpdateSizeMultiplier = 1 + MC_SHARED.getPlayerCount();
		}
		
		UPDATE_POS_MANAGER.maxSize = MAX_UPDATING_CHUNK_COUNT_PER_THREAD_AND_PLAYER
				* Config.Common.MultiThreading.numberOfThreads.get()
				* maxUpdateSizeMultiplier;
		
		UpdateChunkData updateData = new UpdateChunkData(chunkWrapper, neighbourChunkList, dhLevel, lightUpdateOnly);
		if(lightUpdateOnly)
		{
			UPDATE_POS_MANAGER.removeItem(chunkWrapper.getChunkPos());
		}
		int remainingCapacity = UPDATE_POS_MANAGER.addItem(chunkWrapper.getChunkPos(), updateData);
		if (remainingCapacity <= 0)
		{
			// limit how often an overloaded message can be sent
			long msBetweenLastLog = System.currentTimeMillis() - lastOverloadedLogMessageMsTime;
			if (msBetweenLastLog >= MIN_MS_BETWEEN_OVERLOADED_LOG_MESSAGE)
			{
				lastOverloadedLogMessageMsTime = System.currentTimeMillis();
				
				String message = "\u00A76" + "Distant Horizons overloaded, too many chunks queued for LOD processing. " + "\u00A7r" +
						"\nThis may result in holes in your LODs. " +
						"\nFix: move through the world slower, decrease your vanilla render distance, slow down your world pre-generator (IE Chunky), or increase the Distant Horizons' CPU thread counts. " +
						"\nMax queue count ["+UPDATE_POS_MANAGER.maxSize+"] (["+ MAX_UPDATING_CHUNK_COUNT_PER_THREAD_AND_PLAYER +"] per thread+players).";
				
				boolean showWarningInChat = Config.Common.Logging.Warning.showUpdateQueueOverloadedChatWarning.get();
				if (showWarningInChat)
				{
					ClientApi.INSTANCE.showChatMessageNextFrame(message);
				}
				
				// Don't log warnings in singleplayer or in hosted LAN since it usually isn't a problem (and if it is it's easy to notice).
				// Servers should always log since being overloaded is harder to notice. 
				EWorldEnvironment environment = SharedApi.getEnvironment();
				if (showWarningInChat || environment == EWorldEnvironment.SERVER_ONLY)
				{
					LOGGER.warn(message);
				}
			}
		}
		
		
		
		// queue updates up to the number of CPU cores allocated for the job
		// (this prevents doing extra work queuing tasks that may not be necessary)
		// and makes sure the chunks closest to the player are updated first
		PriorityTaskPicker.Executor executor = ThreadPoolUtil.getChunkToLodBuilderExecutor();
		if (executor != null && executor.getQueueSize() < executor.getPoolSize())
		{
			try
			{
				executor.execute(SharedApi::processQueuedChunkUpdate);
			}
			catch (RejectedExecutionException ignore)
			{
				// the executor was shut down, it should be back up shortly and able to accept new jobs
			}
		}
	}
	private static void processQueuedChunkUpdate()
	{
		//LOGGER.trace(chunkWrapper.getChunkPos() + " " + executor.getActiveCount() + " / " + executor.getQueue().size() + " - " + executor.getCompletedTaskCount());
		
		UpdateChunkData updateData = UPDATE_POS_MANAGER.popClosest();
		if (updateData == null)
		{
			return;
		}
		
		IChunkWrapper chunkWrapper = updateData.chunkWrapper;
		@Nullable ArrayList<IChunkWrapper> neighbourChunkList = updateData.neighbourChunkList;
		IDhLevel dhLevel = updateData.dhLevel;
		
		
		try
		{
			boolean checkChunkHash = !Config.Common.LodBuilding.disableUnchangedChunkCheck.get();
			
			// check if this chunk has been converted into an LOD already
			int oldChunkHash = dhLevel.getChunkHash(chunkWrapper.getChunkPos()); // shouldn't happen on the render thread since it may take a few moments to run
			int newChunkHash = chunkWrapper.getBlockBiomeHashCode();
			if (checkChunkHash)
			{
				if (oldChunkHash == newChunkHash && !updateData.lightUpdateOnly)
				{
					// if the chunk hashes are the same then we don't need to bother with lighting the chunk
					// or creating/updating the LODs
					return;
				}
			}
			
			
			// having a list of the nearby chunks is needed for lighting and beacon generation
			ArrayList<IChunkWrapper> nearbyChunkList;
			if (neighbourChunkList != null)
			{
				nearbyChunkList = neighbourChunkList;
			}
			else
			{
				nearbyChunkList = new ArrayList<>(1);
				nearbyChunkList.add(chunkWrapper);
			}
			
			// if this chunk will update its lighting
			// then queue adjacent chunks to update theirs as well
			// adjacent chunk will have 'lightUpdateOnly' true 
			// so they won't schedule further chunk updates
			if (!updateData.lightUpdateOnly)
			{
				for (IChunkWrapper adjacentChunk : nearbyChunkList)
				{
					// pulling a new chunkWrapper is necessary to prevent concurrent modification on the existing chunkWrappers
					IChunkWrapper newCenterChunk = dhLevel.getLevelWrapper().tryGetChunk(adjacentChunk.getChunkPos());
					if (newCenterChunk != null)
					{
						queueChunkUpdate(newCenterChunk, getNeighbourChunkListForChunk(newCenterChunk, dhLevel), dhLevel, true);
					}
				}
			}
			
			// sky lighting is populated later at the data source level
			DhLightingEngine.INSTANCE.bakeChunkBlockLighting(chunkWrapper, nearbyChunkList, dhLevel.hasSkyLight() ? LodUtil.MAX_MC_LIGHT : LodUtil.MIN_MC_LIGHT);
			
			dhLevel.updateBeaconBeamsForChunk(chunkWrapper, nearbyChunkList);
			dhLevel.updateChunkAsync(chunkWrapper, newChunkHash);
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected error when updating chunk at pos: [" + chunkWrapper.getChunkPos() + "]", e);
		}
		finally
		{
			// queue the next position if there are still positions to process
			AbstractExecutorService executor = ThreadPoolUtil.getChunkToLodBuilderExecutor();
			if (executor != null && !UPDATE_POS_MANAGER.updateDataByChunkPos.isEmpty())
			{
				try
				{
					executor.execute(SharedApi::processQueuedChunkUpdate);
				}
				catch (RejectedExecutionException ignore)
				{
					// the executor was shut down, it should be back up shortly and able to accept new jobs
				}
			}
		}
	}
	
	
	
	//=========//
	// F3 Menu //
	//=========//
	
	public String getDebugMenuString()
	{
		String updatingCountStr = F3Screen.NUMBER_FORMAT.format(UPDATE_POS_MANAGER.closestQueue.size());
		String maxUpdateCountStr = F3Screen.NUMBER_FORMAT.format(UPDATE_POS_MANAGER.maxSize);
		return "Queued chunk updates: "+updatingCountStr+" / "+maxUpdateCountStr;
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** contains the objects needed to update a chunk */
	private static class UpdateChunkData
	{
		public IChunkWrapper chunkWrapper;
		@Nullable
		public ArrayList<IChunkWrapper> neighbourChunkList;
		public IDhLevel dhLevel;
		/** adjacent chunks will only update their light */
		public boolean lightUpdateOnly;
		
		public UpdateChunkData(IChunkWrapper chunkWrapper, @Nullable ArrayList<IChunkWrapper> neighbourChunkList, IDhLevel dhLevel, boolean lightUpdateOnly)
		{
			this.chunkWrapper = chunkWrapper;
			this.neighbourChunkList = neighbourChunkList;
			this.dhLevel = dhLevel;
			this.lightUpdateOnly = lightUpdateOnly;
		}
	}
	
	/** keeps track of which chunks need to be updated */
	private static class UpdateChunkPosManager
	{
		private final PriorityBlockingQueue<DhChunkPos> closestQueue;
		private final PriorityBlockingQueue<DhChunkPos> furthestQueue;
		private final ConcurrentHashMap<DhChunkPos, UpdateChunkData> updateDataByChunkPos;
		
		private DhChunkPos center;
		private int maxSize = 500;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public UpdateChunkPosManager()
		{
			this.closestQueue = new PriorityBlockingQueue<>(500, Comparator.comparingDouble(pos -> pos.squaredDistance(this.center)));
			this.furthestQueue = new PriorityBlockingQueue<>(500, Comparator.comparingDouble(pos -> ((DhChunkPos)pos).squaredDistance(this.center)).reversed());
			this.updateDataByChunkPos = new ConcurrentHashMap<>();
			// defaulting to 0,0 is fine since it'll be updated once we start adding items 
			this.center = new DhChunkPos(0, 0);
		}
		
		
		
		//==================//
		// list/set methods //
		//==================//
		
		public boolean contains(DhChunkPos pos) { return this.updateDataByChunkPos.containsKey(pos); }
		
		public void clear()
		{
			this.updateDataByChunkPos.clear();
			this.closestQueue.clear();
			this.furthestQueue.clear();
		}
		
		public void removeItem(DhChunkPos pos)
		{
			this.updateDataByChunkPos.remove(pos);
			this.closestQueue.remove(pos);
			this.furthestQueue.remove(pos);
		}
		
		/**
		 * Adds an item to the queue of chunks that need to be updated.
		 * If there are no more slots, replaces the item furthest from the center.
		 *
		 * @return The number of remaining slots available in the queue.
		 */
		public int addItem(DhChunkPos pos, UpdateChunkData updateData)
		{
			int remainingSlots = this.maxSize - this.updateDataByChunkPos.size();
			if (this.updateDataByChunkPos.containsKey(pos))
			{
				// Chunk is already present in queue, no need to insert
				return remainingSlots;
			}
			
			// If no slots are left, get one by removing the item furthest from the center
			if (remainingSlots <= 0)
			{
				DhChunkPos furthest = this.furthestQueue.poll();
				if (furthest != null)
				{
					this.closestQueue.remove(furthest);
					this.updateDataByChunkPos.remove(furthest);
				}
			}
			
			this.updateDataByChunkPos.put(pos, updateData);
			this.closestQueue.add(pos);
			this.furthestQueue.add(pos);
			
			return remainingSlots;
		}
		
		
		
		//==================//
		// position methods //
		//==================//
		
		public void setCenter(DhChunkPos newCenter)
		{
			// if the rebuild time takes too long 
			// (in James' testing a queue of 500 items only took around 0.1 milliseconds)
			// this equation could be changed to only update after moving 2 or 4 chunks from the center
			if (newCenter.equals(this.center))
			{
				return;
			}
			
			this.center = newCenter;
			
			// rebuild the priority queues to match the new center
			this.closestQueue.clear();
			this.furthestQueue.clear();
			for (DhChunkPos pos : this.updateDataByChunkPos.keySet())
			{
				this.closestQueue.add(pos);
				this.furthestQueue.add(pos);
			}
		}
		
		public UpdateChunkData popClosest()
		{
			if (this.closestQueue.isEmpty())
			{
				return null;
			}
			
			DhChunkPos closest = this.closestQueue.poll();
			if (closest == null)
			{
				return null;
			}
			
			this.furthestQueue.remove(closest);
			return this.updateDataByChunkPos.remove(closest);
		}
		
	}
	
	
	
}
