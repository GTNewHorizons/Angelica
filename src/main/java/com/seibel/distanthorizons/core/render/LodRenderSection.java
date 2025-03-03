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

package com.seibel.distanthorizons.core.render;

import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.CachedColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.generic.BeaconRenderHandler;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.repo.BeaconBeamRepo;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.annotation.WillNotClose;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A render section represents an area that could be rendered.
 * For more information see {@link LodQuadTree}.
 */
public class LodRenderSection implements IDebugRenderable, AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	/**
	 * Used to limit how many upload tasks are queued at once.
	 * If all the upload tasks are queued at once, they will start uploading nearest
	 * to the player, however if the player moves, that order is no longer valid and holes may appear
	 * as further sections are loaded before closer ones.
	 * Only queuing a few of the sections at a time solves this problem.
	 */
	public static final AtomicInteger GLOBAL_UPLOAD_TASKS_COUNT_REF = new AtomicInteger(0);
	
	
	
	public final long pos;
	
	private final IDhClientLevel level;
	@WillNotClose
	private final FullDataSourceProviderV2 fullDataSourceProvider;
	private final LodQuadTree quadTree;
	private final KeyedLockContainer<Long> renderLoadLockContainer;
	private final Cache<Long, CachedColumnRenderSource> cachedRenderSourceByPos;
	
	/** 
	 * contains the list of beacons currently being rendered in this section 
	 * if this list is modified the {@link LodRenderSection#beaconRenderHandler} should be updated to match.
	 */
	private final List<BeaconBeamDTO> activeBeaconList = new ArrayList<>();
	@Nullable
	public final BeaconRenderHandler beaconRenderHandler;
	@Nullable
	public final BeaconBeamRepo beaconBeamRepo;
	
	
	private boolean renderingEnabled = false;
	
	/** this reference is necessary so we can determine what VBO to render */
	public ColumnRenderBuffer renderBuffer; 
	
	
	/** 
	 * Encapsulates everything between pulling data from the database (including neighbors)
	 * up to the point when geometry data is uploaded to the GPU.
	 */
	private CompletableFuture<Void> getAndBuildRenderDataFuture = null;
	@Nullable
	public CompletableFuture<Void> getRenderDataBuildFuture() { return this.getAndBuildRenderDataFuture; } 
	
	/** 
	 * used alongside {@link LodRenderSection#getAndBuildRenderDataFuture} so we can remove
	 * unnecessary tasks from the executor.
	 */
	private Runnable getAndBuildRenderDataRunnable = null;
	
	/** 
	 * Represents just uploading the {@link LodQuadBuilder} to the GPU. <br>
	 * Separate from {@link LodRenderSection#getAndBuildRenderDataFuture} because they run on
	 * different threads (buffer uploading is on the MC render thread) and need to be canceled separately.
	 */
	private CompletableFuture<ColumnRenderBuffer> bufferUploadFuture = null;

	/** 
	 * should be an empty array if no positions need to be generated
	 * 
	 * @deprecated see the comment where this variable is set
	 */
	@Nullable
	@Deprecated
	private Supplier<LongArrayList> missingGenerationPosFunc;
	private LongArrayList getMissingGenerationPos() { return this.missingGenerationPosFunc != null ? this.missingGenerationPosFunc.get() : null; }
	
	private boolean checkedIfFullDataSourceExists = false;
	private boolean fullDataSourceExists = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodRenderSection(
			long pos, 
			LodQuadTree quadTree, 
			IDhClientLevel level, FullDataSourceProviderV2 fullDataSourceProvider, 
			Cache<Long, CachedColumnRenderSource> cachedRenderSourceByPos, KeyedLockContainer<Long> renderLoadLockContainer)
	{
		this.pos = pos;
		this.quadTree = quadTree;
		this.cachedRenderSourceByPos = cachedRenderSourceByPos;
		this.renderLoadLockContainer = renderLoadLockContainer;
		this.level = level;
		this.fullDataSourceProvider = fullDataSourceProvider;
		
		this.beaconRenderHandler = this.quadTree.beaconRenderHandler;
		this.beaconBeamRepo = this.level.getBeaconBeamRepo();
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
	}
	
	
	
	//======================================//
	// render data generation and uploading //
	//======================================//
	
	/** @return true if the upload started, false if it wasn't able to for any reason */
	public synchronized boolean uploadRenderDataToGpuAsync()
	{
		if (!GLProxy.hasInstance())
		{
			// it's possible to try uploading buffers before the GLProxy has been initialized
			// which would cause the system to crash
			return false;
		}
		
		if (this.getAndBuildRenderDataFuture != null)
		{
			// don't accidentally queue multiple uploads at the same time
			return false;
		}
		
		PriorityTaskPicker.Executor executor = ThreadPoolUtil.getFileHandlerExecutor();
		if (executor == null || executor.isTerminated())
		{
			return false;
		}
		
		// Only queue a some of the upload tasks at a time,
		// this means the closer (higher priority) tasks will load first.
		// This also prevents issues where the nearby tasks are canceled due to
		// LOD detail level changing, and having holes in the world
		if (GLOBAL_UPLOAD_TASKS_COUNT_REF.getAndIncrement() > executor.getPoolSize())
		{
			GLOBAL_UPLOAD_TASKS_COUNT_REF.decrementAndGet();
			return false;
		}
		
		try
		{
			CompletableFuture<Void> future = new CompletableFuture<>();
			this.getAndBuildRenderDataFuture = future;
			future.handle((voidObj, throwable) -> 
			{
				// this has to fire are the end of every added future, otherwise we'll lock up and nothing will load
				GLOBAL_UPLOAD_TASKS_COUNT_REF.decrementAndGet(); 
				return null; 
			});
			
			this.getAndBuildRenderDataRunnable = () ->
			{
				this.getAndRefreshRenderingBeacons();
				this.getAndUploadRenderDataToGpuAsync()
					.thenRun(() -> 
					{
						// the future is passed in separately (IE not using the local var) to prevent any possible race condition null pointers
						future.complete(null);
						// the task is done, we don't need to track these anymore
						this.getAndBuildRenderDataFuture = null;
						this.getAndBuildRenderDataRunnable = null;
					});
			};
			executor.execute(this.getAndBuildRenderDataRunnable);
			
			return true;
		}
		catch (RejectedExecutionException ignore)
		{
			this.getAndBuildRenderDataFuture.complete(null);
			this.getAndBuildRenderDataFuture = null;
			this.getAndBuildRenderDataRunnable = null;
			
			/* the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back */
			return false;
		}
	}
	private CompletableFuture<Void> getAndUploadRenderDataToGpuAsync()
	{
		// get the center pos data
		return this.getRenderSourceForPosAsync(this.pos)
			.thenCompose((CachedColumnRenderSource cachedRenderSource) -> 
			{
				try
				{
					if (cachedRenderSource == null || cachedRenderSource.columnRenderSource == null)
					{
						// nothing needs to be rendered
						// TODO how doesn't this cause infinite file handler loops?
						//  to trigger an upload we check if the buffer is null, and we aren't
						//  setting the render buffer here
						return CompletableFuture.completedFuture(null);
					}
					ColumnRenderSource thisRenderSource = cachedRenderSource.columnRenderSource;
					
					
					boolean enableTransparency = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
					LodQuadBuilder lodQuadBuilder = new LodQuadBuilder(enableTransparency, this.level.getClientLevelWrapper());
					
					
					// get the adjacent positions
					// needs to be done async to prevent threads waiting on the same positions to be processed
					final CompletableFuture<CachedColumnRenderSource>[] adjacentLoadFutures = new CompletableFuture[4];
					adjacentLoadFutures[0] = this.getRenderSourceForPosAsync(DhSectionPos.getAdjacentPos(this.pos, EDhDirection.NORTH));
					adjacentLoadFutures[1] = this.getRenderSourceForPosAsync(DhSectionPos.getAdjacentPos(this.pos, EDhDirection.SOUTH));
					adjacentLoadFutures[2] = this.getRenderSourceForPosAsync(DhSectionPos.getAdjacentPos(this.pos, EDhDirection.EAST));
					adjacentLoadFutures[3] = this.getRenderSourceForPosAsync(DhSectionPos.getAdjacentPos(this.pos, EDhDirection.WEST));
					return CompletableFuture.allOf(adjacentLoadFutures).thenRun(() ->
					{
						try (CachedColumnRenderSource northRenderSource = adjacentLoadFutures[0].get();
								CachedColumnRenderSource southRenderSource = adjacentLoadFutures[1].get();
								CachedColumnRenderSource eastRenderSource = adjacentLoadFutures[2].get();
								CachedColumnRenderSource westRenderSource = adjacentLoadFutures[3].get())
						{
							ColumnRenderSource[] adjacentRenderSections = new ColumnRenderSource[EDhDirection.ADJ_DIRECTIONS.length];
							adjacentRenderSections[EDhDirection.NORTH.ordinal() - 2] = (northRenderSource != null) ? northRenderSource.columnRenderSource : null;
							adjacentRenderSections[EDhDirection.SOUTH.ordinal() - 2] = (southRenderSource != null) ? southRenderSource.columnRenderSource : null;
							adjacentRenderSections[EDhDirection.EAST.ordinal() - 2] = (eastRenderSource != null) ? eastRenderSource.columnRenderSource : null;
							adjacentRenderSections[EDhDirection.WEST.ordinal() - 2] = (westRenderSource != null) ? westRenderSource.columnRenderSource : null;
							
							boolean[] adjIsSameDetailLevel = new boolean[EDhDirection.ADJ_DIRECTIONS.length];
							adjIsSameDetailLevel[EDhDirection.NORTH.ordinal() - 2] = this.isAdjacentPosSameDetailLevel(EDhDirection.NORTH);
							adjIsSameDetailLevel[EDhDirection.SOUTH.ordinal() - 2] = this.isAdjacentPosSameDetailLevel(EDhDirection.SOUTH);
							adjIsSameDetailLevel[EDhDirection.EAST.ordinal() - 2] = this.isAdjacentPosSameDetailLevel(EDhDirection.EAST);
							adjIsSameDetailLevel[EDhDirection.WEST.ordinal() - 2] = this.isAdjacentPosSameDetailLevel(EDhDirection.WEST);
							
							// the render sources are only needed by this synchronous method,
							// then they can be closed
							ColumnRenderBufferBuilder.makeLodRenderData(lodQuadBuilder, thisRenderSource, this.level, adjacentRenderSections, adjIsSameDetailLevel);
							this.uploadToGpuAsync(lodQuadBuilder);
						}
						catch (Exception e)
						{
							LOGGER.error("Unexpected error while loading LodRenderSection [" + DhSectionPos.toString(this.pos) + "] adjacent data, Error: [" + e.getMessage() + "].", e);
						}
						finally
						{
							// can only be closed after the data has been processed and uploaded to the GPU
							cachedRenderSource.close();
						}
					});
				}
				catch (Exception e)
				{
					LOGGER.error("Unexpected error while loading LodRenderSection ["+DhSectionPos.toString(this.pos)+"], Error: [" + e.getMessage() + "].", e);
					return CompletableFuture.completedFuture(null);
				}
			});
	}
	/** async is done so each thread can run without waiting on others */
	private CompletableFuture<CachedColumnRenderSource> getRenderSourceForPosAsync(long pos) 
	{
		ReentrantLock lock = this.renderLoadLockContainer.getLockForPos(pos);
		try
		{
			// we don't want multiple threads attempting to load the same position at the same time,
			// and we don't want to access the cache while invalidating it on a different thread
			lock.lock();
			
			// use the cached data if possible
			CachedColumnRenderSource existingCachedRenderSource = this.cachedRenderSourceByPos.getIfPresent(pos);
			if (existingCachedRenderSource != null)
			{
				existingCachedRenderSource.markInUse();
				return existingCachedRenderSource.loadFuture;
			}
			
			
			
			PriorityTaskPicker.Executor executor = ThreadPoolUtil.getFileHandlerExecutor();
			if (executor == null || executor.isTerminated())
			{
				// should only happen if the threadpool is actively being re-sized
				return CompletableFuture.completedFuture(null);
			}
			
			
			// queue loading the render data
			CompletableFuture<CachedColumnRenderSource> loadFuture = new CompletableFuture<>();
			final CachedColumnRenderSource newCachedRenderSource = new CachedColumnRenderSource(loadFuture, lock, this.cachedRenderSourceByPos);
			executor.execute(() ->
			{
				// generate new render source
				try (FullDataSourceV2 fullDataSource = this.fullDataSourceProvider.get(pos))
				{
					newCachedRenderSource.columnRenderSource = FullDataToRenderDataTransformer.transformFullDataToRenderSource(fullDataSource, this.level);
				}
				catch (Exception e)
				{
					LOGGER.error("Unexpected issue creating render data for pos: ["+DhSectionPos.toString(pos)+"], error: ["+e.getMessage()+"].", e);
				}
				finally
				{
					loadFuture.complete(newCachedRenderSource);
				}
			});
			this.cachedRenderSourceByPos.put(pos, newCachedRenderSource);
			
			return loadFuture;
		}
		catch (RejectedExecutionException ignore)
		{
			// the thread pool was probably shut down because it's size is being changed, just wait a sec and it should be back
			return CompletableFuture.completedFuture(null);
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected issue getting and creating render data for pos: ["+DhSectionPos.toString(pos)+"], error: ["+e.getMessage()+"].", e);
			return CompletableFuture.completedFuture(null);
		}
		finally
		{
			lock.unlock();
		}
	}
	private boolean isAdjacentPosSameDetailLevel(EDhDirection direction)
	{
		long adjPos = DhSectionPos.getAdjacentPos(this.pos, direction);
		byte detailLevel = this.quadTree.calculateExpectedDetailLevel(new DhBlockPos2D(MC.getPlayerBlockPos()), adjPos);
		detailLevel += DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
		return detailLevel == DhSectionPos.getDetailLevel(this.pos);
	}
	private void uploadToGpuAsync(LodQuadBuilder lodQuadBuilder)
	{
		if (this.bufferUploadFuture != null)
		{
			// shouldn't normally happen, but just in case canceling the previous future
			// prevents the CPU from working on something that won't be used
			this.bufferUploadFuture.cancel(true);
		}
		
		this.bufferUploadFuture = ColumnRenderBufferBuilder.uploadBuffersAsync(this.level, this.pos, lodQuadBuilder);
		this.bufferUploadFuture.thenAccept((buffer) ->
		{
			// needed to clean up the old data
			ColumnRenderBuffer previousBuffer = this.renderBuffer;
			
			// upload complete
			this.renderBuffer = buffer.buffersUploaded ? buffer : null;
			this.getAndBuildRenderDataFuture = null;
			
			if (previousBuffer != null)
			{
				previousBuffer.close();
			}
		});
	}
	
	
	
	//========================//
	// getters and properties //
	//========================//
	
	public boolean canRender() { return this.renderBuffer != null; }
	
	public boolean getRenderingEnabled() { return this.renderingEnabled; }
	/**
	 * Separate from {@link LodRenderSection#onRenderingEnabled} and {@link LodRenderSection#onRenderingDisabled}
	 * since we need to trigger external changes in disabled -> enabled order
	 * so beacons are removed and then re-added.
	 * However, to prevent holes in the world when disabling sections we need to
	 * enable the new section(s) first before disabling the old one(s).
	 */
	public void setRenderingEnabled(boolean enabled) { this.renderingEnabled = enabled;}
	
	/** @see LodRenderSection#setRenderingEnabled */
	public void onRenderingEnabled() { this.startRenderingBeacons(); }
	/** @see LodRenderSection#setRenderingEnabled */
	public void onRenderingDisabled() 
	{
		this.stopRenderingBeacons();
		
		if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
		{
			// show that this position has just been disabled
			DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
					new DebugRenderer.Box(this.pos, 128f, 156f, 0.09f, Color.CYAN.darker()),
					0.2, 32f
				)
			);
		}
	}
	
	public boolean gpuUploadInProgress() { return this.getAndBuildRenderDataFuture != null; }
	
	
	
	//=================================//
	// full data retrieval (world gen) //
	//=================================//
	
	public boolean isFullyGenerated()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		return missingGenerationPos != null && missingGenerationPos.isEmpty();
	}
	/** Returns true if an LOD exists, regardless of what data is in it */
	public boolean getFullDataSourceExists() 
	{  
		if (!this.checkedIfFullDataSourceExists)
		{
			this.fullDataSourceExists = this.fullDataSourceProvider.repo.existsWithKey(this.pos);
			this.checkedIfFullDataSourceExists = true;
		}
		
		return this.fullDataSourceExists;
	}
	public void updateFullDataSourceExists() 
	{
		// we don't have any ability to remove LODs so we only
		// need to check if an LOD was previously missing
		if (!this.fullDataSourceExists)
		{
			this.checkedIfFullDataSourceExists = false;
			this.getFullDataSourceExists();
		}
	}
	
	public boolean missingPositionsCalculated() { return this.getMissingGenerationPos() != null; }
	public int ungeneratedPositionCount()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		return missingGenerationPos != null ? missingGenerationPos.size() : 0;
	}
	public int ungeneratedChunkCount()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		if (missingGenerationPos == null)
		{
			return 0;
		}
		
		int chunkCount = 0;
		// get the number of chunks each position contains
		for (int i = 0; i < missingGenerationPos.size(); i++)
		{
			int chunkWidth = DhSectionPos.getChunkWidth(missingGenerationPos.getLong(i));
			chunkCount += (chunkWidth * chunkWidth);
		}
		return chunkCount;
	}
	
	public void tryQueuingMissingLodRetrieval()
	{
		if (this.fullDataSourceProvider.canRetrieveMissingDataSources() && this.fullDataSourceProvider.canQueueRetrieval())
		{
			// calculate the missing positions if not already done
			if (this.missingGenerationPosFunc == null)
			{
				// TODO memoization may not be needed anymore.
				//  The expiring cache was originally used to fix a bug with N-sized multiplayer retrieval.
				//  In multiplayer, when moving into new chunks, DH would generate the highest quality LOD, causing it to load,
				//  and since said LOD was incomplete, there were holes, and the LOD wouldn't be queued for additional
				//  retrieval.
				//  However this doesn't appear to be the case as of 2025-2-7, so we might be able to just retrieve the
				//  positions once and keep them in memory forever.
				//  Currently the timeout is set to 10 minutes to test if memoization is actually needed.
				//  10 minutes allows for the LODs to eventually refresh while allowing us
				//  to test if the memoization is actually needed.
				this.missingGenerationPosFunc = Suppliers.memoizeWithExpiration(
						() -> this.fullDataSourceProvider.getPositionsToRetrieve(this.pos),
						10, TimeUnit.MINUTES)::get;
			}
			
			LongArrayList missingGenerationPos = this.getMissingGenerationPos();
			if (missingGenerationPos != null)
			{
				// queue from last to first to prevent shifting the array unnecessarily
				for (int i = missingGenerationPos.size() - 1; i >= 0; i--)
				{
					if (!this.fullDataSourceProvider.canQueueRetrieval())
					{
						// the data source provider isn't accepting any more jobs
						break;
					}
					
					long pos = missingGenerationPos.removeLong(i);
					boolean positionQueued = (this.fullDataSourceProvider.queuePositionForRetrieval(pos) != null);
					if (!positionQueued)
					{
						// shouldn't normally happen, but just in case
						missingGenerationPos.add(pos);
					}
				}
			}
		}
	}
	
	
	
	//=================//
	// beacon handling //
	//=================//
	
	/** gets the active beacon list and stops/starts beacon rendering as necessary */
	private void getAndRefreshRenderingBeacons()
	{
		// do nothing if beacon rendering or repos are unavailable
		if (this.beaconBeamRepo == null 
			|| this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		// Synchronized to prevent two threads for starting/stopping rendering at once
		// Shouldn't be necessary, but just in case.
		synchronized (this.activeBeaconList)
		{
			List<BeaconBeamDTO> activeBeacons = this.beaconBeamRepo.getAllBeamsForPos(this.pos);
			
			
			// stop rendering current beacons
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.stopRenderingBeaconAtPos(beam.blockPos);
			}
			
			// swap old and new active beacon list
			this.activeBeaconList.clear();
			this.activeBeaconList.addAll(activeBeacons);
			
			// start rendering new beacon list
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.startRenderingBeacon(beam);
			}
		}
	}
	
	private void stopRenderingBeacons()
	{
		// do nothing if beacon rendering is unavailable
		if (this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		synchronized (this.activeBeaconList)
		{
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.stopRenderingBeaconAtPos(beam.blockPos);
			}
		}
	}
	
	private void startRenderingBeacons()
	{
		// do nothing if beacon rendering is unavailable 
		if (this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		synchronized (this.activeBeaconList)
		{
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.startRenderingBeacon(beam);
			}
		}
	}
	
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		Color color = Color.red;
		if (this.renderingEnabled)
		{
			color = Color.green;
		}
		else if (this.getAndBuildRenderDataFuture != null)
		{
			color = Color.yellow;
		}
		else if (this.canRender())
		{
			color = Color.cyan;
		}
		
		debugRenderer.renderBox(new DebugRenderer.Box(this.pos, 400, 8f, Objects.hashCode(this), 0.1f, color));
	}
	
	@Override
	public String toString()
	{
		return  "pos=[" + DhSectionPos.toString(this.pos) + "] " +
				"enabled=[" + this.renderingEnabled + "] " +
				"uploading=[" + this.gpuUploadInProgress() + "] ";
	}
	
	@Override
	public void close()
	{
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
		
		if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
		{
			// show a particle for the closed section
			DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
					new DebugRenderer.Box(this.pos, 128f, 156f, 0.09f, Color.RED.darker()),
					0.5, 32f
				)
			);
		}
		
		
		this.stopRenderingBeacons();
		
		if (this.renderBuffer != null)
		{
			this.renderBuffer.close();
		}
		
		// removes any in-progress futures since they aren't needed any more
		CompletableFuture<Void> buildFuture = this.getAndBuildRenderDataFuture;
		if (buildFuture != null)
		{
			// remove the task from our executor if present
			// note: don't cancel the task since that prevents cleanup, we just don't want it to run
			PriorityTaskPicker.Executor executor = ThreadPoolUtil.getFileHandlerExecutor();
			if (executor != null && !executor.isTerminated())
			{
				Runnable runnable = this.getAndBuildRenderDataRunnable;
				if (runnable != null)
				{
					executor.remove(runnable);
				}
			}
		}
		
		CompletableFuture<ColumnRenderBuffer> uploadFuture = this.bufferUploadFuture;
		if (uploadFuture != null)
		{
			uploadFuture.cancel(true);
		}
		
		
		
		// remove any active world gen requests that may be for this position
		ThreadPoolExecutor executor = ThreadPoolUtil.getCleanupExecutor();
		// while this should generally be a fast operation 
		// this is run on a separate thread to prevent lag on the render thread
		executor.execute(() -> this.fullDataSourceProvider.removeRetrievalRequestIf((genPos) -> DhSectionPos.contains(this.pos, genPos)));
		
	}
	
	
	
}
