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

package com.seibel.distanthorizons.coreapi.DependencyInjection;

import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IOverrideInjector;
import com.seibel.distanthorizons.coreapi.util.StringUtil;

import java.util.HashMap;

/**
 * This class takes care of dependency injection for overridable objects. <Br>
 * This is done so other mods can override our methods to improve features down the line.
 *
 * @author James Seibel
 * @version 2024-1-30
 */
public class OverrideInjector implements IOverrideInjector<IDhApiOverrideable>
{
	public static final OverrideInjector INSTANCE = new OverrideInjector();
	
	private final HashMap<Class<? extends IDhApiOverrideable>, OverridePriorityListContainer> overrideContainerByInterface = new HashMap<>();
	
	/**
	 * This is used to determine if an override is part of Distant Horizons'
	 * Core or not.
	 * This probably isn't the best way of going about this, but it works for now.
	 */
	private final String corePackagePath;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public OverrideInjector()
	{
		String thisPackageName = this.getClass().getPackage().getName();
		int secondPackageEndingIndex = StringUtil.nthIndexOf(thisPackageName, ".", 3);
		
		this.corePackagePath = thisPackageName.substring(0, secondPackageEndingIndex); // this should be "com.seibel.distanthorizons"
	}
	
	public OverrideInjector(String newCorePackagePath) { this.corePackagePath = newCorePackagePath; }
	
	
	
	//=========//
	// binding //
 	//=========//
	
	@Override
	public void bind(Class<? extends IDhApiOverrideable> dependencyInterface, IDhApiOverrideable dependencyImplementation) throws IllegalStateException, IllegalArgumentException
	{
		// make sure a override container exists
		OverridePriorityListContainer overrideContainer = this.overrideContainerByInterface.get(dependencyInterface);
		if (overrideContainer == null)
		{
			overrideContainer = new OverridePriorityListContainer();
			this.overrideContainerByInterface.put(dependencyInterface, overrideContainer);
		}
		
		
		// validate the new override //
		
		// check if this override is a core override
		if (dependencyImplementation.getPriority() == CORE_PRIORITY)
		{
			// this claims to be a core override, is that true?
			String packageName = dependencyImplementation.getClass().getPackage().getName();
			if (!packageName.startsWith(this.corePackagePath))
			{
				throw new IllegalArgumentException("Only Distant Horizons internal objects can use the Override Priority [" + CORE_PRIORITY + "]. Please use a higher number.");
			}
		}
		
		// make sure the override has a valid priority
		else if (dependencyImplementation.getPriority() < MIN_NON_CORE_OVERRIDE_PRIORITY)
		{
			throw new IllegalArgumentException("Invalid priority value [" + dependencyImplementation.getPriority() + "], override priorities must be [" + MIN_NON_CORE_OVERRIDE_PRIORITY + "] or greater.");
		}
		
		// check if an override already exists with this priority
		IDhApiOverrideable existingOverride = overrideContainer.getOverrideWithPriority(dependencyImplementation.getPriority());
		if (existingOverride != null)
		{
			throw new IllegalStateException("An override already exists with the priority [" + dependencyImplementation.getPriority() + "].");
		}
		
		
		// bind the override
		overrideContainer.addOverride(dependencyImplementation);
	}
	
	@Override
	public void unbind(Class<? extends IDhApiOverrideable> dependencyInterface, IDhApiOverrideable dependencyImplementation)
	{
		OverridePriorityListContainer overrideContainer = this.overrideContainerByInterface.get(dependencyInterface);
		if (overrideContainer != null)
		{
			overrideContainer.removeOverride(dependencyImplementation);
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	@SuppressWarnings("unchecked")
	public <T extends IDhApiOverrideable> T get(Class<T> interfaceClass) throws ClassCastException
	{
		OverridePriorityListContainer overrideContainer = this.overrideContainerByInterface.get(interfaceClass);
		return overrideContainer != null ? (T) overrideContainer.getOverrideWithHighestPriority() : null;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T extends IDhApiOverrideable> T get(Class<T> interfaceClass, int priority) throws ClassCastException
	{
		OverridePriorityListContainer overrideContainer = this.overrideContainerByInterface.get(interfaceClass);
		return overrideContainer != null ? (T) overrideContainer.getOverrideWithPriority(priority) : null;
	}
	
	
	
	//==========//
	// clearing //
	//==========//
	
	@Override
	public void clear() { this.overrideContainerByInterface.clear(); }
	
	
	
}
