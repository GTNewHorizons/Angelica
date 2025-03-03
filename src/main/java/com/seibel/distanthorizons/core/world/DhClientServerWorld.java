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

import com.seibel.distanthorizons.core.level.DhClientServerLevel;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.objects.EventLoop;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class DhClientServerWorld extends AbstractDhServerWorld<DhClientServerLevel> implements IDhClientWorld
{
	private final Set<DhClientServerLevel> dhLevels = Collections.synchronizedSet(new HashSet<>());
	
	public ExecutorService dhTickerThread = ThreadUtil.makeSingleThreadPool("Client Server World Ticker Thread", 2);
	public EventLoop eventLoop = new EventLoop(this.dhTickerThread, this::_clientTick); //TODO: Rate-limit the loop
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhClientServerWorld()
	{
		super(EWorldEnvironment.CLIENT_SERVER);
		LOGGER.info("Started DhWorld of type " + this.environment);
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public DhClientServerLevel getOrLoadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (wrapper instanceof IServerLevelWrapper)
		{
			return this.dhLevelByLevelWrapper.computeIfAbsent(wrapper, (levelWrapper) ->
			{
				DhClientServerLevel level = new DhClientServerLevel(this.saveStructure, (IServerLevelWrapper) levelWrapper, this.getServerPlayerStateManager());
				this.dhLevels.add(level);
				return level;
			});
		}
		else
		{
			return this.dhLevelByLevelWrapper.computeIfAbsent(wrapper, (levelWrapper) ->
			{
				IClientLevelWrapper clientLevelWrapper = (IClientLevelWrapper) levelWrapper;
				IServerLevelWrapper serverLevelWrapper = clientLevelWrapper.tryGetServerSideWrapper();
				LodUtil.assertTrue(serverLevelWrapper != null);
				if (!clientLevelWrapper.getDimensionType().equals(serverLevelWrapper.getDimensionType()))
				{
					LodUtil.assertNotReach("tryGetServerSideWrapper returned a level for a different dimension. ClientLevelWrapper dim: [" + clientLevelWrapper.getDhIdentifier() + "] ServerLevelWrapper dim: [" + serverLevelWrapper.getDhIdentifier() + "].");
				}
				
				
				DhClientServerLevel level = this.dhLevelByLevelWrapper.get(serverLevelWrapper);
				if (level == null)
				{
					return null;
				}
				
				level.startRenderer(clientLevelWrapper);
				clientLevelWrapper.setParentLevel(level);
				return level;
			});
		}
	}
	
	@Override
	public void unloadLevel(@NotNull ILevelWrapper wrapper)
	{
		if (this.dhLevelByLevelWrapper.containsKey(wrapper))
		{
			if (wrapper instanceof IServerLevelWrapper)
			{
				LOGGER.info("Unloading level " + this.dhLevelByLevelWrapper.get(wrapper));
				wrapper.onUnload();
				
				DhClientServerLevel clientServerLevel = this.dhLevelByLevelWrapper.remove(wrapper);
				clientServerLevel.close();
				this.dhLevels.remove(clientServerLevel);
			}
			else
			{
				// If the level wrapper is a Client Level Wrapper, then that means the client side leaves the level,
				// but note that the server side still has the level loaded. So, we don't want to unload the level,
				// we just want to stop rendering it.
				this.dhLevelByLevelWrapper.remove(wrapper).stopRenderer(); // Ignore resource warning. The level obj is referenced elsewhere.
			}
		}
	}
	
	private void _clientTick()
	{
		//LOGGER.info("Client world tick with {} levels", levels.size());
		this.dhLevels.forEach(DhClientServerLevel::clientTick);
	}
	
	@Override 
	public void clientTick()
	{
		//LOGGER.info("Client world tick");
		this.eventLoop.tick();
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	/** synchronized to prevent a rare issue where the server tries closing the same world multiple times in rapid succession. */
	@Override
	public synchronized void close()
	{
		synchronized (this.dhLevels)
		{
			// close each level
			for (DhClientServerLevel level : this.dhLevels)
			{
				LOGGER.info("Unloading level [" + level.getServerLevelWrapper().getDhIdentifier() + "].");
				
				// level wrapper shouldn't be null, but just in case
				IServerLevelWrapper serverLevelWrapper = level.getServerLevelWrapper();
				if (serverLevelWrapper != null)
				{
					serverLevelWrapper.onUnload();
				}
				
				level.close();
			}
		}
		
		this.dhLevelByLevelWrapper.clear();
		this.eventLoop.close();
		LOGGER.info("Closed DhWorld of type " + this.environment);
	}
	
}
