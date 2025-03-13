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

import com.google.common.cache.CacheBuilder;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.AppliedConfigState;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.fullDatafile.RemoteFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.generation.RemoteWorldRetrievalQueue;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState;
import com.seibel.distanthorizons.core.multiplayer.client.SyncOnLoadRequestQueue;
import com.seibel.distanthorizons.core.network.event.ScopedNetworkEventSource;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataPartialUpdateMessage;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** The level used when connected to a server */
public class DhClientLevel extends AbstractDhLevel implements IDhClientLevel
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public final ClientLevelModule clientside;
	public final IClientLevelWrapper levelWrapper;
	public final ISaveStructure saveStructure;
	public final RemoteFullDataSourceProvider dataFileHandler;
	
	@CheckForNull
	private final ClientNetworkState networkState;
	@Nullable
	private final ScopedNetworkEventSource networkEventSource;
	
	private final Set<DhChunkPos> loadedOnceChunks = Collections.newSetFromMap(
			CacheBuilder.newBuilder()
					.expireAfterWrite(10, TimeUnit.MINUTES)
					.<DhChunkPos, Boolean>build()
					.asMap()
	);
	
	public final WorldGenModule worldGenModule;
	public final AppliedConfigState<Boolean> worldGeneratorEnabledConfig;
	
	@Nullable
	private final SyncOnLoadRequestQueue syncOnLoadRequestQueue;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhClientLevel(ISaveStructure saveStructure, IClientLevelWrapper clientLevelWrapper, @Nullable ClientNetworkState networkState) 
	{ this(saveStructure, clientLevelWrapper, null, true, networkState); }
	public DhClientLevel(ISaveStructure saveStructure, IClientLevelWrapper clientLevelWrapper, @Nullable File fullDataSaveDirOverride, boolean enableRendering, @Nullable ClientNetworkState networkState)
	{
		File saveFolder = saveStructure.getSaveFolder(clientLevelWrapper);
		File pre23Folder = saveStructure.getPre23SaveFolder(clientLevelWrapper);
		
		if (pre23Folder.exists())
		{
			if (!pre23Folder.renameTo(saveFolder))
			{
				throw new RuntimeException("Could not move old save data folder: " + pre23Folder.getAbsolutePath() + " to " + saveFolder.getAbsolutePath());
			}
		}
		else if (saveStructure.getSaveFolder(clientLevelWrapper).mkdirs())
		{
			LOGGER.warn("unable to create data folder.");
		}
		
		this.levelWrapper = clientLevelWrapper;
		this.levelWrapper.setParentLevel(this);
		this.saveStructure = saveStructure;
		
		this.networkState = networkState;
		if (this.networkState != null)
		{
			this.networkEventSource = new ScopedNetworkEventSource(this.networkState.getSession());
			this.syncOnLoadRequestQueue = new SyncOnLoadRequestQueue(this, this.networkState);
			this.registerNetworkHandlers();
		}
		else
		{
			this.networkEventSource = null;
			this.syncOnLoadRequestQueue = null;
		}
		
		this.dataFileHandler = new RemoteFullDataSourceProvider(this, saveStructure, fullDataSaveDirOverride, this.syncOnLoadRequestQueue);
		this.worldGeneratorEnabledConfig = new AppliedConfigState<>(Config.Common.WorldGenerator.enableDistantGeneration);
		this.worldGenModule = new WorldGenModule(this, this.dataFileHandler, () -> new WorldGenState(this, networkState));
		
		this.clientside = new ClientLevelModule(this);
		
		this.createAndSetSupportingRepos(this.dataFileHandler.repo.databaseFile);
		this.runRepoReliantSetup();
		
		if (enableRendering)
		{
			this.clientside.startRenderer(clientLevelWrapper);
			LOGGER.info("Started DHLevel for " + this.levelWrapper + " with saves at " + this.saveStructure);
		}
	}
	private void registerNetworkHandlers()
	{
		assert this.networkEventSource != null;
		assert this.networkState != null;
		
		this.networkEventSource.registerHandler(FullDataPartialUpdateMessage.class, message ->
		{
			if (MC_CLIENT.connectedToReplay())
			{
				return;
			}
			
			try (FullDataSourceV2DTO dataSourceDto = this.networkState.fullDataPayloadReceiver.decodeDataSourceAndReleaseBuffer(message.payload))
			{
				if (!message.isSameLevelAs(this.levelWrapper))
				{
					return;
				}
				
				this.updateBeaconBeamsForSectionPos(dataSourceDto.pos, message.payload.beaconBeams);
				
				FullDataSourceV2 fullDataSource = dataSourceDto.createDataSource(this.levelWrapper);
				this.updateDataSourcesAsync(fullDataSource).whenComplete((result, e) -> fullDataSource.close());
			}
			catch (Exception e)
			{
				LOGGER.error("Error while updating full data source", e);
			}
		});
	}
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	@Override
	public void clientTick()
	{
		try
		{
			this.clientside.clientTick();
			
			if (this.syncOnLoadRequestQueue != null)
			{
				this.syncOnLoadRequestQueue.tick(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Unexpected clientTick Exception: "+e.getMessage(), e);
		}
	}
	
	@Override
	public boolean shouldDoWorldGen()
	{
		ClientNetworkState networkState = this.networkState;
		
		boolean isClientUsable = false, isAllowedDimension = false;
		if (networkState != null)
		{
			isClientUsable = networkState.isReady();
			isAllowedDimension = MC_CLIENT.getWrappedClientLevel() == this.levelWrapper;
		}
		
		return isClientUsable
				&& networkState.sessionConfig.isDistantGenerationEnabled()
				&& isAllowedDimension
				&& this.clientside.isRendering();
	}
	
	@Override
	@Nullable
	public DhBlockPos2D getTargetPosForGeneration()
	{
		return new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos());
	}
	
	@Override
	public void worldGenTick()
	{
		this.worldGenModule.worldGenTick();
	}
	
	
	
	//===========//
	// rendering //
	//===========//
	
	@Override
	public void render(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.render(renderEventParam, profiler); }
	
	@Override
	public void renderDeferred(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.renderDeferred(renderEventParam, profiler); }
	
	
	
	//===========//
	// world gen //
	//===========//
	
	@Override
	public void onWorldGenTaskComplete(long pos)
	{
		DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
						new DebugRenderer.Box(pos, 128f, 156f, 0.09f, Color.red.darker()),
						0.2, 32f
				)
		);
		
		this.clientside.reloadPos(pos);
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	public int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper block) { return this.levelWrapper.getBlockColor(pos, biome, block); }
	
	@Override
	public IClientLevelWrapper getClientLevelWrapper() { return this.levelWrapper; }
	
	@Override
	public void clearRenderCache() { this.clientside.clearRenderCache(); }
	
	@Override
	public ILevelWrapper getLevelWrapper() { return this.levelWrapper; }
	
	@Override
	public CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data) { return this.clientside.updateDataSourcesAsync(data); }
	
	@Override
	public int getMinY() { return this.levelWrapper.getMinHeight(); }
	
	@Override
	public FullDataSourceProviderV2 getFullDataProvider() { return this.dataFileHandler; }
	
	@Override
	public ISaveStructure getSaveStructure() { return this.saveStructure; }
	
	@Override
	public boolean hasSkyLight() { return this.levelWrapper.hasSkyLight(); }
	
	@Override
	public GenericObjectRenderer getGenericRenderer() { return this.clientside.genericRenderer; }
	@Override
	public RenderBufferHandler getRenderBufferHandler()
	{
		ClientLevelModule.ClientRenderState renderState = this.clientside.ClientRenderStateRef.get();
		return (renderState != null) ? renderState.renderBufferHandler : null;
	}
	
	public boolean shouldProcessChunkUpdate(DhChunkPos chunkPos)
	{
		if (this.networkState == null || !this.networkState.isReady())
		{
			return true;
		}
		
		return !this.networkState.sessionConfig.isRealTimeUpdatesEnabled() || this.loadedOnceChunks.add(chunkPos);
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		String dimName = this.levelWrapper.getDhIdentifier();
		boolean rendering = this.clientside.isRendering();
		messageList.add("["+dimName+"] rendering: "+(rendering ? "yes" : "no"));
		
		
		boolean migrationErrored = this.dataFileHandler.getMigrationStoppedWithError();
		if (!migrationErrored)
		{
			long legacyDeletionCount = this.dataFileHandler.getLegacyDeletionCount();
			if (legacyDeletionCount > 0)
			{
				messageList.add("  Migrating - Deleting #: " + legacyDeletionCount);
			}
			long migrationCount = this.dataFileHandler.getTotalMigrationCount();
			if (migrationCount > 0)
			{
				messageList.add("  Migrating - Conversion #: " + migrationCount);
			}
		}
		else
		{
			messageList.add("  Migration Failed");
		}
		
		
		// world gen
		this.worldGenModule.addDebugMenuStringsToList(messageList);
		if (this.syncOnLoadRequestQueue != null)
		{
			assert this.networkState != null;
			if (this.networkState.sessionConfig.getSynchronizeOnLoad())
			{
				this.syncOnLoadRequestQueue.addDebugMenuStringsToList(messageList);
			}
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return "DhClientLevel{" + this.getClientLevelWrapper().getDhIdentifier() + "}"; }
	
	@Override
	public void close()
	{
		if (this.worldGenModule != null)
		{
			this.worldGenModule.close();
		}
		
		if (this.networkEventSource != null)
		{
			this.networkEventSource.close();
		}
		
		this.levelWrapper.setParentLevel(null);
		this.clientside.close();
		super.close();
		this.dataFileHandler.close();
		LOGGER.info("Closed [" + DhClientLevel.class.getSimpleName() + "] for [" + this.levelWrapper + "]");
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class WorldGenState extends WorldGenModule.AbstractWorldGenState
	{
		WorldGenState(DhClientLevel level, ClientNetworkState networkState)
		{
			this.worldGenerationQueue = new RemoteWorldRetrievalQueue(networkState, level);
		}
	}
	
}
