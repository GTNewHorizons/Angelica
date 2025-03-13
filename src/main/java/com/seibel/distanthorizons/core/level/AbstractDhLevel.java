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

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkModifiedEvent;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.DelayedFullDataSourceSaveCache;
import com.seibel.distanthorizons.core.generation.DhLightingEngine;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.renderer.generic.CloudRenderHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.dto.ChunkHashDTO;
import com.seibel.distanthorizons.core.sql.repo.AbstractDhRepo;
import com.seibel.distanthorizons.core.sql.repo.BeaconBeamRepo;
import com.seibel.distanthorizons.core.sql.repo.ChunkHashRepo;
import com.seibel.distanthorizons.core.util.KeyedLockContainer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractDhLevel implements IDhLevel
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** if this is null then the other handler is probably null too, but just in case */
	@Nullable
	public ChunkHashRepo chunkHashRepo;
	/** if this is null then the other handler is probably null too, but just in case */
	@Nullable
	public BeaconBeamRepo beaconBeamRepo;
	
	protected final KeyedLockContainer<Long> beaconUpdateLockContainer = new KeyedLockContainer<>();
	
	protected final DelayedFullDataSourceSaveCache delayedFullDataSourceSaveCache = new DelayedFullDataSourceSaveCache(this::onDataSourceSaveAsync, 3_000);
	/** contains the {@link DhChunkPos} for each {@link DhSectionPos} that are queued to save */
	protected final ConcurrentHashMap<Long, HashSet<DhChunkPos>> updatedChunkPosSetBySectionPos = new ConcurrentHashMap<>();
	protected final ConcurrentHashMap<DhChunkPos, Integer> updatedChunkHashesByChunkPos = new ConcurrentHashMap<>();
	
	/** Will be null if clouds shouldn't be rendered for this level. */
	@Nullable
	protected CloudRenderHandler cloudRenderHandler;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected AbstractDhLevel() {  }
	
	/** 
	 * Creating the repos requires access to the level file, which isn't
	 * available at constructor time.
	 */
	protected void createAndSetSupportingRepos(File databaseFile)
	{
		// chunk hash
		ChunkHashRepo newChunkHashRepo = null;
		try
		{
			newChunkHashRepo = new ChunkHashRepo(AbstractDhRepo.DEFAULT_DATABASE_TYPE, databaseFile);
		}
		catch (SQLException e)
		{
			LOGGER.error("Unable to create [ChunkHashRepo], error: ["+e.getMessage()+"].", e);
		}
		this.chunkHashRepo = newChunkHashRepo;
		
		
		// beacon beam
		BeaconBeamRepo newBeaconBeamRepo = null;
		try
		{
			newBeaconBeamRepo = new BeaconBeamRepo(AbstractDhRepo.DEFAULT_DATABASE_TYPE, databaseFile);
		}
		catch (SQLException e)
		{
			LOGGER.error("Unable to create [BeaconBeamRepo], error: ["+e.getMessage()+"].", e);
		}
		this.beaconBeamRepo = newBeaconBeamRepo;
	}
	
	/** handles any setup that needs the repos to be created */
	protected void runRepoReliantSetup()
	{
		GenericObjectRenderer genericRenderer = this.getGenericRenderer();
		if (genericRenderer != null)
		{
			// only client levels can render clouds
			if (this instanceof IDhClientLevel)
			{
				// only add clouds for certain dimension types
				if (!this.getLevelWrapper().hasCeiling()
						&& !this.getLevelWrapper().getDimensionType().isTheEnd())
				{
					this.cloudRenderHandler = new CloudRenderHandler((IDhClientLevel)this, genericRenderer);
				}
			}
		}
	}
	
	
	
	//=================//
	// default methods //
	//=================//
	
	@Override
	public void updateChunkAsync(IChunkWrapper chunkWrapper, int chunkHash)
	{
		try (FullDataSourceV2 dataSource = FullDataSourceV2.createFromChunk(chunkWrapper))
		{
			if (dataSource == null)
			{
				// This can happen if, among other reasons, a chunk save is superseded by a later event
				return;
			}
			
			
			this.updatedChunkPosSetBySectionPos.compute(dataSource.getPos(), (dataSourcePos, chunkPosSet) ->
			{
				if (chunkPosSet == null)
				{
					chunkPosSet = new HashSet<>();
				}
				chunkPosSet.add(chunkWrapper.getChunkPos());
				return chunkPosSet;
			});
			this.updatedChunkHashesByChunkPos.put(chunkWrapper.getChunkPos(), chunkHash);
			
			// merging writes together in memory significantly improves throughput, since most
			// chunk modifications will be right next to each other, IE effecting the same LODs
			this.delayedFullDataSourceSaveCache.writeDataSourceToMemoryAndQueueSave(dataSource);
		}
	}
	
	private CompletableFuture<Void> onDataSourceSaveAsync(FullDataSourceV2 fullDataSource)
	{
		// block lights should have been populated at the chunkWrapper stage
		// waiting to populate the data source's skylight at this stage prevents re-lighting and
		// allows us to reduce cross-chunk lighting issues by lighting the whole 4x4 LOD at once 
		DhLightingEngine.INSTANCE.bakeDataSourceSkyLight(fullDataSource, this.hasSkyLight() ? LodUtil.MAX_MC_LIGHT : LodUtil.MIN_MC_LIGHT);
		
		
		return this.updateDataSourcesAsync(fullDataSource)
			.thenRun(() -> 
			{
				HashSet<DhChunkPos> updatedChunkPosSet = this.updatedChunkPosSetBySectionPos.remove(fullDataSource.getPos());
				if (updatedChunkPosSet != null)
				{
					for (DhChunkPos chunkPos : updatedChunkPosSet)
					{
						// save after the data source has been updated to prevent saving the hash without the associated datasource
						Integer chunkHash = this.updatedChunkHashesByChunkPos.remove(chunkPos);
						if (this.chunkHashRepo != null && chunkHash != null)
						{
							this.chunkHashRepo.save(new ChunkHashDTO(chunkPos, chunkHash));
						}
						
						ApiEventInjector.INSTANCE.fireAllEvents(
								DhApiChunkModifiedEvent.class,
								new DhApiChunkModifiedEvent.EventParam(this.getLevelWrapper(), chunkPos.getX(), chunkPos.getZ()));
					}
				}
			});
	}
	
	
	
	//=======//
	// repos //
	//=======//
	
	// chunk hash //
	
	@Override
	public int getChunkHash(DhChunkPos pos)
	{
		if (this.chunkHashRepo == null)
		{
			return 0;
		}
		
		ChunkHashDTO dto = this.chunkHashRepo.getByKey(pos);
		return (dto != null) ? dto.chunkHash : 0;
	}
	
	
	
	//=================//
	// beacon handling //
	//=================//
	
	@Override
	public void updateBeaconBeamsForSectionPos(long sectionPos, List<BeaconBeamDTO> activeBeamList)
	{
		int minBlockX = DhSectionPos.getMinCornerBlockX(sectionPos);
		int minBlockZ = DhSectionPos.getMinCornerBlockZ(sectionPos);
		// TODO special logic had to be done for DhChunkPos.getMaxBlock,
		//  does that need to be done here?
		//  The DhChunkPos issue caused beacons to appear/disappear incorrectly on negative chunk borders
		int maxBlockX = minBlockX + DhSectionPos.getBlockWidth(sectionPos);
		int maxBlockZ = minBlockZ + DhSectionPos.getBlockWidth(sectionPos);
		
		this.updateBeaconBeamsBetweenBlockPos(
				sectionPos,
				minBlockX, maxBlockX,
				minBlockZ, maxBlockZ,
				activeBeamList
		);
	}
	
	@Override
	public void updateBeaconBeamsForChunkPos(DhChunkPos chunkPos, List<BeaconBeamDTO> activeBeamList)
	{
		long sectionPos = DhSectionPos.encodeContaining(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, chunkPos);
		
		int minBlockX = chunkPos.getMinBlockX();
		int minBlockZ = chunkPos.getMinBlockZ();
		int maxBlockX = chunkPos.getMaxBlockX();
		int maxBlockZ = chunkPos.getMaxBlockZ();
		
		//LOGGER.info("beacons ["+activeBeamList.size()+"] at ["+chunkPos+"] x["+minBlockX+"]-["+maxBlockX+"] z["+minBlockZ+"]-["+maxBlockZ+"].");
		
		this.updateBeaconBeamsBetweenBlockPos(
				sectionPos,
				minBlockX, maxBlockX,
				minBlockZ, maxBlockZ,
				activeBeamList
		);
	}
	
	private void updateBeaconBeamsBetweenBlockPos(
			long sectionPosForLock,
			int minBlockX, int maxBlockX,
			int minBlockZ, int maxBlockZ,
			List<BeaconBeamDTO> activeBeamList
		) // TODO min/max block pos instead
	{
		if (this.beaconBeamRepo == null)
		{
			return;
		}
		
		
		// locked to prevent two threads from updating the same section at the same time
		ReentrantLock lock = this.beaconUpdateLockContainer.getLockForPos(sectionPosForLock);
		try
		{
			lock.lock();
			
			HashSet<DhBlockPos> allPosSet = new HashSet<>();
			
			// sort new beams
			HashMap<DhBlockPos, BeaconBeamDTO> activeBeamByPos = new HashMap<>(activeBeamList.size());
			for (BeaconBeamDTO beam : activeBeamList)
			{
				activeBeamByPos.put(beam.blockPos, beam);
				allPosSet.add(beam.blockPos);
			}
			
			// get existing beams
			List<BeaconBeamDTO> existingBeamList = this.beaconBeamRepo.getAllBeamsInBlockPosRange(
					minBlockX, maxBlockX,
					minBlockZ, maxBlockZ);
			HashMap<DhBlockPos, BeaconBeamDTO> existingBeamByPos = new HashMap<>(existingBeamList.size());
			for (BeaconBeamDTO beam : existingBeamList)
			{
				existingBeamByPos.put(beam.blockPos, beam);
				allPosSet.add(beam.blockPos);
			}
			
			
			
			
			for (DhBlockPos beaconPos : allPosSet)
			{
				if (minBlockX <= beaconPos.getX() && beaconPos.getX() <= maxBlockX
					&& minBlockZ <= beaconPos.getZ() && beaconPos.getZ() <= maxBlockZ)
				{
					//// don't modify beacons outside the updated range
					//continue;
				}
				else
				{
					continue;
				}
				
				
				BeaconBeamDTO existingBeam = existingBeamByPos.get(beaconPos);
				BeaconBeamDTO activeBeam = activeBeamByPos.get(beaconPos);
				if (activeBeam != null)
				{
					//LOGGER.info("add beacon ["+activeBeam.blockPos+"] x["+minBlockX+"]-["+maxBlockX+"] z["+minBlockZ+"]-["+maxBlockZ+"].");
					
					if (existingBeam == null)
					{
						// new beam found, add to DB
						this.beaconBeamRepo.save(activeBeam);
					}
					else
					{
						// beam still exists in chunk
						if (!existingBeam.color.equals(activeBeam.color))
						{
							// beam colors were changed
							this.beaconBeamRepo.save(activeBeam);
						}
					}
				}
				else if (existingBeam != null)
				{
					// beam no longer exists at position, remove from DB
					this.beaconBeamRepo.deleteWithKey(beaconPos);
					//LOGGER.info("remove beacon ["+beaconPos+"] x["+minBlockX+"]-["+maxBlockX+"] z["+minBlockZ+"]-["+maxBlockZ+"].");
				}
			}
		}
		finally
		{
			lock.unlock();
		}
	}
	
	@Override
	@Nullable
	public BeaconBeamRepo getBeaconBeamRepo() { return this.beaconBeamRepo; }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close() 
	{ 
		if (this.chunkHashRepo != null)
		{
			this.chunkHashRepo.close();
		}
		if (this.beaconBeamRepo != null)
		{
			this.beaconBeamRepo.close();
		}
		
		this.delayedFullDataSourceSaveCache.close();
	}
	
}
