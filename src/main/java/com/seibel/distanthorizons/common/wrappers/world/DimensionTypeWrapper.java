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

package com.seibel.distanthorizons.common.wrappers.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;

import net.minecraft.world.level.dimension.DimensionType;

/**
 * @author James Seibel
 */
public class DimensionTypeWrapper implements IDimensionTypeWrapper
{
	private static final ConcurrentMap<String, DimensionTypeWrapper> DIMENSION_WRAPPER_BY_NAME = new ConcurrentHashMap<>();
	private final DimensionType dimensionType;
	
	
	
	//=============//
	// Constructor //
	//=============//
	
	public DimensionTypeWrapper(DimensionType dimensionType) { this.dimensionType = dimensionType; }
	
	public static DimensionTypeWrapper getDimensionTypeWrapper(DimensionType dimensionType)
	{
		String dimName = getName(dimensionType);
		
		// check if the dimension has already been wrapped
		if (DIMENSION_WRAPPER_BY_NAME.containsKey(dimName) 
			&& DIMENSION_WRAPPER_BY_NAME.get(dimName) != null)
		{
			return DIMENSION_WRAPPER_BY_NAME.get(dimName);
		}
		
		
		// create the missing wrapper
		DimensionTypeWrapper dimensionTypeWrapper = new DimensionTypeWrapper(dimensionType);
		DIMENSION_WRAPPER_BY_NAME.put(dimName, dimensionTypeWrapper);
		return dimensionTypeWrapper;
	}
	
	public static void clearMap() { DIMENSION_WRAPPER_BY_NAME.clear(); }
	
	
	
	//=================//
	// wrapper methods //
	//=================//
	
	@Override
	public String getName() { return getName(this.dimensionType); }
	public static String getName(DimensionType dimensionType)
	{
		#if MC_VER <= MC_1_16_5
		// effectsLocation() is marked as client only, so using the backing field directly
		return dimensionType.effectsLocation.getPath();
		#else
		return dimensionType.effectsLocation().getPath();
		#endif
	}
	
	@Override
	public boolean hasCeiling() { return this.dimensionType.hasCeiling(); }
	
	@Override
	public boolean hasSkyLight() { return this.dimensionType.hasSkyLight(); }
	
	@Override
	public Object getWrappedMcObject() { return this.dimensionType; }
	
	// there's definitely a better way of doing this, but it should work well enough for now
	@Override
	public boolean isTheEnd() { return this.getName().equalsIgnoreCase("the_end"); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public boolean equals(Object obj)
	{
		if (obj.getClass() != DimensionTypeWrapper.class)
		{
			return false;
		}
		else
		{
			DimensionTypeWrapper other = (DimensionTypeWrapper) obj;
			return other.getName().equals(this.getName());
		}
	}
	
	
	
}
