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

package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.file.structure.ClientOnlySaveStructure;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.EventLoop;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class DhClientWorld extends AbstractDhWorld implements IDhClientWorld
{
	private final ConcurrentHashMap<IClientLevelWrapper, DhClientLevel> levels;
	public final ClientOnlySaveStructure saveStructure;
	public final ClientNetworkState networkState = new ClientNetworkState();
	
	public ExecutorService dhTickerThread = ThreadUtil.makeSingleThreadPool("Client World Ticker Thread");
	public EventLoop eventLoop = new EventLoop(this.dhTickerThread, this::_clientTick);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhClientWorld()
	{
		super(EWorldEnvironment.CLIENT_ONLY);
		
		this.saveStructure = new ClientOnlySaveStructure();
		this.levels = new ConcurrentHashMap<>();
		
		LOGGER.info("Started DhWorld of type " + this.environment);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public DhClientLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		
		return this.levels.computeIfAbsent((IClientLevelWrapper) wrapper, 
				(clientLevelWrapper) -> new DhClientLevel(this.saveStructure, clientLevelWrapper, this.networkState));
	}
	
	@Override
	public DhClientLevel getLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return null;
		}
		
		return this.levels.get(wrapper);
	}
	
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() { return this.levels.values(); }
	@Override
	public int getLoadedLevelCount() { return this.levels.size(); }
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (!(wrapper instanceof IClientLevelWrapper))
		{
			return;
		}
		
		if (this.levels.containsKey(wrapper))
		{
			LOGGER.info("Unloading level " + this.levels.get(wrapper));
			wrapper.onUnload();
			this.levels.remove(wrapper).close();
		}
	}
	
	private void _clientTick() { this.levels.values().forEach(DhClientLevel::clientTick); }
	
	@Override 
	public void clientTick() { this.eventLoop.tick(); }
	
	@Override 
	public void worldGenTick() { this.levels.values().forEach(DhClientLevel::worldGenTick); }
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		super.addDebugMenuStringsToList(messageList);
		this.networkState.addDebugMenuStringsToList(messageList);
	}
	
	@Override
	public void close()
	{
		this.networkState.close();
		
		
		for (DhClientLevel dhClientLevel : this.levels.values())
		{
			LOGGER.info("Unloading level [" + dhClientLevel.getLevelWrapper().getDhIdentifier() + "].");
			
			// level wrapper shouldn't be null, but just in case
			IClientLevelWrapper clientLevelWrapper = dhClientLevel.getClientLevelWrapper();
			if (clientLevelWrapper != null)
			{
				clientLevelWrapper.onUnload();
			}
			
			dhClientLevel.close();
		}
		
		this.levels.clear();
		this.eventLoop.close();
		LOGGER.info("Closed DhWorld of type [" + this.environment + "].");
	}
	
}
