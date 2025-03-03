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

import com.seibel.distanthorizons.api.interfaces.world.IDhApiDimensionTypeWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/**
 * Used to interact with the currently loaded world.
 * This is separate from the world itself to prevent issues
 * with API implementors referencing said world when it needs
 * to be loaded/unloaded.
 *
 * @author James Seibel
 * @version 2024-9-27
 */
public class DhApiWorldProxy implements IDhApiWorldProxy
{
	public static DhApiWorldProxy INSTANCE = new DhApiWorldProxy();
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftSharedWrapper MC_SHARED = SingletonInjector.INSTANCE.get(IMinecraftSharedWrapper.class);
	private static final String NO_WORLD_EXCEPTION_STRING = "No world loaded";
	
	private boolean isReadOnly = false;
	
	
	
	//=============//
	// constructor // 
	//=============//
	
	private DhApiWorldProxy() { }
	
	
	
	//===================//
	// getters / setters //
	//===================//
	
	@Override
	public boolean worldLoaded() { return SharedApi.getAbstractDhWorld() != null; }
	
	@Override
	public void setReadOnly(boolean readOnly) throws IllegalStateException { this.setReadOnly(readOnly, true); }
	/** 
	 * Not part of the public API. 
	 * Normal API users shouldn't be able to change the upcoming world state
	 * this is only here so DH can revert the readonly value after the world is unloaded
	 */
	public void setReadOnly(boolean readOnly, boolean throwIfWorldUnloaded) throws IllegalStateException
	{
		if (throwIfWorldUnloaded && SharedApi.getAbstractDhWorld() == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		boolean valueChanged = (this.isReadOnly != readOnly);
		this.isReadOnly = readOnly;
		
		if (valueChanged)
		{
			if (this.isReadOnly)
			{
				LOGGER.info("DH world set to read-only. LODs will not update while this API flag is active.");
			}
			else
			{
				LOGGER.info("DH world is no longer in read-only mode. LODs will update like normal.");
			}
		}
	}
	
	@Override
	public boolean getReadOnly() throws IllegalStateException
	{
		if (SharedApi.getAbstractDhWorld() == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		
		return this.isReadOnly; 
	}
	
	
	
	//================//
	// level handlers //
	//================//
	
	@Override
	public IDhApiLevelWrapper getSinglePlayerLevel() throws IllegalStateException
	{
		if (SharedApi.getAbstractDhWorld() == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		if (MC_SHARED.isDedicatedServer())
		{
			return null;
		}
		
		
		return MC_CLIENT.getWrappedClientLevel();
	}
	
	
	@Override
	public Iterable<IDhApiLevelWrapper> getAllLoadedLevelWrappers() throws IllegalStateException
	{
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		ArrayList<IDhApiLevelWrapper> returnList = new ArrayList<>();
		for (IDhLevel dhLevel : world.getAllLoadedLevels())
		{
			returnList.add(dhLevel.getLevelWrapper());
		}
		return returnList;
	}
	
	@Override
	public Iterable<IDhApiLevelWrapper> getAllLoadedLevelsForDimensionType(IDhApiDimensionTypeWrapper dimensionTypeWrapper) throws IllegalStateException
	{
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		ArrayList<IDhApiLevelWrapper> returnList = new ArrayList<>();
		for (IDhLevel dhLevel : world.getAllLoadedLevels())
		{
			ILevelWrapper levelWrapper = dhLevel.getLevelWrapper();
			if (levelWrapper.getDimensionType().equals(dimensionTypeWrapper))
			{
				returnList.add(levelWrapper);
			}
		}
		return returnList;
	}
	
	@Override
	public Iterable<IDhApiLevelWrapper> getAllLoadedLevelsWithDimensionNameLike(String dimensionName) throws IllegalStateException
	{
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null)
		{
			throw new IllegalStateException(NO_WORLD_EXCEPTION_STRING);
		}
		
		String soughtDimName = dimensionName.toLowerCase();
		
		ArrayList<IDhApiLevelWrapper> returnList = new ArrayList<>();
		for (IDhLevel dhLevel : world.getAllLoadedLevels())
		{
			ILevelWrapper levelWrapper = dhLevel.getLevelWrapper();
			String levelDimName = levelWrapper.getDhIdentifier().toLowerCase();
			if (levelDimName.contains(soughtDimName))
			{
				returnList.add(levelWrapper);
			}
		}
		
		return returnList;
	}
	
	
}
