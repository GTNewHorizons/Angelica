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

import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.AbstractDataSourceHandler;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.LodQuadTree;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import org.apache.logging.log4j.Logger;

import javax.annotation.WillNotClose;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ClientLevelModule implements Closeable, AbstractDataSourceHandler.IDataSourceUpdateFunc<FullDataSourceV2>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private final IDhClientLevel clientLevel;
	
	@WillNotClose
	public final FullDataSourceProviderV2 fullDataSourceProvider;
	public final AtomicReference<ClientRenderState> ClientRenderStateRef = new AtomicReference<>();
	/** 
	 * This is handled outside of the {@link ClientRenderState} to prevent destroying
	 * the {@link GenericObjectRenderer} when changing render distances or enabling/disabling rendering. <br><br>
	 * 
	 * Destroying the {@link GenericObjectRenderer} would cause any existing bindings to be 
	 * erroneously removed.
	 */
	public final GenericObjectRenderer genericRenderer = new GenericObjectRenderer();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ClientLevelModule(IDhClientLevel clientLevel)
	{
		this.clientLevel = clientLevel;
		
		this.fullDataSourceProvider = this.clientLevel.getFullDataProvider();
		this.fullDataSourceProvider.dateSourceUpdateListeners.add(this);
	}
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	private EDhApiDebugRendering lastDebugRendering = EDhApiDebugRendering.OFF;
	
	public void clientTick()
	{
		// can be false if the level is unloading
		if (!MC_CLIENT.playerExists())
		{
			return;
		}
		
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState == null)
		{
			return;
		}
		// TODO this should probably be handled via a config change listener
		// recreate the RenderState if the render distance changes
		if (clientRenderState.quadtree.blockRenderDistanceDiameter != Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH * 2)
		{
			if (!this.ClientRenderStateRef.compareAndSet(clientRenderState, null))
			{
				return;
			}
			
			IClientLevelWrapper clientLevelWrapper = this.clientLevel.getClientLevelWrapper();
			if (clientLevelWrapper == null)
			{
				return;
			}
			
			clientRenderState.close();
			clientRenderState = new ClientRenderState(this.clientLevel, clientLevelWrapper, this.clientLevel.getFullDataProvider(), this.genericRenderer);
			if (!this.ClientRenderStateRef.compareAndSet(null, clientRenderState))
			{
				//FIXME: How to handle this?
				LOGGER.warn("Failed to set render state due to concurrency after changing view distance");
				clientRenderState.close();
				return;
			}
		}
		clientRenderState.quadtree.tick(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
		
		boolean isBuffersDirty = false;
		EDhApiDebugRendering newDebugRendering = Config.Client.Advanced.Debugging.debugRendering.get();
		if (newDebugRendering != lastDebugRendering)
		{
			lastDebugRendering = newDebugRendering;
			isBuffersDirty = true;
		}
		if (isBuffersDirty)
		{
			clientRenderState.lodRenderer.bufferHandler.MarkAllBuffersDirty();
		}
	}
	
	
	//========//
	// render //
	//========//
	
	/** @return if the {@link ClientRenderState} was successfully swapped */
	public boolean startRenderer(IClientLevelWrapper clientLevelWrapper)
	{
		// TODO why are we passing in a level wrapper? Our client level is already defined.
		ClientRenderState ClientRenderState = new ClientRenderState(this.clientLevel, clientLevelWrapper, this.clientLevel.getFullDataProvider(), this.genericRenderer);
		if (!this.ClientRenderStateRef.compareAndSet(null, ClientRenderState))
		{
			LOGGER.warn("Failed to start renderer due to concurrency");
			ClientRenderState.close();
			return false;
		}
		else
		{
			return true;
		}
	}
	
	public boolean isRendering()
	{
		return this.ClientRenderStateRef.get() != null;
	}
	
	public void render(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			// either the renderer hasn't been started yet, or is being reloaded
			return;
		}
		ClientRenderState.lodRenderer.drawLods(ClientRenderState.clientLevelWrapper, renderEventParam, profiler);
	}
	
	public void renderDeferred(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			// either the renderer hasn't been started yet, or is being reloaded
			return;
		}
		ClientRenderState.lodRenderer.drawDeferredLods(ClientRenderState.clientLevelWrapper, renderEventParam, profiler);
	}
	
	public void stopRenderer()
	{
		LOGGER.info("Stopping renderer for " + this);
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			LOGGER.warn("Tried to stop renderer for " + this + " when it was not started!");
			return;
		}
		// stop the render state
		while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null)) // TODO why is there a while loop here?
		{
			ClientRenderState = this.ClientRenderStateRef.get();
			if (ClientRenderState == null)
			{
				return;
			}
		}
		ClientRenderState.close();
	}
	
	
	
	//===============//
	// data handling //
	//===============//
	
	public CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data) { return this.clientLevel.getFullDataProvider().updateDataSourceAsync(data); }
	@Override
	public void OnDataSourceUpdated(FullDataSourceV2 updatedFullDataSource)
	{
		// if rendering, also update the render sources
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			ClientRenderState.quadtree.reloadPos(updatedFullDataSource.getPos());
		}
	}
	
	public void close()
	{
		// shutdown the renderer
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			// TODO does this have to be in a while loop, if so why?
			while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null))
			{
				ClientRenderState = this.ClientRenderStateRef.get();
				if (ClientRenderState == null)
				{
					break;
				}
			}
			
			if (ClientRenderState != null)
			{
				ClientRenderState.close();
			}
		}
		
		this.fullDataSourceProvider.dateSourceUpdateListeners.remove(this);
	}
	
	
	
	//=======================//
	// misc helper functions //
	//=======================//
	
	public void clearRenderCache()
	{
		this.clientLevel.getClientLevelWrapper().clearBlockColorCache();
		
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null && ClientRenderState.quadtree != null)
		{
			ClientRenderState.quadtree.clearRenderDataCache();
		}
	}
	
	public void reloadPos(long pos)
	{
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState != null && clientRenderState.quadtree != null)
		{
			clientRenderState.quadtree.reloadPos(pos);
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class ClientRenderState implements Closeable
	{
		private static final Logger LOGGER = DhLoggerBuilder.getLogger();
		
		public final IClientLevelWrapper clientLevelWrapper;
		public final LodQuadTree quadtree;
		public final RenderBufferHandler renderBufferHandler;
		public final LodRenderer lodRenderer;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public ClientRenderState(
				IDhClientLevel dhClientLevel, IClientLevelWrapper clientLevelWrapper, 
				FullDataSourceProviderV2 fullDataSourceProvider,
				GenericObjectRenderer genericRenderer)
		{
			this.clientLevelWrapper = clientLevelWrapper;
			
			this.quadtree = new LodQuadTree(dhClientLevel, Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH * 2,
					// initial position is (0,0) just in case the player hasn't loaded in yet, the tree will be moved once the level starts ticking
					0, 0,
					fullDataSourceProvider);
			
			this.renderBufferHandler = new RenderBufferHandler(this.quadtree);
			this.lodRenderer = new LodRenderer(this.renderBufferHandler, genericRenderer);
		}
		
		
		
		//================//
		// base overrides //
		//================//
		
		@Override
		public void close()
		{
			LOGGER.info("Shutting down " + ClientRenderState.class.getSimpleName());
			
			this.lodRenderer.close();
			this.quadtree.close();
		}
		
	}
	
}
