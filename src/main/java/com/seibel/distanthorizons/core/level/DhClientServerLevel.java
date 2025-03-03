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

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The level used on a singleplayer world */
public class DhClientServerLevel extends AbstractDhServerLevel implements IDhClientLevel
{
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public final ClientLevelModule clientside;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhClientServerLevel(ISaveStructure saveStructure, IServerLevelWrapper serverLevelWrapper, ServerPlayerStateManager serverPlayerStateManager)
	{
		super(saveStructure, serverLevelWrapper, serverPlayerStateManager, false);
		
		this.serverLevelWrapper.setParentLevel(this);
		this.clientside = new ClientLevelModule(this);
		this.runRepoReliantSetup();
	}
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	@Override
	public void clientTick() { this.clientside.clientTick(); }
	
	@Override
	public void render(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.render(renderEventParam, profiler); }
	
	@Override
	public void renderDeferred(DhApiRenderParam renderEventParam, IProfilerWrapper profiler)
	{ this.clientside.renderDeferred(renderEventParam, profiler); }
	
	//========//
	// render //
	//========//
	
	public void startRenderer(IClientLevelWrapper clientLevel) { this.clientside.startRenderer(clientLevel); }
	
	public void stopRenderer() { this.clientside.stopRenderer(); }
	
	
	
	//================//
	// level handling //
	//================//
	
	@Override //FIXME this can fail if the clientLevel isn't available yet, maybe in that case we could return -1 and handle it upstream?
	public int computeBaseColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper block)
	{
		IClientLevelWrapper clientLevel = this.getClientLevelWrapper();
		if (clientLevel == null)
		{
			return 0;
		}
		else
		{
			return clientLevel.getBlockColor(pos, biome, block);
		}
	}
	
	@Override
	public IClientLevelWrapper getClientLevelWrapper() { return MC_CLIENT.getWrappedClientLevel(); }
	
	@Override
	public void clearRenderCache() { this.clientside.clearRenderCache(); }
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		// header
		String dimName = this.serverLevelWrapper.getDhIdentifier();
		boolean rendering = this.clientside.isRendering();
		messageList.add("["+dimName+"] rendering: "+(rendering ? "yes" : "no"));
		
		super.addDebugMenuStringsToList(messageList);
	}
	
	
	@Override
	public GenericObjectRenderer getGenericRenderer() { return this.clientside.genericRenderer; }
	@Override
	public RenderBufferHandler getRenderBufferHandler()
	{
		ClientLevelModule.ClientRenderState renderState = this.clientside.ClientRenderStateRef.get();
		return (renderState != null) ? renderState.renderBufferHandler : null;
	}
	
	
	
	//===============//
	// data handling //
	//===============//
	
	@Override
	public void onWorldGenTaskComplete(long pos)
	{
		super.onWorldGenTaskComplete(pos);
		
		DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
						new DebugRenderer.Box(pos, 128f, 156f, 0.09f, Color.red.darker()),
						0.2, 32f
				)
		);
		
		this.clientside.reloadPos(pos);
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return "DhClientServerLevel{"+this.serverLevelWrapper.getKeyedLevelDimensionName()+"}"; }
	
	@Override
	public void close()
	{
		this.clientside.close();
		super.close();
		this.serverside.close();
		LOGGER.info("Closed " + this.getClass().getSimpleName() + " for " + this.getServerLevelWrapper());
	}
	
}
