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

package com.seibel.distanthorizons.core.config;

import com.seibel.distanthorizons.core.config.types.ConfigEntry;

// TODO: Make this integrate with the config system
public class AppliedConfigState<T>
{
	final ConfigEntry<T> entry;
	T activeValue;
	
	
	
	public AppliedConfigState(ConfigEntry<T> entryToWatch)
	{
		this.entry = entryToWatch;
		this.activeValue = entryToWatch.get();
	}
	
	
	
	/** Returns true if the value was changed */
	public boolean pollNewValue()
	{
		T newValue = this.entry.get();
		if (newValue.equals(this.activeValue))
		{
			return false;
		}
		this.activeValue = newValue;
		return true;
	}
	
	public T get() { return this.activeValue; }
	
}
